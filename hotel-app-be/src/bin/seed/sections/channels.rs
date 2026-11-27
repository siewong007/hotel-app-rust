//! Channel management & dynamic rate rules (`channels` default section).
//! Adds two custom booking channels alongside the bootstrap catalog plus
//! channel pricing and commission rules so rate-matrix, preview, and
//! commission calculations can all be tested out of the box.

use crate::engine::Tx;

pub async fn seed(tx: &mut Tx<'_>) -> Result<(), sqlx::Error> {
    sqlx::raw_sql(SQL).execute(&mut **tx).await?;
    Ok(())
}

const SQL: &str = r#"
INSERT INTO public.booking_channels (
    id, name, channel_type, default_commission_type, default_commission_value,
    default_commission_scope, is_active, abbreviation, code, integration_mode,
    settlement_payment_methods, created_at, updated_at
)
OVERRIDING SYSTEM VALUE VALUES
    (800701, 'Corporate Portal', 'corporate', 'percentage', 5.00,
     'per_booking', true, 'CP', 'stg_corp_portal', 'manual', NULL,
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours')),
    (800702, 'Luxury Escapes OTA', 'ota', 'percentage', 15.00,
     'per_booking', true, 'LE', 'stg_lux_ota', 'api', ARRAY['online_payment', 'bank_transfer'],
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'));

INSERT INTO public.channel_pricing_rules (
    id, channel_id, room_type_id, rate_plan_id, rule_type, value,
    effective_from, effective_to, priority, is_active, reason,
    created_by, updated_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE VALUES
    (800711, (SELECT id FROM public.booking_channels WHERE name = 'Booking.com'),
     NULL, 800501, 'markup_percent', 12.00,
     (SELECT today - 30 FROM staging_ref), NULL, 10, true, 'Standard OTA rack markup',
     800001, 800001,
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours')),
    (800712, (SELECT id FROM public.booking_channels WHERE name = 'Direct Website'),
     NULL, 800501, 'discount_percent', 5.00,
     (SELECT today - 30 FROM staging_ref), NULL, 10, true, 'Book-direct website incentive',
     800007, 800007,
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours')),
    (800713, 800701,
     800201, 800502, 'fixed_price', 82.00,
     (SELECT today - 15 FROM staging_ref), (SELECT today + 180 FROM staging_ref), 20, true, 'Contracted ECO corporate floor rate',
     800001, 800001,
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours')),
    (800714, 800702,
     800202, NULL, 'net_rate', 460.00,
     (SELECT today - 10 FROM staging_ref), (SELECT today + 90 FROM staging_ref), 15, true, 'Seasonal Premier Suite net contract',
     800007, 800007,
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'));

INSERT INTO public.channel_commission_rules (
    id, channel_id, commission_type, value, scope,
    effective_from, effective_to, priority, is_active, reason,
    created_by, updated_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE VALUES
    (800721, (SELECT id FROM public.booking_channels WHERE name = 'Booking.com'),
     'percentage', 16.50, 'per_booking',
     (SELECT today - 30 FROM staging_ref), (SELECT today + 90 FROM staging_ref), 10, true, 'Preferred partner visibility tier',
     800001, 800001,
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours')),
    (800722, (SELECT id FROM public.booking_channels WHERE name = 'Agoda'),
     'fixed_amount', 25.00, 'per_night',
     (SELECT today - 15 FROM staging_ref), (SELECT today + 60 FROM staging_ref), 10, true, 'Flat per-night promotional fee',
     800001, 800001,
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours')),
    (800723, 800702,
     'percentage', 14.00, 'per_booking',
     (SELECT today FROM staging_ref), NULL, 5, true, 'Introductory commission schedule',
     800001, 800001,
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'),
     ((SELECT today FROM staging_ref)::timestamptz + interval '12 hours'));
"#;
