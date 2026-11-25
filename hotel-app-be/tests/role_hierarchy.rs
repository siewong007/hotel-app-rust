//! Role hierarchy characterization and enforcement suite (`935_5xx` fixtures).
//!
//! Verifies against a patched PostgreSQL database:
//! - the 12-role priority ladder (`SYSTEM_ROLE_HIERARCHY`) is exposed via
//!   `priority` and `is_system_role` on `Role` responses (`roles`, `snapshot`,
//!   `user_roles_permissions`) and returned in descending priority order;
//! - custom role creation and updates bound `priority` strictly below a
//!   non-super-admin actor's priority and reserve `1000` for `super_admin`;
//! - user-role and team-membership mutations refuse peer, superior,
//!   team-elevated, and active/suspended super-admin targets while allowing
//!   legitimate delegation to lower-ranked accounts.

use hotel_app_be::AuthService;
use hotel_app_be::core::error::ApiError;
use hotel_app_be::core::rbac_cache;
use hotel_app_be::models::{AssignRoleInput, RoleInput, SYSTEM_ROLE_HIERARCHY, UserRoleIdsInput};
use hotel_app_be::modules::rbac::service as rbac_service;
use hotel_app_be::modules::teams::models::{TeamCreateInput, TeamMemberInput, TeamRoleIdsInput};
use hotel_app_be::modules::teams::service as teams_service;
use hotel_app_be::modules::users::service as users_service;
use sqlx::{PgPool, postgres::PgPoolOptions};

const TEST_JWT_SECRET: &str = "hotel-app-be-role-hierarchy-test-secret-32chars!!";

const SUPER_ADMIN: i64 = 935_501;
const SUSPENDED_SUPER_ADMIN: i64 = 935_502;
const MANAGER_A: i64 = 935_503;
const MANAGER_B: i64 = 935_504;
const TEAM_MANAGER: i64 = 935_505;
const ADMIN_USER: i64 = 935_506;
const RECEPTION_LEAD: i64 = 935_507;
const RECEPTION_PEER: i64 = 935_508;
const GUEST_STAFF: i64 = 935_509;

const ALL_USERS: [i64; 9] = [
    SUPER_ADMIN,
    SUSPENDED_SUPER_ADMIN,
    MANAGER_A,
    MANAGER_B,
    TEAM_MANAGER,
    ADMIN_USER,
    RECEPTION_LEAD,
    RECEPTION_PEER,
    GUEST_STAFF,
];

const CUSTOM_ROLES: [&str; 4] = [
    "rh935_default_role",
    "rh935_ranked_role",
    "rh935_ranked_renamed",
    "rh935_super_role",
];

const TEAM_CODES: [&str; 2] = ["rh935_mgr_team", "rh935_desk_team"];

static LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

fn ensure_test_app_config() {
    static INIT: std::sync::Once = std::sync::Once::new();
    INIT.call_once(|| {
        let _ = AuthService::init_jwt_secret(TEST_JWT_SECRET);
        if std::env::var("JWT_SECRET").is_err() {
            // SAFETY: runs once inside `Once::call_once` before concurrent reads.
            unsafe { std::env::set_var("JWT_SECRET", TEST_JWT_SECRET) };
        }
        let _ = hotel_app_be::core::config::init_from_env();
    });
}

