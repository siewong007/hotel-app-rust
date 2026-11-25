//! Guest Relations — the staff-facing guest 360 workspace.
//!
//! Phase 1 aggregates existing per-guest data (interaction notes on
//! `guest_notes`, `guest_preferences`, reviews, loyalty, vouchers,
//! communications, support conversations) behind the `/guests/{id}/...`
//! endpoints described in
//! `docs/architecture/overview.md`.

pub mod handlers;
pub mod models;
pub mod repository;
pub mod routes;
pub mod service;
pub mod validation;
