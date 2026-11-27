//! Scenario registry: name → ordered module list. Every scenario includes its
//! dependencies (fixed ids make replays coherent — a scenario's bookings exist
//! because its module list contains the booking section that owns them).
//!
//! Dependency edges that matter:
//!   bookings_history  → bookings_ops + bookings_matrix (FK to bookings)
//!   booking_groups    → channels + bookings_ops + bookings_matrix (room windows & channels)
//!   finance           → bookings_ops + bookings_matrix + anonymous + booking_groups
//!                       (payments/invoices/ledgers FK to bookings; 804125/126
//!                       settle anonymous bookings 802202/802203)
//!   night_audit       → bookings_matrix (posted_nights/details ref 802001-45)
//!   marketing         → bookings_ops + bookings_matrix (voucher redemptions)
//!   loyalty           → finance (points ledger refs payment ids)
//!   webhook_fixtures  → bookings_matrix + anonymous (pending PayPal payments)
//!   support           → bookings_ops + bookings_matrix (conversations ref bookings)
//!   audit             → everything its resource_ids point at for coherence
//!   guest_access      → core (portal sessions FK to guests)
//!   auth_extra        → core (extends users)
//!   ekyc              → core (opt-in synthetic eKYC fixtures; excluded from full)
//!   scale             → core + channels + rooms_state (opt-in 850k-899k benchmark rows; excluded from full)

/// Canonical module execution order. `resolve` always emits modules in this
/// order regardless of the order scenarios were requested.
pub const MODULE_ORDER: &[&str] = &[
    "core",
    "channels",
    "rooms_state",
    "bookings_ops",
    "bookings_matrix",
    "bookings_history",
    "anonymous",
    "availability",
    "booking_groups",
    "operations",
    "night_audit",
    "finance",
    "marketing",
    "loyalty",
    "guest_access",
    "auth_extra",
    "support",
    "notifications",
    "webhook_fixtures",
    "audit",
    "ekyc",
    "scale",
];

/// Modules included in `full` / `--all` / bare `seed`. Opt-in sections
/// (`ekyc`, `scale`) must be requested explicitly by `--scenario`.
pub const DEFAULT_MODULES: &[&str] = &[
    "core",
    "channels",
    "rooms_state",
    "bookings_ops",
    "bookings_matrix",
    "bookings_history",
    "anonymous",
    "availability",
    "booking_groups",
    "operations",
    "night_audit",
    "finance",
    "marketing",
    "loyalty",
    "guest_access",
    "auth_extra",
    "support",
    "notifications",
    "webhook_fixtures",
    "audit",
];

pub struct Scenario {
    pub name: &'static str,
    pub description: &'static str,
    pub modules: &'static [&'static str],
}

pub const SCENARIOS: &[Scenario] = &[
    Scenario {
        name: "basic",
        description: "Working hotel: staff, inventory, rates, guests, today's front-desk bookings",
        modules: &["core", "rooms_state", "bookings_ops"],
    },
    Scenario {
        name: "channels",
        description: "Custom booking channels alongside dynamic pricing and commission rules",
        modules: &["core", "channels"],
    },
    Scenario {
        name: "availability",
        description: "Inventory grid: allocations, room status spread, sold-out and one-left dates",
        modules: &["core", "rooms_state", "availability"],
    },
    Scenario {
        name: "booking-lifecycle",
        description: "Every booking status: pending through checked_out, no_show, voided, comp stays, anonymous bookings",
        modules: &[
            "core",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "bookings_history",
            "anonymous",
        ],
    },
    Scenario {
        name: "booking-groups",
        description: "Multi-room booking groups in active/paid, active/unpaid-hold, and voided states",
        modules: &[
            "core",
            "channels",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "booking_groups",
        ],
    },
    Scenario {
        name: "frontdesk",
        description: "Today's operations: arrivals, departures, in-house, housekeeping board, maintenance",
        modules: &["core", "rooms_state", "bookings_ops", "operations"],
    },
    Scenario {
        name: "payments",
        description: "Every payment state, invoices, city ledgers, refund/partial/retry/receipt fixtures",
        modules: &[
            "core",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "anonymous",
            "finance",
            "webhook_fixtures",
        ],
    },
    Scenario {
        name: "webhooks",
        description: "Gateway-replay fixtures: pending PayPal payments with deterministic order ids (no webhook store exists)",
        modules: &[
            "core",
            "rooms_state",
            "bookings_matrix",
            "anonymous",
            "webhook_fixtures",
        ],
    },
    Scenario {
        name: "authentication",
        description: "RBAC users for every role plus locked/inactive/unverified, 2FA-enabled user, portal sessions",
        modules: &["core", "auth_extra", "guest_access"],
    },
    Scenario {
        name: "support",
        description: "Guest support inbox across all statuses with guest/staff/system messages and events",
        modules: &[
            "core",
            "channels",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "support",
        ],
    },
    Scenario {
        name: "audit",
        description: "Booking history, room status history, audit markers, night-audit runs, job history",
        modules: &[
            "core",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "bookings_history",
            "anonymous",
            "operations",
            "night_audit",
            "finance",
            "marketing",
            "audit",
        ],
    },
    Scenario {
        name: "notifications",
        description: "Staff notifications with read markers, email delivery states, the suppression list",
        modules: &[
            "core",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "marketing",
            "notifications",
        ],
    },
    Scenario {
        name: "edge-cases",
        description: "Boundary fixtures: adjacent/one-night/long stays, max occupancy, aging hold, sold-out date, retry-after-failure, anonymous flows",
        modules: &[
            "core",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "bookings_history",
            "anonymous",
            "availability",
            "finance",
        ],
    },
    Scenario {
        name: "operations",
        description: "Housekeeping board, maintenance tickets, room events, night-audit history",
        modules: &[
            "core",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "operations",
            "night_audit",
        ],
    },
    Scenario {
        name: "marketing",
        description: "Promotions in every status, vouchers/redemptions, segments, email campaigns",
        modules: &[
            "core",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "marketing",
        ],
    },
    Scenario {
        name: "loyalty",
        description: "Members, tiers, points ledger, reward catalog, redemptions, complimentary credits",
        modules: &[
            "core",
            "rooms_state",
            "bookings_ops",
            "bookings_matrix",
            "anonymous",
            "finance",
            "loyalty",
        ],
    },
    Scenario {
        name: "guest-access",
        description: "Anonymous bookings with access tokens, guest-portal sessions, user-guest links",
        modules: &["core", "rooms_state", "anonymous", "guest_access"],
    },
    Scenario {
        name: "ekyc",
        description: "Synthetic eKYC applications, notes, and decision history across review states (opt-in)",
        modules: &["core", "ekyc"],
    },
    Scenario {
        name: "scale",
        description: "High-volume 850k-899k benchmark dataset (1k guests, 2.5k bookings/payments, 2k audits) (opt-in)",
        modules: &["core", "channels", "rooms_state", "scale"],
    },
    Scenario {
        name: "full",
        description: "Complete default development hotel — every standard module (excludes opt-in ekyc and scale)",
        modules: DEFAULT_MODULES,
    },
];

