//! Manager owns user management and data transfer; admin does not
//! (patch 0031).
//!
//! The owner's rule: manager has everything admin has, plus user management
//! and data transfer. This file checks, against a real PostgreSQL database
//! that has run the patch catalog:
//!
//! - the role grants: manager ⊇ admin, admin holds none of `users:*`,
//!   `roles:*`, `permissions:*`, `data_transfer:*`, team administration or
//!   their menu entries, and manager holds all of them;
//! - effective permissions: an admin account is refused user management and
//!   data transfer, a manager account gets both, and an account flagged
//!   `users.is_super_admin` and given the `super_admin` role by the patch
//!   keeps everything (the System Administrator account);
//! - the escalation guard: a manager may assign `admin` and below, but not
//!   `manager` or `super_admin`, and may not manage a super-admin account.
//!
//! Fixtures use the fixed `931_4xx` block and are removed before and after.
//! Skips without `DATABASE_URL`.

use hotel_app_be::AuthService;
use hotel_app_be::core::error::ApiError;
use hotel_app_be::core::rbac_cache;
use hotel_app_be::models::AssignRoleInput;
use hotel_app_be::modules::rbac::service as rbac_service;
use hotel_app_be::modules::users::service as users_service;
use sqlx::{PgPool, postgres::PgPoolOptions};

const TEST_JWT_SECRET: &str = "hotel-app-be-manager-user-mgmt-test-secret-32chars";
const ADMIN: i64 = 931_401;
const MANAGER: i64 = 931_402;
const FLAGGED_ADMIN: i64 = 931_403;
const TARGET: i64 = 931_404;
const ALL: [i64; 4] = [ADMIN, MANAGER, FLAGGED_ADMIN, TARGET];

/// Every permission the patch moves from admin to manager.
const MOVED_SQL: &str = "\
    SELECT p.name FROM permissions p \
    WHERE p.resource IN ('users', 'roles', 'permissions', 'data_transfer') \
       OR p.name IN ('teams:create', 'teams:update', 'teams:delete', 'teams:assign', \
                     'teams:manage', 'navigation_rbac:read', 'navigation_data_transfer:read') \
    ORDER BY 1";

static LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

fn ensure_test_app_config() {
    static INIT: std::sync::Once = std::sync::Once::new();
    INIT.call_once(|| {
        let _ = AuthService::init_jwt_secret(TEST_JWT_SECRET);
        if std::env::var("JWT_SECRET").is_err() {
            // SAFETY: runs exactly once, inside `Once::call_once`, before any
            // test in this binary reads config env vars.
            unsafe { std::env::set_var("JWT_SECRET", TEST_JWT_SECRET) };
        }
        let _ = hotel_app_be::core::config::init_from_env();
    });
}

async fn setup_pg_pool() -> Option<PgPool> {
    let Ok(database_url) = std::env::var("DATABASE_URL") else {
        eprintln!("Skipping manager user-management tests because DATABASE_URL is not set");
        return None;
    };
    ensure_test_app_config();
    let pool = PgPoolOptions::new()
        .max_connections(5)
        .after_connect(|conn, _| {
            Box::pin(async move {
                sqlx::query("SET app.allow_audit_mutation = 'on'")
                    .execute(conn)
                    .await
                    .map(|_| ())
            })
        })
        .connect(&database_url)
        .await
        .expect("failed to connect to PostgreSQL test database");
    Some(pool)
}

async fn role_id(pool: &PgPool, name: &str) -> i64 {
    sqlx::query_scalar("SELECT id FROM roles WHERE name = $1")
        .bind(name)
        .fetch_one(pool)
        .await
        .unwrap_or_else(|e| panic!("role '{name}' must exist: {e}"))
}

async fn role_permission_names(pool: &PgPool, role: &str) -> Vec<String> {
    sqlx::query_scalar(
        "SELECT p.name FROM permissions p \
         JOIN role_permissions rp ON rp.permission_id = p.id \
         JOIN roles r ON r.id = rp.role_id \
         WHERE r.name = $1 ORDER BY 1",
    )
    .bind(role)
    .fetch_all(pool)
    .await
    .unwrap()
}

async fn cleanup(pool: &PgPool) {
    for sql in [
        "DELETE FROM refresh_tokens WHERE user_id = ANY($1)",
        "DELETE FROM audit_logs WHERE user_id = ANY($1) \
            OR (resource_type IN ('user', 'user_role') AND resource_id = ANY($1))",
        "DELETE FROM user_roles WHERE user_id = ANY($1)",
        "DELETE FROM users WHERE id = ANY($1)",
    ] {
        sqlx::query(sql).bind(&ALL[..]).execute(pool).await.unwrap();
    }
    rbac_cache::clear_all();
}

