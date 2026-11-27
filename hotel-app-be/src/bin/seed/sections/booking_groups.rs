//! Multi-room booking groups (`booking_groups` default section).
//! Seeds three groups (active+paid, active+unpaid with hold_expires_at and
//! portal_request_id, and voided) across unused future stay windows (`+70..+82`).

use crate::engine::Tx;

pub async fn seed(tx: &mut Tx<'_>) -> Result<(), sqlx::Error> {
    sqlx::raw_sql(SQL).execute(&mut **tx).await?;
    Ok(())
}

const SQL: &str = r#"
INSERT INTO public.booking_groups (
    id, group_number, guest_id, check_in_date, check_out_date,
    source, status, payment_status, payment_method, room_count,
    subtotal, discount_amount, tax_amount, tourism_tax_amount, total_amount,
    currency, hold_expires_at, portal_request_id, created_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE VALUES
    (802501, 'GRP-STG-0001', 801001,
     (SELECT today + 70 FROM staging_ref), (SELECT today + 73 FROM staging_ref),
     'website', 'active', 'paid', 'online_payment', 2,
     900.00, 0.00, 72.00, 0.00, 972.00, 'MYR',
     NULL, 'stg-grp-req-001', 800002,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours'),
    (802502, 'GRP-STG-0002', 801002,
     (SELECT today + 75 FROM staging_ref), (SELECT today + 77 FROM staging_ref),
     'website', 'active', 'unpaid', NULL, 2,
     1000.00, 0.00, 80.00, 0.00, 1080.00, 'MYR',
     (SELECT today + 1 FROM staging_ref)::timestamptz + interval '12 hours', 'stg-grp-req-002', 800003,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '12 hours'),
    (802503, 'GRP-STG-0003', 801003,
     (SELECT today + 80 FROM staging_ref), (SELECT today + 82 FROM staging_ref),
     'ota', 'voided', 'void', 'online_payment', 2,
     1000.00, 0.00, 80.00, 0.00, 1080.00, 'MYR',
     NULL, 'stg-grp-req-003', 800001,
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '12 hours');

INSERT INTO public.bookings (
    id, booking_number, guest_id, guest_name, guest_email, guest_phone,
    room_id, booking_group_id, check_in_date, check_out_date,
    adults, children, infants, rate_plan_id, room_rate, subtotal, tax_amount,
    discount_amount, discount_percentage, total_amount, currency,
    status, payment_status, payment_method, source, channel, booking_channel_id,
    cancelled_at, cancelled_by, cancellation_reason,
    created_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE VALUES
    -- Group 802501: active & paid (2 STD rooms x 3 nights)
    (802511, 'B-STG-2511', 801001, 'Aisha R.', 'aisha.rahman@staging.hotel-app.test', '+60-12-600-1001',
     800311, 802501, (SELECT today + 70 FROM staging_ref), (SELECT today + 73 FROM staging_ref),
     1, 0, 0, 800501, 150.00, 450.00, 36.00, 0.00, 0.00, 486.00, 'MYR',
     'confirmed', 'paid', 'online_payment', 'website', 'Direct Website',
     (SELECT id FROM public.booking_channels WHERE name = 'Direct Website'),
     NULL, NULL, NULL,
     800002,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours'),
    (802512, 'B-STG-2512', 801001, 'Aisha R.', 'aisha.rahman@staging.hotel-app.test', '+60-12-600-1001',
     800312, 802501, (SELECT today + 70 FROM staging_ref), (SELECT today + 73 FROM staging_ref),
     1, 0, 0, 800501, 150.00, 450.00, 36.00, 0.00, 0.00, 486.00, 'MYR',
     'confirmed', 'paid', 'online_payment', 'website', 'Direct Website',
     (SELECT id FROM public.booking_channels WHERE name = 'Direct Website'),
     NULL, NULL, NULL,
     800002,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours'),
    -- Group 802502: active unpaid hold (2 DLX rooms x 2 nights)
    (802513, 'B-STG-2513', 801002, 'Marcus T.', 'marcus.tan@staging.hotel-app.test', '+60-12-600-1002',
     800314, 802502, (SELECT today + 75 FROM staging_ref), (SELECT today + 77 FROM staging_ref),
     1, 0, 0, 800501, 250.00, 500.00, 40.00, 0.00, 0.00, 540.00, 'MYR',
     'pending_payment', 'unpaid', NULL, 'website', 'Direct Website',
     (SELECT id FROM public.booking_channels WHERE name = 'Direct Website'),
     NULL, NULL, NULL,
     800003,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '12 hours'),
    (802514, 'B-STG-2514', 801002, 'Marcus T.', 'marcus.tan@staging.hotel-app.test', '+60-12-600-1002',
     800315, 802502, (SELECT today + 75 FROM staging_ref), (SELECT today + 77 FROM staging_ref),
     1, 0, 0, 800501, 250.00, 500.00, 40.00, 0.00, 0.00, 540.00, 'MYR',
     'pending_payment', 'unpaid', NULL, 'website', 'Direct Website',
     (SELECT id FROM public.booking_channels WHERE name = 'Direct Website'),
     NULL, NULL, NULL,
     800003,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '12 hours'),
    -- Group 802503: voided group (2 DLX rooms x 2 nights)
    (802515, 'B-STG-2515', 801003, 'Emily W.', 'emily.wilson@staging.hotel-app.test', '+44-7700-901003',
     800316, 802503, (SELECT today + 80 FROM staging_ref), (SELECT today + 82 FROM staging_ref),
     1, 0, 0, 800501, 250.00, 500.00, 40.00, 0.00, 0.00, 540.00, 'MYR',
     'voided', 'void', 'online_payment', 'ota', 'Booking.com',
     (SELECT id FROM public.booking_channels WHERE name = 'Booking.com'),
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '12 hours', 800001, 'Group reservation cancelled by guest',
     800001,
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '12 hours'),
    (802516, 'B-STG-2516', 801003, 'Emily W.', 'emily.wilson@staging.hotel-app.test', '+44-7700-901003',
     800317, 802503, (SELECT today + 80 FROM staging_ref), (SELECT today + 82 FROM staging_ref),
     1, 0, 0, 800501, 250.00, 500.00, 40.00, 0.00, 0.00, 540.00, 'MYR',
     'voided', 'void', 'online_payment', 'ota', 'Booking.com',
     (SELECT id FROM public.booking_channels WHERE name = 'Booking.com'),
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '12 hours', 800001, 'Group reservation cancelled by guest',
     800001,
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '12 hours',
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '12 hours');

-- Payments settling group 802501 with matching group_payment_ref metadata.
INSERT INTO public.payments (
    id, booking_id, amount, currency, payment_method, payment_type,
    transaction_id, payment_gateway, status, metadata,
    processed_at, processed_by, idempotency_key, created_by, created_at
)
OVERRIDING SYSTEM VALUE VALUES
    (804151, 802511, 486.00, 'MYR', 'online_payment', 'booking',
     'TXN-STG-GRP-2511', 'stripe', 'completed', '{"group_payment_ref":"GRP-PAY-STG-001"}'::jsonb,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours', 800006, 'stg-pay-grp-2511', 800006,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours'),
    (804152, 802512, 486.00, 'MYR', 'online_payment', 'booking',
     'TXN-STG-GRP-2512', 'stripe', 'completed', '{"group_payment_ref":"GRP-PAY-STG-001"}'::jsonb,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours', 800006, 'stg-pay-grp-2512', 800006,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '12 hours');

-- Reconcile parent group totals directly from persisted child bookings (including
-- any trigger-computed tourism_tax_amount on foreign guest stays).
UPDATE public.booking_groups bg
SET room_count = s.cnt,
    subtotal = s.sub,
    discount_amount = s.disc,
    tax_amount = s.tax,
    tourism_tax_amount = s.ttax,
    total_amount = s.tot
FROM (
    SELECT booking_group_id,
           COUNT(*)::int AS cnt,
           COALESCE(SUM(subtotal), 0) AS sub,
           COALESCE(SUM(discount_amount), 0) AS disc,
           COALESCE(SUM(tax_amount), 0) AS tax,
           COALESCE(SUM(tourism_tax_amount), 0) AS ttax,
           COALESCE(SUM(total_amount), 0) AS tot
    FROM public.bookings
    WHERE booking_group_id BETWEEN 802501 AND 802503
    GROUP BY booking_group_id
) s
WHERE bg.id = s.booking_group_id;
"#;