/// Resolve requested scenario names to an ordered, deduplicated module list.
/// Unknown names error with the valid list.
pub fn resolve(names: &[String]) -> Result<Vec<&'static str>, String> {
    let mut wanted: Vec<&Scenario> = Vec::new();
    for name in names {
        match SCENARIOS.iter().find(|s| s.name == name.trim()) {
            Some(s) => wanted.push(s),
            None => {
                return Err(format!(
                    "unknown scenario '{name}' — run `seed --list` for valid names"
                ));
            }
        }
    }
    Ok(ordered_modules(
        wanted.iter().flat_map(|s| s.modules.iter().copied()),
    ))
}

/// Every default module in canonical order (`--all`, bare `seed`, and `full` converge).
pub fn resolve_all() -> Vec<&'static str> {
    DEFAULT_MODULES.to_vec()
}

/// Stable-order dedupe against MODULE_ORDER.
fn ordered_modules<'a>(iter: impl Iterator<Item = &'a str>) -> Vec<&'static str> {
    let selected: std::collections::HashSet<&str> = iter.collect();
    MODULE_ORDER
        .iter()
        .copied()
        .filter(|k| selected.contains(k))
        .collect()
}

pub fn list_text() -> String {
    let mut out = String::from("Available scenarios:\n");
    for s in SCENARIOS {
        out.push_str(&format!("  {:<18} {}\n", s.name, s.description));
    }
    out.push_str("\n`seed` with no flags applies `full`; `--all` is equivalent.\n");
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_module_in_scenarios_is_known_and_ordered() {
        for s in SCENARIOS {
            let resolved = ordered_modules(s.modules.iter().copied());
            // No unknown keys, no duplicates, emitted in MODULE_ORDER.
            assert_eq!(resolved.len(), s.modules.len(), "{}", s.name);
        }
    }

    #[test]
    fn unknown_scenario_names_error() {
        assert!(resolve(&["nope".to_string()]).is_err());
    }

    #[test]
    fn resolve_dedupes_and_orders() {
        let modules = resolve(&["payments".to_string(), "basic".to_string()]).unwrap();
        let pos = |k| MODULE_ORDER.iter().position(|m| *m == k).unwrap();
        for pair in modules.windows(2) {
            assert!(pos(pair[0]) < pos(pair[1]));
        }
        assert!(modules.contains(&"finance"));
        assert!(modules.contains(&"core"));
    }

    #[test]
    fn booking_history_runs_after_bookings() {
        let pos = |k| MODULE_ORDER.iter().position(|m| *m == k).unwrap();
        assert!(pos("bookings_history") > pos("bookings_matrix"));
        assert!(pos("bookings_history") > pos("bookings_ops"));
        assert!(pos("finance") > pos("anonymous"));
        assert!(pos("webhook_fixtures") > pos("anonymous"));
        assert!(pos("booking_groups") > pos("bookings_matrix"));
        assert!(pos("support") > pos("bookings_ops"));
    }

    #[test]
    fn resolve_all_excludes_opt_in_modules() {
        let defaults = resolve_all();
        assert!(!defaults.contains(&"ekyc"));
        assert!(!defaults.contains(&"scale"));
        assert!(defaults.contains(&"channels"));
        assert!(defaults.contains(&"booking_groups"));
        assert!(defaults.contains(&"support"));
        assert_eq!(defaults, resolve(&["full".to_string()]).unwrap());
    }

    #[test]
    fn opt_in_scenarios_compose_with_full() {
        let combined =
            resolve(&["full".to_string(), "ekyc".to_string(), "scale".to_string()]).unwrap();
        assert_eq!(combined.as_slice(), MODULE_ORDER);
    }

    #[test]
    fn every_new_scenario_resolves() {
        for name in ["channels", "booking-groups", "support", "ekyc", "scale"] {
            let modules = resolve(&[name.to_string()]).unwrap();
            assert!(!modules.is_empty(), "{name} must resolve modules");
        }
    }
}