async fn insert_staff(pool: &PgPool, id: i64, is_super_admin: bool, roles: &[&str]) {
    sqlx::query(
        "INSERT INTO users (id, username, email, password_hash, full_name, user_type, \
                            is_active, is_verified, is_super_admin) \
         OVERRIDING SYSTEM VALUE \
         VALUES ($1, $2, $3, 'not-a-real-hash', $4, 'staff', true, true, $5)",
    )
    .bind(id)
    .bind(format!("mgr_um_{id}"))
    .bind(format!("mgr_um_{id}@example.com"))
    .bind(format!("Manager UM {id}"))
    .bind(is_super_admin)
    .execute(pool)
    .await
    .unwrap();
    for role in roles {
        let role_id = role_id(pool, role).await;
        sqlx::query("INSERT INTO user_roles (user_id, role_id) VALUES ($1, $2)")
            .bind(id)
            .bind(role_id)
            .execute(pool)
            .await
            .unwrap();
    }
}

async fn holds(pool: &PgPool, user_id: i64, permission: &str) -> bool {
    AuthService::check_permission(pool, user_id, permission)
        .await
        .unwrap()
}

#[tokio::test]
async fn manager_is_a_superset_of_admin_and_owns_user_management() {
    let Some(pool) = setup_pg_pool().await else {
        return;
    };

    let moved: Vec<String> = sqlx::query_scalar(MOVED_SQL)
        .fetch_all(&pool)
        .await
        .unwrap();
    for expected in [
        "users:create",
        "users:manage",
        "roles:manage",
        "permissions:manage",
        "teams:assign",
        "data_transfer:export",
        "data_transfer:import",
        "data_transfer:restore",
    ] {
        assert!(
            moved.iter().any(|name| name == expected),
            "{expected} must be in the moved set"
        );
    }

    let admin = role_permission_names(&pool, "admin").await;
    let manager = role_permission_names(&pool, "manager").await;

    let missing: Vec<&String> = admin.iter().filter(|p| !manager.contains(p)).collect();
    assert!(
        missing.is_empty(),
        "manager must hold every admin permission; missing {missing:?}"
    );
    let leaked: Vec<&String> = moved.iter().filter(|p| admin.contains(p)).collect();
    assert!(
        leaked.is_empty(),
        "admin must not hold user-management or data-transfer permissions: {leaked:?}"
    );
    let absent: Vec<&String> = moved.iter().filter(|p| !manager.contains(p)).collect();
    assert!(
        absent.is_empty(),
        "manager must hold every moved permission; missing {absent:?}"
    );
    assert!(
        admin.iter().any(|p| p == "online_inventory:manage"),
        "admin keeps online inventory"
    );
    assert!(
        admin.iter().any(|p| p == "teams:read"),
        "admin keeps read-only teams"
    );

    let priority = |name: &'static str| {
        let pool = pool.clone();
        async move {
            sqlx::query_scalar::<_, i32>("SELECT priority FROM roles WHERE name = $1")
                .bind(name)
                .fetch_one(&pool)
                .await
                .unwrap()
        }
    };
    let (admin_p, manager_p, super_p) = (
        priority("admin").await,
        priority("manager").await,
        priority("super_admin").await,
    );
    assert!(
        admin_p < manager_p && manager_p < super_p,
        "admin ({admin_p}) < manager ({manager_p}) < super_admin ({super_p})"
    );
}

