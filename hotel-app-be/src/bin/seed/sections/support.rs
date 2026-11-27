//! Guest support inbox (`support` default section).
//! Seeds conversations across all four workflow statuses (`waiting_for_staff`,
//! `waiting_for_guest`, `resolved`, `closed`) plus messages and timeline events,
//! including an active cancellation_request event paired with booking 802102.

use crate::engine::Tx;

pub async fn seed(tx: &mut Tx<'_>) -> Result<(), sqlx::Error> {
    sqlx::raw_sql(SQL).execute(&mut **tx).await?;
    Ok(())
}

const SQL: &str = r#"
INSERT INTO public.support_conversations (
    id, conversation_number, guest_id, booking_id, subject, category,
    status, priority, assigned_team, assigned_to_user_id,
    first_response_due_at, resolution_due_at, first_response_at,
    resolved_at, closed_at, resolution_code, resolution_summary,
    last_activity_at, created_at, updated_at
)
OVERRIDING SYSTEM VALUE VALUES
    (808951, 'SUP-STG-0001', 801003, 802102, 'Cancellation request for B-STG-1002', 'booking',
     'waiting_for_staff', 'high', 'front_desk', 800002,
     (SELECT today + 1 FROM staging_ref)::timestamptz + interval '10 hours',
     (SELECT today + 2 FROM staging_ref)::timestamptz + interval '10 hours',
     NULL, NULL, NULL, NULL, NULL,
     ((SELECT today FROM staging_ref)::timestamptz + interval '9 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '9 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '9 hours')),
    (808952, 'SUP-STG-0002', 801001, 802101, 'Extra pillow and late dinner setup', 'service_request',
     'waiting_for_guest', 'normal', 'front_desk', 800002,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '17 hours',
     (SELECT today + 1 FROM staging_ref)::timestamptz + interval '17 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '16 hours',
     NULL, NULL, NULL, NULL,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '16 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '15 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '16 hours'),
    (808953, 'SUP-STG-0003', 801009, 802106, 'PO number update on TechCorp invoice', 'billing',
     'resolved', 'normal', 'front_desk', 800006,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '11 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '14 hours',
     NULL, 'billing_updated', 'Added PO TC-2026-Q3 to invoice.',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '14 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '10 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '14 hours'),
    (808954, 'SUP-STG-0004', 801005, 802105, 'Room service wait time feedback', 'complaint',
     'closed', 'low', 'front_desk', 800001,
     (SELECT today - 6 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 4 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 6 FROM staging_ref)::timestamptz + interval '10 hours',
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '11 hours',
     (SELECT today - 4 FROM staging_ref)::timestamptz + interval '12 hours',
     'apology_voucher', 'Issued goodwill feedback response and closed ticket.',
     (SELECT today - 4 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 6 FROM staging_ref)::timestamptz + interval '9 hours',
     (SELECT today - 4 FROM staging_ref)::timestamptz + interval '12 hours');

INSERT INTO public.support_messages (
    id, conversation_id, author_type, author_guest_id, author_user_id, body, created_at
)
OVERRIDING SYSTEM VALUE VALUES
    (808961, 808951, 'guest', 801003, NULL,
     'My return flight was rescheduled. Could the team review cancelling the remaining nights on B-STG-1002?',
     (SELECT today FROM staging_ref)::timestamptz + interval '9 hours'),
    (808962, 808952, 'guest', 801001, NULL,
     'Could we have two extra firm pillows placed in room 803 before 9pm?',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '15 hours'),
    (808963, 808952, 'staff', NULL, 800002,
     'Housekeeping has noted your pillow preference. Would you also like a dinner tray time confirmed?',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '16 hours'),
    (808964, 808953, 'guest', 801009, NULL,
     'Please include PO TC-2026-Q3 on our TechCorp settlement receipt for B-STG-1006.',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '10 hours'),
    (808965, 808953, 'staff', NULL, 800006,
     'Updated the billing reference with PO TC-2026-Q3 and reissued the receipt copy.',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '14 hours'),
    (808966, 808954, 'guest', 801005, NULL,
     'Room service took longer than expected on evening delivery.',
     (SELECT today - 6 FROM staging_ref)::timestamptz + interval '9 hours'),
    (808967, 808954, 'staff', NULL, 800001,
     'Thank you for letting us know. We have shared this with our F&B supervisor.',
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '11 hours'),
    (808968, 808954, 'system', NULL, NULL,
     'Conversation closed after confirmation period.',
     (SELECT today - 4 FROM staging_ref)::timestamptz + interval '12 hours');

INSERT INTO public.support_events (
    id, conversation_id, actor_guest_id, actor_user_id, event_type,
    from_status, to_status, details, created_at
)
OVERRIDING SYSTEM VALUE VALUES
    (808971, 808951, 801003, NULL, 'created', NULL, 'waiting_for_staff',
     '{"kind":"cancellation_request"}'::jsonb,
     (SELECT today FROM staging_ref)::timestamptz + interval '9 hours'),
    (808972, 808952, 801001, NULL, 'created', NULL, 'waiting_for_staff',
     '{"kind":"general_support"}'::jsonb,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '15 hours'),
    (808973, 808952, NULL, 800002, 'staff_reply', 'waiting_for_staff', 'waiting_for_guest',
     NULL, (SELECT today - 1 FROM staging_ref)::timestamptz + interval '16 hours'),
    (808974, 808953, NULL, 800006, 'resolved', 'waiting_for_staff', 'resolved',
     '{"resolution_code":"billing_updated"}'::jsonb,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '14 hours'),
    (808975, 808954, NULL, 800001, 'resolved', 'waiting_for_staff', 'resolved',
     '{"resolution_code":"apology_voucher"}'::jsonb,
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '11 hours'),
    (808976, 808954, NULL, 800001, 'closed', 'resolved', 'closed',
     NULL, (SELECT today - 4 FROM staging_ref)::timestamptz + interval '12 hours');
"#;
