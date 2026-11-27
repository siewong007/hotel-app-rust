//! Seed engine: one connection, one transaction — guard, prelude, wipe,
//! generated-id sequence restart, selected sections, sequence resync, counts,
//! commit.
//!
//! The prelude mirrors what staging.sql established for psql: an advisory lock
//! so concurrent seed/patch runs serialize, the audit-mutation escape hatch
//! (the users delete fires `ON DELETE SET NULL` on append-only `audit_logs`),
//! the connection timezone pinned to `system_settings.timezone` so
//! `CURRENT_DATE` is the hotel business day, and the `staging_ref` temp table
//! every section reads its dates from.

use sqlx::Connection;
use sqlx::postgres::PgConnection;

use crate::sections;
use crate::wipe::WIPE_SQL;

pub type Tx<'a> = sqlx::Transaction<'a, sqlx::Postgres>;

/// Per-table band counts produced after a run.
pub struct RunSummary {
    pub ref_date: chrono::NaiveDate,
    pub counts: Vec<(&'static str, i64)>,
}

/// Tables reported in the summary, in display order.
const COUNTED_TABLES: &[(&str, &str)] = &[
    ("users", "users"),
    ("room types", "room_types"),
    ("rooms", "rooms"),
    ("amenities", "amenities"),
    ("rate plans", "rate_plans"),
    ("guests", "guests"),
    ("companies", "companies"),
    ("booking channels", "booking_channels"),
    ("booking groups", "booking_groups"),
    ("bookings", "bookings"),
    ("booking guests", "booking_guests"),
    ("payments", "payments"),
    ("invoices", "invoices"),
    ("city ledger entries", "customer_ledgers"),
    ("housekeeping tasks", "housekeeping_tasks"),
    ("maintenance tickets", "maintenance_tickets"),
    ("room history", "room_history"),
    ("night audit runs", "night_audit_runs"),
    ("promotions", "promotions"),
    ("vouchers", "vouchers"),
    ("email deliveries", "email_deliveries"),
    ("loyalty members", "loyalty_members"),
    ("support conversations", "support_conversations"),
    ("ekyc verifications", "ekyc_verifications"),
    ("staff notifications", "staff_notifications"),
    ("audit events", "audit_logs"),
];

async fn run_section(key: &str, tx: &mut Tx<'_>) -> Result<(), sqlx::Error> {
    match key {
        "core" => sections::core::seed(tx).await,
        "channels" => sections::channels::seed(tx).await,
        "rooms_state" => sections::rooms_state::seed(tx).await,
        "bookings_ops" => sections::bookings_ops::seed(tx).await,
        "bookings_matrix" => sections::bookings_matrix::seed(tx).await,
        "bookings_history" => sections::bookings_history::seed(tx).await,
        "anonymous" => sections::anonymous::seed(tx).await,
        "availability" => sections::availability::seed(tx).await,
        "booking_groups" => sections::booking_groups::seed(tx).await,
        "operations" => sections::operations::seed(tx).await,
        "night_audit" => sections::night_audit::seed(tx).await,
        "finance" => sections::finance::seed(tx).await,
        "marketing" => sections::marketing::seed(tx).await,
        "loyalty" => sections::loyalty::seed(tx).await,
        "guest_access" => sections::guest_access::seed(tx).await,
        "auth_extra" => sections::auth_extra::seed(tx).await,
        "support" => sections::support::seed(tx).await,
        "notifications" => sections::notifications::seed(tx).await,
        "webhook_fixtures" => sections::webhook_fixtures::seed(tx).await,
        "audit" => sections::audit::seed(tx).await,
        "ekyc" => sections::ekyc::seed(tx).await,
        "scale" => sections::scale::seed(tx).await,
        _ => unreachable!("registry only resolves known module keys: {key}"),
    }
}

/// Apply `module_keys` against `url`. Always wipes the seed band first so a
/// rerun yields identical state — never an append.
pub async fn run(
    url: &str,
    module_keys: &[&'static str],
    ref_date: Option<chrono::NaiveDate>,
    verify_after_seed: bool,
    staff_password_hash: Option<&str>,
) -> Result<RunSummary, anyhow::Error> {
    let mut conn = PgConnection::connect(url).await?;

    // Guard: this dataset requires a completed V1 installation (schema +
    // system bootstrap + patch catalog all come from `make db-baseline`).
    let installed: Option<i32> = sqlx::query_scalar(
        "SELECT 1 FROM public.hotel_schema_revisions WHERE generation = 1 AND version = 1",
    )
    .fetch_optional(&mut conn)
    .await?;
    if installed.is_none() {
        anyhow::bail!("seed requires an initialized V1 database — run `make db-baseline` first");
    }

    let mut tx = conn.begin().await?;

    // Serialize with any concurrent seed/patch runner (same lock key staging
    // used, so a straggler psql run cannot interleave either).
    sqlx::raw_sql("SELECT pg_advisory_xact_lock(hashtext('hotel_app_dev_seed'))")
        .execute(&mut *tx)
        .await?;

    // The wipe deletes seed users, which fires users->audit_logs ON DELETE SET
    // NULL on an append-only table; the documented fixture escape hatch is
    // required (the integration suite uses it for the same reason).
    sqlx::raw_sql("SET LOCAL app.allow_audit_mutation = 'on'")
        .execute(&mut *tx)
        .await?;

    // Match core/db.rs: CURRENT_DATE is the hotel business day.
    let timezone: Option<String> =
        sqlx::query_scalar("SELECT value FROM public.system_settings WHERE key = 'timezone'")
            .fetch_optional(&mut *tx)
            .await?;
    if let Some(tz) = timezone {
        sqlx::query("SELECT set_config('TimeZone', $1, true)")
            .bind(tz)
            .execute(&mut *tx)
            .await?;
    }

    if let Some(date) = ref_date {
        sqlx::query("SELECT set_config('staging.ref_date', $1, true)")
            .bind(date.to_string())
            .execute(&mut *tx)
            .await?;
    }
    sqlx::query(
        "CREATE TEMP TABLE staging_ref ON COMMIT DROP AS \
         SELECT COALESCE(current_setting('staging.ref_date', true)::date, CURRENT_DATE) AS today, \
                $1::text AS staff_password_hash",
    )
    .bind(staff_password_hash)
    .execute(&mut *tx)
    .await?;

    sqlx::raw_sql(WIPE_SQL).execute(&mut *tx).await?;

    // Rows a section creates through a function or trigger take their id from
    // the identity sequence. Restart it at the band floor before any section
    // runs, or those rows land below the band on a fresh database (missing
    // from the counts) and at the previous run's max+1 on every rerun.
    sqlx::raw_sql(RESTART_GENERATED_IDS_SQL)
        .execute(&mut *tx)
        .await?;

    for key in module_keys {
        run_section(key, &mut tx).await?;
    }

    sqlx::raw_sql(sqlx::AssertSqlSafe(&*resync_sql()))
        .execute(&mut *tx)
        .await?;

    if verify_after_seed && !module_keys.is_empty() {
        crate::verify::verify_invariants(&mut tx).await?;
    }

    let ref_date_used: chrono::NaiveDate = sqlx::query_scalar("SELECT today FROM staging_ref")
        .fetch_one(&mut *tx)
        .await?;

    let mut counts = Vec::with_capacity(COUNTED_TABLES.len());
    for (label, table) in COUNTED_TABLES {
        // `table` comes from the COUNTED_TABLES const whitelist, not user input.
        let count: i64 = sqlx::query_scalar(sqlx::AssertSqlSafe(format!(
            "SELECT COUNT(*) FROM public.{table} WHERE id BETWEEN 800000 AND 899999"
        )))
        .fetch_one(&mut *tx)
        .await?;
        counts.push((*label, count));
    }

    tx.commit().await?;
    Ok(RunSummary {
        ref_date: ref_date_used,
        counts,
    })
}

/// Sequences the sections draw ids from without naming them, restarted at the
/// band floor right after the wipe has emptied the band. Today that is only
/// housekeeping_tasks: update_room_status() adds a task for each room
/// rooms_state marks dirty or reserved_dirty, so those rows get the same ids
/// (800001 up) on every run; the explicit task ids sit above them. ALTER
/// SEQUENCE ... RESTART rather than setval(): it is transactional, so a failed
/// run rolls it back instead of leaving the sequence under the rows the
/// rollback restores.
const RESTART_GENERATED_IDS_SQL: &str = r#"
DO $$
BEGIN
    EXECUTE format('ALTER SEQUENCE public.housekeeping_tasks_id_seq RESTART WITH %s',
                   (SELECT GREATEST(COALESCE(MAX(id), 0), 800000) + 1 FROM public.housekeeping_tasks));
    EXECUTE format('ALTER SEQUENCE public.room_history_id_seq RESTART WITH %s',
                   (SELECT GREATEST(COALESCE(MAX(id), 0), 800000) + 1 FROM public.room_history));
END;
$$;
"#;

/// Identity-sequence resync list shared between `resync_sql()` and `verify.rs`.
pub const RESYNC_SEQUENCES: &[(&str, &str)] = &[
    ("users", "users_id_seq"),
    ("teams", "teams_id_seq"),
    ("room_types", "room_types_id_seq"),
    ("rooms", "rooms_id_seq"),
    ("rate_plans", "rate_plans_id_seq"),
    ("room_rates", "room_rates_id_seq"),
    ("guests", "guests_id_seq"),
    ("companies", "companies_id_seq"),
    (
        "corporate_account_contacts",
        "corporate_account_contacts_id_seq",
    ),
    ("booking_channels", "booking_channels_id_seq"),
    ("channel_pricing_rules", "channel_pricing_rules_id_seq"),
    (
        "channel_commission_rules",
        "channel_commission_rules_id_seq",
    ),
    ("booking_groups", "booking_groups_id_seq"),
    ("bookings", "bookings_id_seq"),
    ("booking_guests", "booking_guests_id_seq"),
    ("services", "services_id_seq"),
    ("payments", "payments_id_seq"),
    ("invoices", "invoices_id_seq"),
    ("customer_ledgers", "customer_ledgers_id_seq"),
    (
        "customer_ledger_payments",
        "customer_ledger_payments_id_seq",
    ),
    ("promotions", "promotions_id_seq"),
    ("vouchers", "vouchers_id_seq"),
    ("voucher_redemptions", "voucher_redemptions_id_seq"),
    (
        "voucher_redemption_allocations",
        "voucher_redemption_allocations_id_seq",
    ),
    ("housekeeping_tasks", "housekeeping_tasks_id_seq"),
    ("maintenance_tickets", "maintenance_tickets_id_seq"),
    ("room_events", "room_events_id_seq"),
    ("room_history", "room_history_id_seq"),
    ("night_audit_runs", "night_audit_runs_id_seq"),
    ("night_audit_details", "night_audit_details_id_seq"),
    (
        "night_audit_posted_nights",
        "night_audit_posted_nights_id_seq",
    ),
    ("email_templates", "email_templates_id_seq"),
    ("email_campaigns", "email_campaigns_id_seq"),
    ("email_deliveries", "email_deliveries_id_seq"),
    ("email_suppressions", "email_suppressions_id_seq"),
    ("loyalty_members", "loyalty_members_id_seq"),
    ("loyalty_accounts", "loyalty_accounts_id_seq"),
    ("loyalty_memberships", "loyalty_memberships_id_seq"),
    ("loyalty_transactions", "loyalty_transactions_id_seq"),
    ("loyalty_rewards", "loyalty_rewards_id_seq"),
    ("loyalty_redemptions", "loyalty_redemptions_id_seq"),
    (
        "guest_complimentary_credits",
        "guest_complimentary_credits_id_seq",
    ),
    ("guest_portal_sessions", "guest_portal_sessions_id_seq"),
    (
        "payment_retry_capabilities",
        "payment_retry_capabilities_id_seq",
    ),
    ("room_changes", "room_changes_id_seq"),
    ("guest_notes", "guest_notes_id_seq"),
    ("guest_documents", "guest_documents_id_seq"),
    ("guest_reviews", "guest_reviews_id_seq"),
    ("guest_segments", "guest_segments_id_seq"),
    ("user_guests", "user_guests_id_seq"),
    (
        "notification_subscriptions",
        "notification_subscriptions_id_seq",
    ),
    (
        "notification_consent_events",
        "notification_consent_events_id_seq",
    ),
    ("consent_records", "consent_records_id_seq"),
    ("staff_notifications", "staff_notifications_id_seq"),
    ("amenities", "amenities_id_seq"),
    ("audit_logs", "audit_logs_id_seq1"),
    ("self_checkin_events", "self_checkin_events_id_seq"),
    ("support_conversations", "support_conversations_id_seq"),
    ("support_messages", "support_messages_id_seq"),
    ("support_events", "support_events_id_seq"),
    ("ekyc_verifications", "ekyc_verifications_id_seq"),
    ("ekyc_notes", "ekyc_notes_id_seq"),
    ("ekyc_decision_history", "ekyc_decision_history_id_seq"),
];

/// Transactional sequence resync (`ALTER SEQUENCE ... RESTART WITH N` rolls back
/// cleanly if any post-seed invariant check aborts the seed transaction).
fn resync_sql() -> String {
    let mut sql = String::from("DO $$\nBEGIN\n");
    for (table, seq) in RESYNC_SEQUENCES {
        sql.push_str(&format!(
            "    EXECUTE format('ALTER SEQUENCE public.{seq} RESTART WITH %s', \
             (SELECT GREATEST(COALESCE(MAX(id), 0), 800000) + 1 FROM public.{table}));\n"
        ));
    }
    sql.push_str("END;\n$$;\n");
    sql
}