#[tokio::test]
async fn effective_permissions_follow_the_new_split() {
    let Some(pool) = setup_pg_pool().await else {
        return;
    };
    let _guard = LOCK.lock().await;
    cleanup(&pool).await;
    insert_staff(&pool, ADMIN, false, &["admin"]).await;
    insert_staff(&pool, MANAGER, false, &["manager"]).await;
    // What patch 0031 leaves the System Administrator account with.
    insert_staff(&pool, FLAGGED_ADMIN, true, &["admin", "super_admin"]).await;
    rbac_cache::clear_all();

    let mut failures = Vec::new();
    for permission in [
        "users:read",
        "users:create",
        "users:delete",
        "roles:manage",
        "permissions:read",
        "teams:assign",
        "data_transfer:view",
        "data_transfer:export",
    ] {
        if holds(&pool, ADMIN, permission).await {
            failures.push(format!("admin must not hold {permission}"));
        }
        if !holds(&pool, MANAGER, permission).await {
            failures.push(format!("manager must hold {permission}"));
        }
        if !holds(&pool, FLAGGED_ADMIN, permission).await {
            failures.push(format!("super-admin-flagged admin must hold {permission}"));
        }
    }
    for permission in [
        "online_inventory:manage",
        "bookings:manage",
        "settings:manage",
    ] {
        if !holds(&pool, ADMIN, permission).await {
            failures.push(format!("admin must keep {permission}"));
        }
        if !holds(&pool, MANAGER, permission).await {
            failures.push(format!("manager must hold {permission}"));
        }
    }

    cleanup(&pool).await;
    assert!(failures.is_empty(), "{failures:#?}");
}

#[tokio::test]
async fn flagged_super_admin_accounts_hold_the_super_admin_role() {
    let Some(pool) = setup_pg_pool().await else {
        return;
    };
    // The seeded System Administrator (`admin`, flagged, assigned `admin`)
    // must have been given `super_admin` by the patch, or it would have lost
    // user management. Only checked where the seed accounts exist.
    let seeded: Option<(i64, bool)> =
        sqlx::query_as("SELECT id, is_super_admin FROM users WHERE username = 'admin'")
            .fetch_optional(&pool)
            .await
            .unwrap();
    let Some((admin_id, true)) = seeded else {
        return;
    };
    rbac_cache::clear_all();
    let roles = AuthService::get_user_roles(&pool, admin_id).await.unwrap();
    assert!(
        roles.iter().any(|r| r == "super_admin"),
        "the flagged seed admin must hold super_admin: {roles:?}"
    );
    assert!(holds(&pool, admin_id, "users:manage").await);
    assert!(holds(&pool, admin_id, "data_transfer:manage").await);
}

#[tokio::test]
async fn manager_can_assign_admin_but_not_manager_or_super_admin() {
    let Some(pool) = setup_pg_pool().await else {
        return;
    };
    let _guard = LOCK.lock().await;
    cleanup(&pool).await;
    insert_staff(&pool, MANAGER, false, &["manager"]).await;
    insert_staff(&pool, TARGET, false, &["receptionist"]).await;
    // Flag only, no super_admin role: the target guard must key on the flag.
    insert_staff(&pool, FLAGGED_ADMIN, true, &["admin"]).await;
    rbac_cache::clear_all();

    let admin_role = role_id(&pool, "admin").await;
    let manager_role = role_id(&pool, "manager").await;
    let super_role = role_id(&pool, "super_admin").await;
    let assign = |role_id: i64| {
        let pool = pool.clone();
        async move {
            rbac_service::assign_role_to_user(
                &pool,
                MANAGER,
                AssignRoleInput {
                    user_id: TARGET,
                    role_id,
                },
            )
            .await
        }
    };

    let as_admin = assign(admin_role).await;
    let as_manager = assign(manager_role).await;
    let as_super = assign(super_role).await;
    let self_promote = rbac_service::assign_role_to_user(
        &pool,
        MANAGER,
        AssignRoleInput {
            user_id: MANAGER,
            role_id: super_role,
        },
    )
    .await;
    // The System Administrator account holds only `admin` but is flagged
    // super admin; a manager outranks `admin` yet must not touch it.
    let suspend_flagged = users_service::suspend_user(&pool, MANAGER, FLAGGED_ADMIN).await;
    let suspend_admin_target = users_service::suspend_user(&pool, MANAGER, TARGET).await;

    cleanup(&pool).await;

    assert!(as_admin.is_ok(), "manager may assign admin: {as_admin:?}");
    assert!(
        matches!(as_manager, Err(ApiError::Forbidden(_))),
        "manager may not assign manager: {as_manager:?}"
    );
    assert!(
        matches!(as_super, Err(ApiError::Forbidden(_))),
        "manager may not assign super_admin: {as_super:?}"
    );
    assert!(
        matches!(self_promote, Err(ApiError::Forbidden(_))),
        "manager may not promote themselves: {self_promote:?}"
    );
    assert!(
        matches!(suspend_flagged, Err(ApiError::Forbidden(_))),
        "manager may not manage a super-admin account: {suspend_flagged:?}"
    );
    assert!(
        suspend_admin_target.is_ok(),
        "manager may manage an admin account: {suspend_admin_target:?}"
    );
}