async fn setup_pg_pool() -> Option<PgPool> {
    let Ok(database_url) = std::env::var("DATABASE_URL") else {
        eprintln!("Skipping role hierarchy tests because DATABASE_URL is not set");
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

async fn cleanup(pool: &PgPool) {
    let team_codes: Vec<&str> = TEAM_CODES.to_vec();
    let role_names: Vec<&str> = CUSTOM_ROLES.to_vec();

    sqlx::query("DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE code = ANY($1)) OR user_id = ANY($2)")
        .bind(&team_codes)
        .bind(&ALL_USERS[..])
        .execute(pool)
        .await
        .unwrap();
    sqlx::query(
        "DELETE FROM team_roles WHERE team_id IN (SELECT id FROM teams WHERE code = ANY($1))",
    )
    .bind(&team_codes)
    .execute(pool)
    .await
    .unwrap();
    sqlx::query("DELETE FROM audit_logs WHERE user_id = ANY($1) OR (resource_type IN ('user', 'user_role', 'role', 'team') AND resource_id = ANY($1))")
        .bind(&ALL_USERS[..])
        .execute(pool)
        .await
        .unwrap();
    sqlx::query("DELETE FROM teams WHERE code = ANY($1)")
        .bind(&team_codes)
        .execute(pool)
        .await
        .unwrap();
    sqlx::query(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name = ANY($1))",
    )
    .bind(&role_names)
    .execute(pool)
    .await
    .unwrap();
    sqlx::query("DELETE FROM user_roles WHERE user_id = ANY($1) OR role_id IN (SELECT id FROM roles WHERE name = ANY($2))")
        .bind(&ALL_USERS[..])
        .bind(&role_names)
        .execute(pool)
        .await
        .unwrap();
    sqlx::query("DELETE FROM roles WHERE name = ANY($1)")
        .bind(&role_names)
        .execute(pool)
        .await
        .unwrap();
    sqlx::query("DELETE FROM refresh_tokens WHERE user_id = ANY($1)")
        .bind(&ALL_USERS[..])
        .execute(pool)
        .await
        .unwrap();
    sqlx::query("DELETE FROM users WHERE id = ANY($1)")
        .bind(&ALL_USERS[..])
        .execute(pool)
        .await
        .unwrap();
    rbac_cache::clear_all();
}

async fn insert_staff(
    pool: &PgPool,
    id: i64,
    is_active: bool,
    is_super_admin: bool,
    roles: &[&str],
) {
    sqlx::query(
        "INSERT INTO users (id, username, email, password_hash, full_name, user_type, \
                            is_active, is_verified, is_super_admin) \
         OVERRIDING SYSTEM VALUE \
         VALUES ($1, $2, $3, 'not-a-real-hash', $4, 'staff', $5, true, $6)",
    )
    .bind(id)
    .bind(format!("rh935_u_{id}"))
    .bind(format!("rh935_u_{id}@example.com"))
    .bind(format!("Role Hierarchy {id}"))
    .bind(is_active)
    .bind(is_super_admin)
    .execute(pool)
    .await
    .unwrap();

    for role in roles {
        let rid = role_id(pool, role).await;
        sqlx::query("INSERT INTO user_roles (user_id, role_id) VALUES ($1, $2)")
            .bind(id)
            .bind(rid)
            .execute(pool)
            .await
            .unwrap();
    }
}

#[tokio::test]
async fn role_responses_expose_priority_and_sort_by_the_12_role_hierarchy() {
    let Some(pool) = setup_pg_pool().await else {
        return;
    };
    let _guard = LOCK.lock().await;
    cleanup(&pool).await;

    let listed = rbac_service::roles(&pool).await.unwrap();
    for pair in listed.windows(2) {
        assert!(
            pair[0].priority > pair[1].priority
                || (pair[0].priority == pair[1].priority && pair[0].name <= pair[1].name),
            "roles must be ordered by priority DESC, name ASC: {:?} came before {:?}",
            pair[0],
            pair[1]
        );
    }

    let system_roles: Vec<(&str, i64)> = listed
        .iter()
        .filter(|r| r.is_system_role)
        .map(|r| (r.name.as_str(), r.priority))
        .collect();
    assert_eq!(
        system_roles.as_slice(),
        SYSTEM_ROLE_HIERARCHY,
        "seeded system roles must match the 12-role priority ladder"
    );

    let snap = rbac_service::snapshot(&pool).await.unwrap();
    let snap_names: Vec<(&str, i64, bool)> = snap
        .roles
        .iter()
        .filter(|r| r.is_system_role)
        .map(|r| (r.name.as_str(), r.priority, r.is_system_role))
        .collect();
    assert_eq!(snap_names.len(), SYSTEM_ROLE_HIERARCHY.len());
    assert_eq!(snap_names[0], ("super_admin", 1000, true));
    assert_eq!(snap_names[1], ("manager", 150, true));
    assert_eq!(snap_names[2], ("admin", 100, true));
    assert_eq!(snap_names[11], ("guest", 20, true));

    cleanup(&pool).await;
}

#[tokio::test]
async fn custom_role_priorities_are_bounded_below_actor_and_super_admin_ceiling() {
    let Some(pool) = setup_pg_pool().await else {
        return;
    };
    let _guard = LOCK.lock().await;
    cleanup(&pool).await;

    insert_staff(&pool, SUPER_ADMIN, true, true, &["super_admin"]).await;
    insert_staff(&pool, MANAGER_A, true, false, &["manager"]).await;
    rbac_cache::clear_all();

    // Default custom priority is 0 and is_system_role is false.
    let default_role = rbac_service::create_role(
        &pool,
        MANAGER_A,
        RoleInput {
            name: "rh935_default_role".to_string(),
            description: Some("Default priority custom role".to_string()),
            priority: None,
        },
    )
    .await
    .expect("manager can create a default-priority custom role");
    assert_eq!(default_role.priority, 0);
    assert!(!default_role.is_system_role);

    // Manager (150) can create at 120 (< 150) and update to 149 (or None to keep 149).
    let ranked = rbac_service::create_role(
        &pool,
        MANAGER_A,
        RoleInput {
            name: "rh935_ranked_role".to_string(),
            description: None,
            priority: Some(120),
        },
    )
    .await
    .expect("manager can create a role strictly below priority 150");
    assert_eq!(ranked.priority, 120);

    let promoted_ok = rbac_service::update_role(
        &pool,
        MANAGER_A,
        ranked.id,
        RoleInput {
            name: "rh935_ranked_renamed".to_string(),
            description: Some("Updated".to_string()),
            priority: Some(149),
        },
    )
    .await
    .expect("manager can raise a custom role up to 149");
    assert_eq!(promoted_ok.priority, 149);

    let preserved = rbac_service::update_role(
        &pool,
        MANAGER_A,
        ranked.id,
        RoleInput {
            name: "rh935_ranked_renamed".to_string(),
            description: Some("Preserved priority".to_string()),
            priority: None,
        },
    )
    .await
    .expect("omitting priority on update keeps stored priority");
    assert_eq!(preserved.priority, 149);

    // Manager (150) cannot create or promote a role to 150 (peer) or 200 (superior).
    for disallowed in [150_i64, 200, 1000] {
        let create_err = rbac_service::create_role(
            &pool,
            MANAGER_A,
            RoleInput {
                name: "rh935_super_role".to_string(),
                description: None,
                priority: Some(disallowed),
            },
        )
        .await;
        assert!(
            matches!(create_err, Err(ApiError::Forbidden(_))),
            "manager must not create a role at priority {disallowed}: {create_err:?}"
        );

        let update_err = rbac_service::update_role(
            &pool,
            MANAGER_A,
            ranked.id,
            RoleInput {
                name: "rh935_ranked_renamed".to_string(),
                description: None,
                priority: Some(disallowed),
            },
        )
        .await;
        assert!(
            matches!(update_err, Err(ApiError::Forbidden(_))),
            "manager must not promote a role to priority {disallowed}: {update_err:?}"
        );
    }

    // Super admin can create up to 999, but 1000 and negative values are rejected.
    let at_999 = rbac_service::create_role(
        &pool,
        SUPER_ADMIN,
        RoleInput {
            name: "rh935_super_role".to_string(),
            description: None,
            priority: Some(999),
        },
    )
    .await
    .expect("super_admin can create a custom role at 999");
    assert_eq!(at_999.priority, 999);

    for invalid in [-1_i64, 1000] {
        let err = rbac_service::update_role(
            &pool,
            SUPER_ADMIN,
            at_999.id,
            RoleInput {
                name: "rh935_super_role".to_string(),
                description: None,
                priority: Some(invalid),
            },
        )
        .await;
        assert!(
            matches!(err, Err(ApiError::BadRequest(_))),
            "priority {invalid} must be BadRequest even for super_admin: {err:?}"
        );
    }

    cleanup(&pool).await;
}

#[tokio::test]
async fn user_role_and_team_endpoints_enforce_hierarchy_against_peers_superiors_and_super_admins() {
    let Some(pool) = setup_pg_pool().await else {
        return;
    };
    let _guard = LOCK.lock().await;
    cleanup(&pool).await;

    insert_staff(&pool, SUPER_ADMIN, true, true, &["super_admin"]).await;
    insert_staff(&pool, SUSPENDED_SUPER_ADMIN, false, true, &["admin"]).await;
    insert_staff(&pool, MANAGER_A, true, false, &["manager"]).await;
    insert_staff(&pool, MANAGER_B, true, false, &["manager", "receptionist"]).await;
    insert_staff(&pool, TEAM_MANAGER, true, false, &["staff"]).await;
    insert_staff(&pool, ADMIN_USER, true, false, &["admin"]).await;
    insert_staff(&pool, RECEPTION_LEAD, true, false, &["receptionist"]).await;
    insert_staff(&pool, RECEPTION_PEER, true, false, &["receptionist"]).await;
    insert_staff(&pool, GUEST_STAFF, true, false, &["guest"]).await;

    let manager_role = role_id(&pool, "manager").await;
    let admin_role = role_id(&pool, "admin").await;
    let receptionist_role = role_id(&pool, "receptionist").await;
    let staff_role = role_id(&pool, "staff").await;
    let guest_role = role_id(&pool, "guest").await;

    // Super admin sets up two teams:
    // 1. `rh935_mgr_team` confers `manager` on `TEAM_MANAGER` (so TEAM_MANAGER
    //    only holds `staff` directly, but is a peer `manager` via the team).
    let mgr_team = teams_service::create_team(
        &pool,
        SUPER_ADMIN,
        TeamCreateInput {
            code: "rh935_mgr_team".to_string(),
            name: "RH935 Management Team".to_string(),
            description: None,
        },
    )
    .await
    .unwrap();
    teams_service::replace_team_roles(
        &pool,
        SUPER_ADMIN,
        mgr_team.id,
        TeamRoleIdsInput {
            role_ids: vec![manager_role],
        },
    )
    .await
    .unwrap();
    teams_service::add_member(
        &pool,
        SUPER_ADMIN,
        mgr_team.id,
        TeamMemberInput {
            user_id: TEAM_MANAGER,
            is_lead: false,
            expires_at: None,
        },
    )
    .await
    .unwrap();

    // 2. `rh935_desk_team` confers `staff` (40) and is led by `RECEPTION_LEAD` (60),
    //    with `SUPER_ADMIN` and `MANAGER_B` also on the roster.
    let desk_team = teams_service::create_team(
        &pool,
        SUPER_ADMIN,
        TeamCreateInput {
            code: "rh935_desk_team".to_string(),
            name: "RH935 Front Desk Team".to_string(),
            description: None,
        },
    )
    .await
    .unwrap();
    teams_service::replace_team_roles(
        &pool,
        SUPER_ADMIN,
        desk_team.id,
        TeamRoleIdsInput {
            role_ids: vec![staff_role],
        },
    )
    .await
    .unwrap();
    for (member, is_lead) in [
        (RECEPTION_LEAD, true),
        (SUPER_ADMIN, false),
        (MANAGER_B, false),
    ] {
        teams_service::add_member(
            &pool,
            SUPER_ADMIN,
            desk_team.id,
            TeamMemberInput {
                user_id: member,
                is_lead,
                expires_at: None,
            },
        )
        .await
        .unwrap();
    }
    rbac_cache::clear_all();

    // Effective user roles endpoint includes team-conferred roles sorted by priority DESC.
    let tm_view = users_service::user_roles_permissions(&pool, TEAM_MANAGER)
        .await
        .unwrap();
    let tm_roles: Vec<(&str, i64)> = tm_view
        .roles
        .iter()
        .map(|r| (r.name.as_str(), r.priority))
        .collect();
    assert_eq!(tm_roles, vec![("manager", 150), ("staff", 40)]);

    // Manager A cannot assign, remove, or replace roles on peers (direct or
    // team-conferred) or active/suspended super-admin accounts — even when the
    // role itself (`guest` = 20, `receptionist` = 60) is well below 150.
    for protected_target in [SUPER_ADMIN, SUSPENDED_SUPER_ADMIN, MANAGER_B, TEAM_MANAGER] {
        let assign_err = rbac_service::assign_role_to_user(
            &pool,
            MANAGER_A,
            AssignRoleInput {
                user_id: protected_target,
                role_id: guest_role,
            },
        )
        .await;
        assert!(
            matches!(assign_err, Err(ApiError::Forbidden(_))),
            "manager must not assign roles to protected target {protected_target}: {assign_err:?}"
        );

        let remove_err = rbac_service::remove_role_from_user(
            &pool,
            MANAGER_A,
            protected_target,
            receptionist_role,
        )
        .await;
        assert!(
            matches!(remove_err, Err(ApiError::Forbidden(_))),
            "manager must not remove roles from protected target {protected_target}: {remove_err:?}"
        );

        let replace_err = rbac_service::replace_user_roles(
            &pool,
            MANAGER_A,
            protected_target,
            UserRoleIdsInput {
                role_ids: vec![guest_role],
            },
        )
        .await;
        assert!(
            matches!(replace_err, Err(ApiError::Forbidden(_))),
            "manager must not replace roles on protected target {protected_target}: {replace_err:?}"
        );
    }

    // Positive control: Manager A (150) CAN manage Admin (100) roles.
    rbac_service::assign_role_to_user(
        &pool,
        MANAGER_A,
        AssignRoleInput {
            user_id: ADMIN_USER,
            role_id: receptionist_role,
        },
    )
    .await
    .expect("manager (150) can assign receptionist (60) to admin (100)");
    rbac_service::remove_role_from_user(&pool, MANAGER_A, ADMIN_USER, receptionist_role)
        .await
        .expect("manager (150) can remove receptionist (60) from admin (100)");
    rbac_service::replace_user_roles(
        &pool,
        MANAGER_A,
        ADMIN_USER,
        UserRoleIdsInput {
            role_ids: vec![admin_role],
        },
    )
    .await
    .expect("manager (150) can replace roles on admin (100)");

    // Team membership guards:
    // - Manager A cannot add/overwrite or remove SUPER_ADMIN, SUSPENDED_SUPER_ADMIN,
    //   MANAGER_B, or TEAM_MANAGER on `desk_team` (even though `desk_team` only confers `staff`).
    for protected_target in [SUPER_ADMIN, SUSPENDED_SUPER_ADMIN, MANAGER_B, TEAM_MANAGER] {
        let add_err = teams_service::add_member(
            &pool,
            MANAGER_A,
            desk_team.id,
            TeamMemberInput {
                user_id: protected_target,
                is_lead: false,
                expires_at: None,
            },
        )
        .await;
        assert!(
            matches!(add_err, Err(ApiError::Forbidden(_))),
            "manager must not add/upsert protected target {protected_target} on a team: {add_err:?}"
        );
    }
    for protected_member in [SUPER_ADMIN, MANAGER_B] {
        let rm_err =
            teams_service::remove_member(&pool, MANAGER_A, desk_team.id, protected_member).await;
        assert!(
            matches!(rm_err, Err(ApiError::Forbidden(_))),
            "manager must not remove protected member {protected_member} from a team: {rm_err:?}"
        );
    }

    // - Receptionist lead (60) CAN add and remove lower-ranked `GUEST_STAFF` (20) on `desk_team` (staff = 40),
    //   but CANNOT add/remove peer `RECEPTION_PEER` (60) or superior `ADMIN_USER` (100).
    teams_service::add_member(
        &pool,
        RECEPTION_LEAD,
        desk_team.id,
        TeamMemberInput {
            user_id: GUEST_STAFF,
            is_lead: false,
            expires_at: None,
        },
    )
    .await
    .expect("receptionist lead (60) can add lower-ranked user (20) to staff (40) team");
    teams_service::remove_member(&pool, RECEPTION_LEAD, desk_team.id, GUEST_STAFF)
        .await
        .expect("receptionist lead (60) can remove lower-ranked user (20) from staff (40) team");

    for disallowed_member in [RECEPTION_PEER, ADMIN_USER, SUPER_ADMIN] {
        let lead_add_err = teams_service::add_member(
            &pool,
            RECEPTION_LEAD,
            desk_team.id,
            TeamMemberInput {
                user_id: disallowed_member,
                is_lead: false,
                expires_at: None,
            },
        )
        .await;
        assert!(
            matches!(lead_add_err, Err(ApiError::Forbidden(_))),
            "receptionist lead (60) must not add peer/superior {disallowed_member}: {lead_add_err:?}"
        );
    }

    cleanup(&pool).await;
}
