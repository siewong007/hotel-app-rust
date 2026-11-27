//! Opt-in high-volume performance dataset (`--scenario scale`, IDs `850000..899999`).
//! Excluded from default `full` / `--all` runs. Populates set-based deterministic
//! data using `generate_series` for benchmarking indexes, pagination, analytics,
//! search, and PG19 planner behavior:
//!   - 1,000 guests (`850001..851000`)
//!   - 2,500 completed historical bookings (`855001..857500`)
//!   - 2,500 completed payments (`860001..862500`)
//!   -   500 paid invoices (`865001..865500`)
//!   -   400 customer ledgers (`868001..868400`) + 400 ledger payments (`869001..869400`)
//!   - 1,000 housekeeping tasks (`870001..871000`)
//!   - 2,000 id-guarded audit log entries (`875001..877000`)

use crate::engine::Tx;

pub async fn seed(tx: &mut Tx<'_>) -> Result<(), sqlx::Error> {
    sqlx::raw_sql(SQL).execute(&mut **tx).await?;
    Ok(())
}

const SQL: &str = r#"
-- 1,000 deterministic scale guests (`850001..851000`).
INSERT INTO public.guests (
    id, nick_name, first_name, last_name, email, phone,
    date_of_birth, nationality, country, id_type, id_number,
    marketing_opt_in, guest_type, tourism_type, total_stays, total_spend,
    tags, is_active, created_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE
SELECT
    850000 + n,
    meta.fn || ' ' || meta.ln || ' S' || n::text,
    meta.fn,
    meta.ln,
    'scale.guest.' || n::text || '@staging.hotel-app.test',
    '+60-19-850-' || lpad(n::text, 4, '0'),
    DATE '1965-01-01' + ((n * 29) % 14000),
    meta.nat,
    meta.cnt,
    CASE WHEN meta.nat = 'Malaysian' THEN 'national_id' ELSE 'passport' END::public.identificationtype,
    'SCL-ID-' || lpad((850000 + n)::text, 6, '0'),
    (n % 2) = 0,
    CASE WHEN n % 3 = 0 THEN 'member' ELSE 'non_member' END::public.guest_type,
    CASE WHEN meta.nat = 'Malaysian' THEN 'local' ELSE 'foreign' END::public.tourism_type,
    1 + (n % 8),
    ((1 + (n % 8)) * 320)::numeric(10,2),
    CASE WHEN n % 5 = 0 THEN ARRAY['repeat', 'corporate'] ELSE ARRAY['leisure'] END,
    true,
    800003,
    ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours') - ((n % 180) * interval '1 day'),
    ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours') - ((n % 90) * interval '1 day')
FROM generate_series(1, 1000) AS n
CROSS JOIN LATERAL (
    SELECT
        (ARRAY['Amina','Daniel','Farah','Gabriel','Hana','Irfan','Jie','Kavita','Liam','Nadia'])[n % 10 + 1] AS fn,
        (ARRAY['Abdullah','Chong','Fernandez','Goh','Ibrahim','Kumar','Lee','Menon','Ng','Tan'])[n % 10 + 1] AS ln,
        (ARRAY['Malaysian','Malaysian','Singaporean','Malaysian','Malaysian','Indian','Chinese','Malaysian','Australian','Malaysian'])[n % 10 + 1] AS nat,
        (ARRAY['Malaysia','Malaysia','Singapore','Malaysia','Malaysia','India','China','Malaysia','Australia','Malaysia'])[n % 10 + 1] AS cnt
) AS meta;

-- 2,500 completed historical bookings (`855001..857500`).
INSERT INTO public.bookings (
    id, booking_number, guest_id, guest_name, guest_email,
    room_id, check_in_date, check_out_date, adults, children,
    rate_plan_id, room_rate, subtotal, tax_amount, discount_amount, discount_percentage,
    total_amount, currency, status, payment_status, source,
    booking_channel_id, channel, actual_check_in, actual_check_out,
    is_posted, posted_date, posted_by, created_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE
SELECT
    855000 + n,
    'B-SCL-' || lpad(n::text, 5, '0'),
    gst.id,
    gst.nick_name,
    gst.email,
    rm.id,
    stay.ci,
    stay.ci + stay.nights,
    1,
    0,
    800501 + (n % 4),
    rt.base_price,
    ROUND(rt.base_price * stay.nights, 2),
    ROUND(rt.base_price * stay.nights * 0.08, 2),
    0.00,
    0.00,
    ROUND(rt.base_price * stay.nights * 1.08, 2),
    'MYR',
    'completed',
    'paid',
    (ARRAY['direct','website','ota','phone','walk_in'])[n % 5 + 1],
    bc.id,
    bc.name,
    stay.ci::timestamptz + interval '15 hours',
    (stay.ci + stay.nights)::timestamptz + interval '11 hours',
    true,
    stay.ci + stay.nights,
    800002,
    CASE WHEN n % 2 = 0 THEN 800002 ELSE 800003 END,
    (stay.ci - (2 + (n % 10)))::timestamptz + interval '12 hours',
    (stay.ci + stay.nights)::timestamptz + interval '12 hours'
FROM generate_series(1, 2500) AS n
JOIN public.guests gst ON gst.id = 850001 + ((n - 1) % 1000)
JOIN public.rooms rm ON rm.id = 800301 + ((n - 1) % 24)
JOIN public.room_types rt ON rt.id = rm.room_type_id
CROSS JOIN LATERAL (
    SELECT (SELECT today FROM staging_ref) - 730 + ((n - 1) % 700) AS ci,
           1 + (n % 4) AS nights
) AS stay
LEFT JOIN public.booking_channels bc ON bc.name = CASE n % 5
    WHEN 0 THEN 'Direct'
    WHEN 1 THEN 'Direct Website'
    WHEN 2 THEN 'Booking.com'
    WHEN 3 THEN 'Phone'
    ELSE 'Walk-in'
END;

-- 2,500 completed payments (`860001..862500`) matching the scale bookings exactly.
INSERT INTO public.payments (
    id, booking_id, amount, currency, payment_method, payment_type,
    transaction_id, payment_gateway, status, processed_at, processed_by,
    idempotency_key, created_by, created_at
)
OVERRIDING SYSTEM VALUE
SELECT
    860000 + (b.id - 855000),
    b.id,
    b.total_amount + COALESCE(b.tourism_tax_amount, 0) + COALESCE(b.extra_bed_charge, 0),
    'MYR',
    (ARRAY['credit_card','cash','online_payment','ewallet','bank_transfer','debit_card'])[(b.id - 855000) % 6 + 1],
    'booking',
    'TXN-SCL-' || lpad((b.id - 855000)::text, 5, '0'),
    'stripe',
    'completed',
    b.created_at + interval '1 hour',
    800006,
    'scl-pay-' || lpad((b.id - 855000)::text, 5, '0'),
    800006,
    b.created_at + interval '1 hour'
FROM public.bookings b
WHERE b.id BETWEEN 855001 AND 857500
ORDER BY b.id;

-- 500 paid invoices (`865001..865500`) across the first 500 scale bookings.
INSERT INTO public.invoices (
    id, invoice_number, booking_id, bill_to_guest_id, bill_to_corporate_id,
    billing_name, billing_email, issue_date, due_date,
    subtotal, tax_amount, discount_amount, total_amount, paid_amount,
    currency, line_items, status, invoice_type, room_charges, service_charges,
    created_by, sent_at, paid_at, created_at, updated_at
)
OVERRIDING SYSTEM VALUE
SELECT
    865000 + (b.id - 855000),
    'INV-SCL-' || lpad((b.id - 855000)::text, 5, '0'),
    b.id,
    b.guest_id,
    NULL,
    b.guest_name,
    b.guest_email,
    b.check_in_date,
    b.check_out_date,
    b.subtotal,
    b.tax_amount + COALESCE(b.tourism_tax_amount, 0),
    b.discount_amount,
    b.total_amount + COALESCE(b.tourism_tax_amount, 0),
    b.total_amount + COALESCE(b.tourism_tax_amount, 0),
    'MYR',
    jsonb_build_array(jsonb_build_object(
        'description', 'Room stay ' || b.booking_number,
        'quantity', GREATEST(b.check_out_date - b.check_in_date, 1),
        'unit_price', b.room_rate,
        'amount', b.subtotal
    )),
    'paid',
    'booking',
    b.subtotal,
    0.00,
    800006,
    b.check_in_date::timestamptz + interval '12 hours',
    b.check_out_date::timestamptz + interval '10 hours',
    b.check_in_date::timestamptz + interval '12 hours',
    b.check_out_date::timestamptz + interval '10 hours'
FROM public.bookings b
WHERE b.id BETWEEN 855001 AND 855500
ORDER BY b.id;

-- 400 customer ledgers (`868001..868400`) + 400 ledger payments (`869001..869400`).
INSERT INTO public.customer_ledgers (
    id, company_name, company_registration_number, contact_person, contact_email,
    description, expense_type, amount, currency, status, paid_amount,
    folio_number, invoice_number, invoice_date, due_date,
    post_type, transaction_type, folio_type, posting_date, transaction_date,
    is_posted, posted_at, created_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE
SELECT
    868000 + n,
    CASE WHEN n % 2 = 0 THEN 'TechCorp Solutions Sdn Bhd' ELSE 'Globex (Malaysia) Sdn Bhd' END,
    CASE WHEN n % 2 = 0 THEN 'SA-2011-00442' ELSE 'SA-2009-00117' END,
    CASE WHEN n % 2 = 0 THEN 'Diana Lim' ELSE 'Harold Sim' END,
    CASE WHEN n % 2 = 0 THEN 'ap@techcorp.staging.hotel-app.test' ELSE 'travel@globex.staging.hotel-app.test' END,
    'Scale corporate stay batch #' || n::text,
    'accommodation',
    ((2 + (n % 5)) * 150)::numeric(10,2),
    'MYR',
    CASE WHEN n % 2 = 0 THEN 'paid' ELSE 'partial' END,
    CASE WHEN n % 2 = 0
         THEN ((2 + (n % 5)) * 150)::numeric(10,2)
         ELSE ROUND(((2 + (n % 5)) * 150)::numeric / 2, 2)
    END,
    'FOL-SCL-' || lpad(n::text, 5, '0'),
    'INV-SCL-CL-' || lpad(n::text, 5, '0'),
    (SELECT today FROM staging_ref) - ((n % 120) + 1),
    (SELECT today FROM staging_ref) - (n % 120) + 30,
    'room_charge',
    'debit',
    'city_ledger',
    (SELECT today FROM staging_ref) - ((n % 120) + 1),
    (SELECT today FROM staging_ref) - ((n % 120) + 1),
    true,
    ((SELECT today FROM staging_ref) - ((n % 120) + 1))::timestamptz + interval '18 hours',
    800006,
    ((SELECT today FROM staging_ref) - ((n % 120) + 1))::timestamptz + interval '12 hours',
    ((SELECT today FROM staging_ref) - ((n % 120) + 1))::timestamptz + interval '18 hours'
FROM generate_series(1, 400) AS n;

INSERT INTO public.customer_ledger_payments (
    id, ledger_id, payment_amount, payment_method, payment_reference,
    payment_date, receipt_number, notes, processed_by, created_at
)
OVERRIDING SYSTEM VALUE
SELECT
    869000 + (cl.id - 868000),
    cl.id,
    cl.paid_amount,
    'bank_transfer',
    'TT-SCL-' || lpad((cl.id - 868000)::text, 5, '0'),
    cl.posted_at + interval '1 day',
    'RCP-SCL-' || lpad((cl.id - 868000)::text, 5, '0'),
    'Deterministic scale ledger settlement',
    800006,
    cl.posted_at + interval '1 day'
FROM public.customer_ledgers cl
WHERE cl.id BETWEEN 868001 AND 868400
ORDER BY cl.id;

-- 1,000 housekeeping tasks (`870001..871000`).
INSERT INTO public.housekeeping_tasks (
    id, room_id, task_type, priority, status, assigned_to,
    scheduled_date, task_date, started_at, completed_at,
    notes, created_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE
SELECT
    870000 + n,
    800301 + ((n - 1) % 24),
    CASE n % 3 WHEN 0 THEN 'cleaning' WHEN 1 THEN 'inspection' ELSE 'turndown' END,
    CASE n % 4 WHEN 0 THEN 'high' WHEN 1 THEN 'normal' WHEN 2 THEN 'low' ELSE 'urgent' END,
    'completed',
    CASE n % 2 WHEN 0 THEN 800004 ELSE 800005 END,
    (SELECT today FROM staging_ref) - ((n % 90) + 1),
    (SELECT today FROM staging_ref) - ((n % 90) + 1),
    ((SELECT today FROM staging_ref) - ((n % 90) + 1))::timestamptz + interval '10 hours',
    ((SELECT today FROM staging_ref) - ((n % 90) + 1))::timestamptz + interval '11 hours',
    'Scale housekeeping task #' || n::text,
    800005,
    ((SELECT today FROM staging_ref) - ((n % 90) + 1))::timestamptz + interval '9 hours',
    ((SELECT today FROM staging_ref) - ((n % 90) + 1))::timestamptz + interval '11 hours'
FROM generate_series(1, 1000) AS n;

-- 2,000 id-guarded audit log entries (`875001..877000`).
SELECT public.ensure_audit_logs_partition(date_trunc('month', (SELECT today FROM staging_ref))::date);
SELECT public.ensure_audit_logs_partition(date_trunc('month', (SELECT today FROM staging_ref) - interval '1 month')::date);
SELECT public.ensure_audit_logs_truncate_guards();

INSERT INTO public.audit_logs (
    id, user_id, action, resource_type, resource_id,
    details, ip_address, user_agent, created_at
)
OVERRIDING SYSTEM VALUE
SELECT v.*
FROM (
    SELECT
        875000 + n AS id,
        (SELECT id FROM public.users WHERE username = 'admin') AS user_id,
        (ARRAY['booking.update','payment.create','invoice.issue','room.status_change'])[n % 4 + 1] AS action,
        (ARRAY['booking','payment','invoice','room'])[n % 4 + 1] AS resource_type,
        CASE n % 4
            WHEN 0 THEN 855001 + (n % 2500)
            WHEN 1 THEN 860001 + (n % 2500)
            WHEN 2 THEN 865001 + (n % 500)
            ELSE 800301 + (n % 24)
        END AS resource_id,
        jsonb_build_object('staging_seed', 'scale', 'seq', n) AS details,
        '203.0.113.50'::inet AS ip_address,
        'scale-seed/v1' AS user_agent,
        ((SELECT today FROM staging_ref) - (n % 28))::timestamptz + ((n % 20) * interval '1 hour') AS created_at
    FROM generate_series(1, 2000) AS n
) AS v
WHERE NOT EXISTS (SELECT 1 FROM public.audit_logs a WHERE a.id = v.id);
"#;
