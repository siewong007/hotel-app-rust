//! Temporary scratch-database completeness probe; never run against real data.
use axum::body::Body;
use hotel_app_be::models::{BackupImportMode, ExportScope, ImportExecuteRequest, ImportJobState};
use hotel_app_be::modules::data_transfer::{jobs, service};
use serde_json::Value;
use sqlx::postgres::PgPoolOptions;
use std::collections::BTreeSet;

#[tokio::test]
async fn all_scopes_and_full_system_restore_preserve_data() {
    let url = std::env::var("DATABASE_URL").expect("scratch DB required");
    assert!(url.ends_with("/backup_check"));
    let pool = PgPoolOptions::new()
        .max_connections(5)
        .connect(&url)
        .await
        .unwrap();
    let user_id: i64 = sqlx::query_scalar("SELECT id FROM users ORDER BY id LIMIT 1")
        .fetch_one(&pool)
        .await
        .unwrap();
    for scope in [
        ExportScope::Standard,
        ExportScope::Full,
        ExportScope::Backup,
        ExportScope::System,
    ] {
        let text = service::export_booking_data(&pool, scope).await.unwrap();
        let doc: Value = serde_json::from_str(&text).unwrap();
        let tables = doc["tables"].as_object().unwrap();
        let mut rows = 0usize;
        for (key, values) in tables {
            let expected: i64 =
                sqlx::query_scalar(sqlx::AssertSqlSafe(format!("SELECT count(*) FROM {key}")))
                    .fetch_one(&pool)
                    .await
                    .unwrap();
            assert_eq!(
                values.as_array().unwrap().len() as i64,
                expected,
                "missing rows in {key}"
            );
            assert_eq!(
                doc["integrity"]["entityRows"][key], expected,
                "wrong trailer for {key}"
            );
            rows += expected as usize;
        }
        let catalog: Vec<String> = sqlx::query_scalar("SELECT n.nspname || '.' || c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind IN ('r','p') AND NOT c.relispartition AND n.nspname IN ('public','app')").fetch_all(&pool).await.unwrap();
        let mut accounted: BTreeSet<String> = tables.keys().cloned().collect();
        for exclusion in doc["manifest"]["exclusions"].as_array().unwrap() {
            accounted.insert(exclusion["name"].as_str().unwrap().to_string());
        }
        if let Some(omitted) = doc["manifest"]["omitted"].as_array() {
            for key in omitted {
                accounted.insert(key.as_str().unwrap().to_string());
            }
        }
        let missing: Vec<_> = catalog
            .iter()
            .filter(|key| !accounted.contains(*key))
            .collect();
        assert!(missing.is_empty(), "unaccounted tables: {missing:?}");
        println!(
            "scope={scope:?}: {} tables, {rows} rows; no unaccounted tables",
            tables.len()
        );
    }
    let before: Value = serde_json::from_str(
        &service::export_booking_data(&pool, ExportScope::System)
            .await
            .unwrap(),
    )
    .unwrap();
    let selected: Vec<String> = before["tables"]
        .as_object()
        .unwrap()
        .keys()
        .filter(|key| *key != "public.hotel_schema_revisions")
        .cloned()
        .collect();
    let passphrase = "completeness-check-passphrase";
    let body = service::export_booking_data_body(
        &pool,
        user_id,
        ExportScope::System,
        Some(passphrase.to_string()),
    )
    .await
    .unwrap();
    let bytes = axum::body::to_bytes(body, usize::MAX).await.unwrap();
    println!("Backup bytes length: {}", bytes.len());

    // Decrypt it here if needed or send to the staging endpoint
    let upload = jobs::stage_backup_upload(Body::from(bytes.clone()))
        .await
        .expect("upload failed");
    let job = jobs::start_import_job(
        &pool,
        user_id,
        ImportExecuteRequest {
            upload_id: upload.upload_id,
            mode: BackupImportMode::Restore,
            on_conflict: None,
            tables: selected.clone(),
            confirm: true,
            passphrase: Some(passphrase.to_string()),
        },
    )
    .await
    .unwrap();
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(90);
    loop {
        let status = jobs::import_job_status(job.job_id).unwrap();
        if status.status != ImportJobState::Running {
            assert_eq!(
                status.status,
                ImportJobState::Succeeded,
                "restore failed: {:?}",
                status.error
            );
            let result = status.result.unwrap();
            println!(
                "system restore: inserted={}, updated={}, skipped={}",
                result.inserted, result.updated, result.skipped
            );
            assert_eq!(
                result.skipped, 0,
                "restore skipped data: {:?}",
                result.report
            );
            break;
        }
        assert!(std::time::Instant::now() < deadline);
        tokio::time::sleep(std::time::Duration::from_millis(100)).await;
    }
    let after: Value = serde_json::from_str(
        &service::export_booking_data(&pool, ExportScope::System)
            .await
            .unwrap(),
    )
    .unwrap();
    let mut changed = Vec::new();
    for key in &selected {
        if key == "public.audit_logs" {
            continue;
        } // the transfer itself adds audit events
        let canonical = |doc: &Value| {
            let mut rows: Vec<_> = doc["tables"][key]
                .as_array()
                .unwrap()
                .iter()
                .map(|v| v.to_string())
                .collect();
            rows.sort();
            rows
        };
        if canonical(&before) != canonical(&after) {
            changed.push(key.clone());
        }
    }
    assert!(
        changed.is_empty(),
        "tables changed after restore: {changed:?}"
    );
    println!(
        "full system restore matched every selected table except expected transfer audit events"
    );
}
