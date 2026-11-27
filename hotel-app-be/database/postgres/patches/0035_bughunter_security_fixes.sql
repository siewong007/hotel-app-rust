-- Security remediation hardening (bug-hunter audit):
-- 1. Single-use OAuth ID token replay table (`consumed_oauth_tokens`).
-- 2. Non-negative CHECK constraints on `public.bookings` monetary and extra-bed fields.
--
-- Converges both pre-0035 (patch 0034 head) and updated V1 baseline cleanly.

CREATE TABLE IF NOT EXISTS public.consumed_oauth_tokens (
    token_hash character varying(128) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT consumed_oauth_tokens_pkey PRIMARY KEY (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_consumed_oauth_tokens_expires_at
    ON public.consumed_oauth_tokens USING btree (expires_at);

DO $checks_0035$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.bookings'::regclass
          AND conname = 'bookings_extra_bed_charge_non_negative'
    ) THEN
        ALTER TABLE public.bookings
            ADD CONSTRAINT bookings_extra_bed_charge_non_negative
            CHECK (((extra_bed_charge IS NULL) OR (extra_bed_charge >= (0)::numeric)));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.bookings'::regclass
          AND conname = 'bookings_extra_bed_count_non_negative'
    ) THEN
        ALTER TABLE public.bookings
            ADD CONSTRAINT bookings_extra_bed_count_non_negative
            CHECK (((extra_bed_count IS NULL) OR (extra_bed_count >= 0)));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.bookings'::regclass
          AND conname = 'bookings_room_rate_non_negative'
    ) THEN
        ALTER TABLE public.bookings
            ADD CONSTRAINT bookings_room_rate_non_negative
            CHECK ((room_rate >= (0)::numeric));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.bookings'::regclass
          AND conname = 'bookings_subtotal_non_negative'
    ) THEN
        ALTER TABLE public.bookings
            ADD CONSTRAINT bookings_subtotal_non_negative
            CHECK ((subtotal >= (0)::numeric));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.bookings'::regclass
          AND conname = 'bookings_total_amount_non_negative'
    ) THEN
        ALTER TABLE public.bookings
            ADD CONSTRAINT bookings_total_amount_non_negative
            CHECK ((total_amount >= (0)::numeric));
    END IF;
END;
$checks_0035$;
