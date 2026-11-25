# Booking workflow — staff + guest portal

Booking CRUD + check-in/checkout + void + group bookings + guest-portal
self-service. Read `docs/architecture/overview.md` first. Code under
`hotel-app-be/src/modules/bookings/` and `modules/booking_groups/`; frontend
under `hotel-web-fe/src/features/bookings/` +
`hotel-web-fe/src/routes/_authenticated/bookings/`. Verified `2026-11-25` against
`master` (incl. patch `0032`).

## Layers

- `modules/bookings/routes.rs` (488 lines) — RBAC gate + dispatch, 36 `.route(` registrations.
- `modules/bookings/handlers.rs` — thin request → service wrappers:
  `void_booking_handler` (:102, renamed from `delete_booking_handler`),
  `manual_checkin_handler` (:125), `reactivate_booking_handler` (:260).
- `modules/bookings/service.rs` (1353 lines) — service-layer permission and
  cross-domain helpers: `can_book_with_credits_for_guest`,
  `cancel_pending_booking_by_guest`, `release_pending_payment_booking`
  (:290), `release_stale_unpaid_holds` (:499 — driven by
  `modules/bookings/unpaid_hold_scheduler.rs`), `void_booking` (:585),
  `manual_checkin` (:755), `checkin_booking_flow` (:794),
  `reactivate_booking` (:1057).
- `modules/bookings/lifecycle.rs` (4156 lines) — most booking logic: create /
  list / get / update / checkout / reactivate / void tx helpers.
- Split-out siblings in the same directory: `repository.rs`,
  `checkin_advisory.rs`, `complimentary.rs`, `credits.rs`, `auto_checkin.rs`,
  `unpaid_hold_scheduler.rs`, `group_create.rs`, `group_actions.rs`,
  `new_reservations.rs`, `emails.rs`, `helpers.rs`, `list.rs`, `queries.rs`,
  `summary.rs`, plus `modules/booking_groups/` (group voucher split/resplit &
  persistence).

## Status vocabulary

`bookings.status` CHECK constraint (`bookings_status_check`, updated in patch `0032`):
`pending`, `pending_payment`, `pending_confirmation`, `confirmed`,
`checked_in`, `auto_checked_in`, `late_checkout`, `checked_out`, `no_show`,
`completed`, `comp_void`, `partial_complimentary`, `fully_complimentary`, `voided`.
`cancelled`, `complimentary`, `unpaid`/`paid` (that's `payment_status`) are
**not** legal booking statuses.

`bookings_no_room_date_overlap` (updated in patch `0032`) blocks room/date overlap
across `pending`, `pending_payment`, `pending_confirmation`, `confirmed`,
`checked_in`, `auto_checked_in`, `late_checkout`, `partial_complimentary`, and
`fully_complimentary`.

## Routes (staff)

`modules/bookings/routes.rs` gates each of the 36 route registrations with
`bookings:read|create|update|delete|manage` (manage = delete/void/reactivate).
Guest-portal self-service lives in `modules/guest_portal/routes.rs` under
`/api/guest-portal` and public single/group booking in
`modules/guest_booking/routes.rs` under `/api/booking`.

## Key logic

- `create_booking_handler` (`lifecycle.rs`:1116) — locks the room row with
  `FOR UPDATE OF r` before insert, so a first insert can't race a
  conflicting one; physical `rooms.status = 'reserved'` holds apply on arrival
  day only (`reconcile_room_holds`, patch 0018). Channel economics resolve here
  when `booking_channel_id` is set: quote lookup computes `commission_amount`,
  `channel_pricing_snapshot`, and `net_revenue` — but only for
  non-staff-priced bookings (staff `daily_rates`/`room_rate_override` wins).
  Room and extra-bed rates are Service Tax (SST) inclusive (`service_tax_rate`);
  `canonical_tourism_tax_for_guest` supplies additive Tourism Tax (`RM10/night`
  for foreign tourists). Online check-in on channel bookings posts only the
  channel-settled room amount (`settlement_payment_methods`, patch 0023),
  holding back desk charges (`tourism_tax_amount`, `extra_bed_charge`, upgrade
  differences) for reception collection.
- `update_booking_handler` (`lifecycle.rs`:1580) — rebuilding `daily_rates`
  when dates/nights change; `ensure_checkout_balance_resolved` (:929) blocks
  checkout while balance outstanding, except company-billed;
  `auto_post_company_ledger`; the ledger delta sync re-replaces room lines;
  `fetch_booking_detail` joins `booking_channels`.
- `manual_checkin_handler` (`handlers.rs`:125) → `service.rs:manual_checkin`
  (:755) → `checkin_booking_flow` (:794) →
  `checkin_booking_flow_for_booking`.
- `void_booking_handler` (`handlers.rs`:102) → `service.rs:void_booking`
  (:585) → `lifecycle.rs:void_booking_tx` (:2758) which calls
  `release_room_tx` (:2834), `void_booking_payments_tx` (:2854),
  `void_booking_ledgers_tx` (:2937), `restore_complimentary_credits_tx`
  (:3008). Soft-voids (keeps the row for history).
- `reactivate_booking` (`service.rs`:1057) → `lifecycle.rs`.

## Frontend

`features/bookings/components/Bookings/BookingsPage.tsx` — the staff bookings list;
`features/bookings/components/Bookings/BookingDetailDrawer.tsx` (`createCheckoutInvoice`
→ `CheckoutInvoiceModal`) hosts the checkout flow including
`handleConfirmCheckout` (`CheckoutInvoiceModal.tsx`:611, posts to
`/api/invoices/generate/{booking_id}` then `/api/bookings/:id/checkout`). State
lives in page hooks under `features/bookings/hooks/`; there is no
`useBookingsPageState` hook (removed 2026-09-09).
