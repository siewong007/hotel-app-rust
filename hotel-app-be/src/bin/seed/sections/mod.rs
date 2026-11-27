//! Fixture sections. Each module owns a slice of the 800000-899999 id band and
//! only INSERTs — the wipe pass runs first for every invocation, so sections
//! never need their own cleanup.

pub mod anonymous;
pub mod audit;
pub mod auth_extra;
pub mod availability;
pub mod booking_groups;
pub mod bookings_history;
pub mod bookings_matrix;
pub mod bookings_ops;
pub mod channels;
pub mod core;
pub mod ekyc;
pub mod finance;
pub mod guest_access;
pub mod loyalty;
pub mod marketing;
pub mod night_audit;
pub mod notifications;
pub mod operations;
pub mod rooms_state;
pub mod scale;
pub mod support;
pub mod webhook_fixtures;
