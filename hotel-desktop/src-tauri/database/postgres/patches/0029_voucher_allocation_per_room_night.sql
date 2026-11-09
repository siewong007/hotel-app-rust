-- One voucher allocation row per room-night, not per night.
--
-- A multi-room booking redeems one voucher across all its rooms, and every
-- room has the same stay dates, so the old UNIQUE (redemption_id, stay_date)
-- refused the second room's first night: any multi-room checkout with a
-- voucher failed with a duplicate-key error (HTTP 500). The key now includes
-- the booking. Single-room bookings are unaffected (one booking per
-- redemption). Idempotent: drops the old key if present, adds the new one if
-- missing.
ALTER TABLE public.voucher_redemption_allocations
    DROP CONSTRAINT IF EXISTS voucher_redemption_allocations_redemption_id_stay_date_key;

DO $allocation_key$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.voucher_redemption_allocations'::regclass
          AND conname = 'voucher_redemption_allocations_redemption_booking_night_key'
    ) THEN
        ALTER TABLE public.voucher_redemption_allocations
            ADD CONSTRAINT voucher_redemption_allocations_redemption_booking_night_key
            UNIQUE (redemption_id, booking_id, stay_date);
    END IF;
END
$allocation_key$;
