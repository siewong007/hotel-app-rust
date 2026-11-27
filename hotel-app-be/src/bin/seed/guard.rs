//! Safety gate: refuse to touch production, and gate `--reset` on an explicit
//! development signal. There is deliberately no override that would let a reset
//! run against a production-marked environment.

/// Resolved deployment environment, using the app's own precedence
/// (APP_ENV first, ENVIRONMENT second — see hotel-app-be/.env.example).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeployEnv {
    Production,
    Development,
    /// Unset or unrecognized values.
    Unknown,
}

/// Resolve APP_ENV then ENVIRONMENT (app precedence order) into a DeployEnv.
/// `read` is `|k| env::var(k).ok()` in production code and a map lookup in tests.
pub fn resolve_env(read: impl Fn(&str) -> Option<String>) -> DeployEnv {
    for key in ["APP_ENV", "ENVIRONMENT"] {
        let Some(value) = read(key) else { continue };
        match value.trim().to_ascii_lowercase().as_str() {
            "production" | "prod" => return DeployEnv::Production,
            "development" | "dev" | "local" | "test" | "testing" | "staging" => {
                return DeployEnv::Development;
            }
            _ => continue,
        }
    }
    DeployEnv::Unknown
}

/// Extract the host out of a postgres URL (`postgres://user:pass@host:port/db`,
/// unix-socket `?host=/path` form included). Returns "" when unparseable —
/// callers must treat that as *not* loopback.
pub fn db_host(url: &str) -> &str {
    let after_scheme = url.split_once("://").map(|(_, rest)| rest).unwrap_or(url);
    let authority = after_scheme.split('/').next().unwrap_or_default();
    let hostport = authority.rsplit('@').next().unwrap_or_default();
    if let Some(rest) = hostport.strip_prefix('[') {
        rest.split(']').next().unwrap_or_default()
    } else {
        hostport.split(':').next().unwrap_or_default()
    }
}

fn is_loopback_host(host: &str) -> bool {
    host.is_empty()
        || host == "localhost"
        || host.ends_with(".localhost")
        || host.starts_with("127.")
        || host == "::1"
        || host.starts_with('/') // unix socket directory
        || host.starts_with("%2F") // url-encoded unix socket
}

pub fn is_loopback_url(database_url: &str) -> bool {
    is_loopback_host(db_host(database_url))
}

/// `--reset` may run only when the environment is explicitly development, or
/// when nothing claims an environment and the database is plainly local.
/// Production-marked environments are never resettable — no escape hatch.
pub fn reset_permitted(env: DeployEnv, database_url: &str) -> bool {
    match env {
        DeployEnv::Production => false,
        DeployEnv::Development => true,
        DeployEnv::Unknown => is_loopback_host(db_host(database_url)),
    }
}

/// Every seed operation refuses a production-marked environment, not just
/// `--reset`: inserting fake guests/bookings into production is the disaster
/// this gate exists for.
pub fn require_not_production(env: DeployEnv) -> Result<(), String> {
    match env {
        DeployEnv::Production => {
            Err("refusing to seed: APP_ENV/ENVIRONMENT resolves to production".to_string())
        }
        _ => Ok(()),
    }
}

pub fn require_reset_permitted(env: DeployEnv, database_url: &str) -> Result<(), String> {
    if !reset_permitted(env, database_url) {
        return Err(
            "refusing --reset: set APP_ENV/ENVIRONMENT to a development value, or run \
             against a loopback DATABASE_URL"
                .to_string(),
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn env_from(pairs: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
        let map: HashMap<String, String> = pairs
            .iter()
            .map(|(k, v)| ((*k).to_string(), (*v).to_string()))
            .collect();
        move |key| map.get(key).cloned()
    }

    #[test]
    fn resolve_env_prefers_app_env_over_environment() {
        assert_eq!(
            resolve_env(env_from(&[
                ("APP_ENV", "Production"),
                ("ENVIRONMENT", "development"),
            ])),
            DeployEnv::Production
        );
        assert_eq!(
            resolve_env(env_from(
                &[("APP_ENV", "STAGING"), ("ENVIRONMENT", "prod"),]
            )),
            DeployEnv::Development
        );
        assert_eq!(
            resolve_env(env_from(&[("ENVIRONMENT", "local")])),
            DeployEnv::Development
        );
        assert_eq!(
            resolve_env(env_from(&[("APP_ENV", "custom")])),
            DeployEnv::Unknown
        );
    }

    #[test]
    fn db_host_handles_urls_ipv6_and_unix_sockets() {
        assert_eq!(
            db_host("postgres://user:pass@127.0.0.1:5432/db"),
            "127.0.0.1"
        );
        assert_eq!(
            db_host("postgres://user@localhost:55432/test_pg19"),
            "localhost"
        );
        assert_eq!(db_host("postgres://user@[::1]:5432/db"), "::1");
        assert_eq!(
            db_host("postgres://user@[2001:db8::1]:5432/db"),
            "2001:db8::1"
        );
        assert!(!reset_permitted(
            DeployEnv::Unknown,
            "postgres://user@[2001:db8::1]:5432/db"
        ));
        assert_eq!(db_host("postgres:///db?host=/var/run/postgresql"), "");
        assert_eq!(
            db_host("postgres://prod.example.com:5432/hotel"),
            "prod.example.com"
        );
    }

    #[test]
    fn reset_and_production_guards_enforce_safety() {
        assert!(require_not_production(DeployEnv::Production).is_err());
        assert!(require_not_production(DeployEnv::Development).is_ok());
        assert!(require_not_production(DeployEnv::Unknown).is_ok());

        assert!(!reset_permitted(
            DeployEnv::Production,
            "postgres://user@localhost:5432/db"
        ));
        assert!(reset_permitted(
            DeployEnv::Development,
            "postgres://db.internal:5432/dev"
        ));
        assert!(reset_permitted(
            DeployEnv::Unknown,
            "postgres://user@127.0.0.1:5432/db"
        ));
        assert!(!reset_permitted(
            DeployEnv::Unknown,
            "postgres://user@prod-db.internal:5432/db"
        ));
        assert!(
            require_reset_permitted(DeployEnv::Unknown, "postgres://user@remote-host:5432/db")
                .is_err()
        );
    }
}
