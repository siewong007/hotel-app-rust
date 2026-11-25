-- Full-audit schema, routine, view, constraint, and seed fixes.
-- Converges both the pre-0032 baseline and the updated V1 baseline cleanly
-- with exact definition guards on every altered object.

-- 1. bookings.status: add 'late_checkout' to bookings_status_check
DO $booking_status$
DECLARE
    found_definition text;
    current_definition constant text := $bs_current$CHECK (((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('pending_payment'::character varying)::text, ('pending_confirmation'::character varying)::text, ('confirmed'::character varying)::text, ('checked_in'::character varying)::text, ('auto_checked_in'::character varying)::text, ('late_checkout'::character varying)::text, ('checked_out'::character varying)::text, ('no_show'::character varying)::text, ('completed'::character varying)::text, ('comp_void'::character varying)::text, ('partial_complimentary'::character varying)::text, ('fully_complimentary'::character varying)::text, ('voided'::character varying)::text])))$bs_current$;
    old_definition constant text := $bs_old$CHECK (((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('pending_payment'::character varying)::text, ('pending_confirmation'::character varying)::text, ('confirmed'::character varying)::text, ('checked_in'::character varying)::text, ('auto_checked_in'::character varying)::text, ('checked_out'::character varying)::text, ('no_show'::character varying)::text, ('completed'::character varying)::text, ('comp_void'::character varying)::text, ('partial_complimentary'::character varying)::text, ('fully_complimentary'::character varying)::text, ('voided'::character varying)::text])))$bs_old$;
BEGIN
    SELECT pg_get_constraintdef(constraint_row.oid, false)
    INTO found_definition
    FROM pg_constraint AS constraint_row
    JOIN pg_class AS table_row ON table_row.oid = constraint_row.conrelid
    JOIN pg_namespace AS schema_row ON schema_row.oid = table_row.relnamespace
    WHERE schema_row.nspname = 'public'
      AND table_row.relname = 'bookings'
      AND constraint_row.conname = 'bookings_status_check';

    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'bookings_status_check has incompatible definition: <missing>';
    ELSIF found_definition = old_definition THEN
        EXECUTE 'ALTER TABLE public.bookings DROP CONSTRAINT bookings_status_check';
        EXECUTE 'ALTER TABLE public.bookings ADD CONSTRAINT bookings_status_check ' || current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'bookings_status_check has incompatible definition: %', found_definition;
    END IF;
END;
$booking_status$;

-- 2. bookings_no_room_date_overlap: include late_checkout and complimentary statuses
DO $booking_overlap$
DECLARE
    found_definition text;
    current_definition constant text := $ov_current$EXCLUDE USING gist (room_id WITH =, daterange(check_in_date, check_out_date, '[)'::text) WITH &&) WHERE (((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('pending_payment'::character varying)::text, ('pending_confirmation'::character varying)::text, ('confirmed'::character varying)::text, ('checked_in'::character varying)::text, ('auto_checked_in'::character varying)::text, ('late_checkout'::character varying)::text, ('partial_complimentary'::character varying)::text, ('fully_complimentary'::character varying)::text])))$ov_current$;
    old_definition constant text := $ov_old$EXCLUDE USING gist (room_id WITH =, daterange(check_in_date, check_out_date, '[)'::text) WITH &&) WHERE (((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('pending_payment'::character varying)::text, ('pending_confirmation'::character varying)::text, ('confirmed'::character varying)::text, ('checked_in'::character varying)::text, ('auto_checked_in'::character varying)::text])))$ov_old$;
BEGIN
    SELECT pg_get_constraintdef(constraint_row.oid, false)
    INTO found_definition
    FROM pg_constraint AS constraint_row
    JOIN pg_class AS table_row ON table_row.oid = constraint_row.conrelid
    JOIN pg_namespace AS schema_row ON schema_row.oid = table_row.relnamespace
    WHERE schema_row.nspname = 'public'
      AND table_row.relname = 'bookings'
      AND constraint_row.conname = 'bookings_no_room_date_overlap';

    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'bookings_no_room_date_overlap has incompatible definition: <missing>';
    ELSIF found_definition = old_definition THEN
        EXECUTE 'ALTER TABLE public.bookings DROP CONSTRAINT bookings_no_room_date_overlap';
        EXECUTE 'ALTER TABLE public.bookings ADD CONSTRAINT bookings_no_room_date_overlap ' || current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'bookings_no_room_date_overlap has incompatible definition: %', found_definition;
    END IF;
END;
$booking_overlap$;

-- 3. customer_ledgers_guest_id_fkey: reference public.guests(id) ON DELETE SET NULL
DO $ledger_guest_fk$
DECLARE
    found_definition text;
    current_definition constant text := 'FOREIGN KEY (guest_id) REFERENCES guests(id) ON DELETE SET NULL';
    old_definition constant text := 'FOREIGN KEY (guest_id) REFERENCES users(id) ON DELETE SET NULL';
BEGIN
    SELECT pg_get_constraintdef(constraint_row.oid, false)
    INTO found_definition
    FROM pg_constraint AS constraint_row
    JOIN pg_class AS table_row ON table_row.oid = constraint_row.conrelid
    JOIN pg_namespace AS schema_row ON schema_row.oid = table_row.relnamespace
    WHERE schema_row.nspname = 'public'
      AND table_row.relname = 'customer_ledgers'
      AND constraint_row.conname = 'customer_ledgers_guest_id_fkey';

    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'customer_ledgers_guest_id_fkey has incompatible definition: <missing>';
    ELSIF found_definition = old_definition THEN
        UPDATE public.customer_ledgers cl
           SET guest_id = NULL
         WHERE cl.guest_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM public.guests g WHERE g.id = cl.guest_id);

        EXECUTE 'ALTER TABLE public.customer_ledgers DROP CONSTRAINT customer_ledgers_guest_id_fkey';
        EXECUTE 'ALTER TABLE public.customer_ledgers ADD CONSTRAINT customer_ledgers_guest_id_fkey FOREIGN KEY (guest_id) REFERENCES public.guests(id) ON DELETE SET NULL';
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'customer_ledgers_guest_id_fkey has incompatible definition: %', found_definition;
    END IF;
END;
$ledger_guest_fk$;

-- 4. uq_customer_ledgers_booking_room_charge: exclude voided ledger rows
DO $room_charge_idx$
DECLARE
    found_definition text;
    current_definition constant text := $idx_current$CREATE UNIQUE INDEX uq_customer_ledgers_booking_room_charge ON public.customer_ledgers USING btree (booking_id) WHERE (((post_type)::text = 'room_charge'::text) AND (COALESCE(is_reversal, false) = false) AND (booking_id IS NOT NULL) AND (void_at IS NULL) AND ((status)::text <> 'void'::text))$idx_current$;
    old_definition constant text := $idx_old$CREATE UNIQUE INDEX uq_customer_ledgers_booking_room_charge ON public.customer_ledgers USING btree (booking_id) WHERE (((post_type)::text = 'room_charge'::text) AND (COALESCE(is_reversal, false) = false) AND (booking_id IS NOT NULL))$idx_old$;
BEGIN
    SELECT pg_get_indexdef(index_row.oid)
    INTO found_definition
    FROM pg_class AS index_row
    JOIN pg_namespace AS schema_row ON schema_row.oid = index_row.relnamespace
    WHERE schema_row.nspname = 'public'
      AND index_row.relname = 'uq_customer_ledgers_booking_room_charge';

    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'uq_customer_ledgers_booking_room_charge has incompatible definition: <missing>';
    ELSIF found_definition = old_definition THEN
        EXECUTE 'DROP INDEX public.uq_customer_ledgers_booking_room_charge';
        EXECUTE current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'uq_customer_ledgers_booking_room_charge has incompatible definition: %', found_definition;
    END IF;
END;
$room_charge_idx$;

-- 5. uq_customer_ledgers_active_reversal: prevent duplicate active reversals per original ledger row
DO $active_reversal_idx$
DECLARE
    found_definition text;
    current_definition constant text := $idx_rev$CREATE UNIQUE INDEX uq_customer_ledgers_active_reversal ON public.customer_ledgers USING btree (original_transaction_id) WHERE ((COALESCE(is_reversal, false) = true) AND (original_transaction_id IS NOT NULL) AND (void_at IS NULL) AND ((status)::text <> 'void'::text))$idx_rev$;
BEGIN
    SELECT pg_get_indexdef(index_row.oid)
    INTO found_definition
    FROM pg_class AS index_row
    JOIN pg_namespace AS schema_row ON schema_row.oid = index_row.relnamespace
    WHERE schema_row.nspname = 'public'
      AND index_row.relname = 'uq_customer_ledgers_active_reversal';

    IF found_definition IS NULL THEN
        EXECUTE current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'uq_customer_ledgers_active_reversal has incompatible definition: %', found_definition;
    END IF;
END;
$active_reversal_idx$;

-- 6. loyalty_rewards_inventory_non_negative: forbid negative inventory counts
DO $loyalty_inventory$
DECLARE
    found_definition text;
    current_definition constant text := 'CHECK (((inventory_count IS NULL) OR (inventory_count >= 0)))';
BEGIN
    SELECT pg_get_constraintdef(constraint_row.oid, false)
    INTO found_definition
    FROM pg_constraint AS constraint_row
    JOIN pg_class AS table_row ON table_row.oid = constraint_row.conrelid
    JOIN pg_namespace AS schema_row ON schema_row.oid = table_row.relnamespace
    WHERE schema_row.nspname = 'public'
      AND table_row.relname = 'loyalty_rewards'
      AND constraint_row.conname = 'loyalty_rewards_inventory_non_negative';

    IF found_definition IS NULL THEN
        EXECUTE 'ALTER TABLE public.loyalty_rewards ADD CONSTRAINT loyalty_rewards_inventory_non_negative ' || current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'loyalty_rewards_inventory_non_negative has incompatible definition: %', found_definition;
    END IF;
END;
$loyalty_inventory$;

-- 7. Drop redundant duplicate FK fk_room_changes_guest (keep room_changes_guest_id_fkey)
DO $dup_room_changes_fk$
DECLARE
    found_definition text;
    expected_definition constant text := 'FOREIGN KEY (guest_id) REFERENCES guests(id) ON DELETE SET NULL';
BEGIN
    SELECT pg_get_constraintdef(constraint_row.oid, false)
    INTO found_definition
    FROM pg_constraint AS constraint_row
    JOIN pg_class AS table_row ON table_row.oid = constraint_row.conrelid
    JOIN pg_namespace AS schema_row ON schema_row.oid = table_row.relnamespace
    WHERE schema_row.nspname = 'public'
      AND table_row.relname = 'room_changes'
      AND constraint_row.conname = 'fk_room_changes_guest';

    IF found_definition IS NOT NULL THEN
        IF found_definition <> expected_definition THEN
            RAISE EXCEPTION 'fk_room_changes_guest has incompatible definition: %', found_definition;
        END IF;
        EXECUTE 'ALTER TABLE public.room_changes DROP CONSTRAINT fk_room_changes_guest';
    END IF;
END;
$dup_room_changes_fk$;

-- 8. generate_folio_number() & generate_invoice_number()
DO $folio_and_invoice_generators$
DECLARE
    found_definition text;
    folio_current constant text := $folio_cur$CREATE OR REPLACE FUNCTION public.generate_folio_number()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_id_text TEXT;
BEGIN
    IF NEW.folio_number IS NULL THEN
        v_id_text := NEW.id::TEXT;
        NEW.folio_number := CASE NEW.folio_type
            WHEN 'guest_folio' THEN 'GF-'
            WHEN 'master_folio' THEN 'MF-'
            WHEN 'city_ledger' THEN 'CL-'
            WHEN 'group_folio' THEN 'GP-'
            WHEN 'ar_ledger' THEN 'AR-'
            ELSE 'TX-'
        END || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD(v_id_text, GREATEST(LENGTH(v_id_text), 6), '0');
    END IF;
    IF NEW.net_amount IS NULL THEN
        NEW.net_amount := NEW.amount - COALESCE(NEW.tax_amount, 0) - COALESCE(NEW.service_charge, 0);
    END IF;
    IF NEW.is_posted = TRUE AND NEW.posted_at IS NULL THEN
        NEW.posted_at := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$function$
$folio_cur$;
    folio_old constant text := $folio_old$CREATE OR REPLACE FUNCTION public.generate_folio_number()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.folio_number IS NULL THEN
        NEW.folio_number := CASE NEW.folio_type
            WHEN 'guest_folio' THEN 'GF-'
            WHEN 'master_folio' THEN 'MF-'
            WHEN 'city_ledger' THEN 'CL-'
            WHEN 'group_folio' THEN 'GP-'
            WHEN 'ar_ledger' THEN 'AR-'
            ELSE 'TX-'
        END || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD(NEW.id::TEXT, 6, '0');
    END IF;
    IF NEW.net_amount IS NULL THEN
        NEW.net_amount := NEW.amount - COALESCE(NEW.tax_amount, 0) - COALESCE(NEW.service_charge, 0);
    END IF;
    IF NEW.is_posted = TRUE AND NEW.posted_at IS NULL THEN
        NEW.posted_at := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$function$
$folio_old$;
    inv_current constant text := $inv_cur$CREATE OR REPLACE FUNCTION public.generate_invoice_number()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_prefix TEXT;
    v_next_seq BIGINT;
    v_seq_text TEXT;
BEGIN
    IF NEW.invoice_number IS NULL THEN
        v_prefix := 'INV-' || TO_CHAR(CURRENT_DATE, 'YYYYMM') || '-';

        PERFORM pg_advisory_xact_lock(
            hashtextextended('invoice_number:' || TO_CHAR(CURRENT_DATE, 'YYYYMM'), 0)
        );

        SELECT COALESCE(MAX(CAST(SUBSTRING(invoice_number FROM 12) AS BIGINT)), 0)
          INTO v_next_seq
          FROM (
              SELECT invoice_number FROM invoices
               WHERE invoice_number LIKE v_prefix || '%'
                 AND SUBSTRING(invoice_number FROM 12) ~ '^[0-9]+$'
              UNION ALL
              SELECT invoice_number FROM customer_ledgers
               WHERE invoice_number LIKE v_prefix || '%'
                 AND SUBSTRING(invoice_number FROM 12) ~ '^[0-9]+$'
          ) combined;

        v_seq_text := (v_next_seq + 1)::TEXT;
        NEW.invoice_number := v_prefix || LPAD(v_seq_text, GREATEST(LENGTH(v_seq_text), 4), '0');
    END IF;
    RETURN NEW;
END;
$function$
$inv_cur$;
    inv_old constant text := $inv_old$CREATE OR REPLACE FUNCTION public.generate_invoice_number()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_prefix TEXT;
    v_next_seq INTEGER;
BEGIN
    IF NEW.invoice_number IS NULL THEN
        v_prefix := 'INV-' || TO_CHAR(CURRENT_DATE, 'YYYYMM') || '-';

        SELECT COALESCE(MAX(CAST(SUBSTRING(invoice_number FROM 12) AS INTEGER)), 0)
          INTO v_next_seq
          FROM (
              SELECT invoice_number FROM invoices
               WHERE invoice_number LIKE v_prefix || '%'
              UNION ALL
              SELECT invoice_number FROM customer_ledgers
               WHERE invoice_number LIKE v_prefix || '%'
          ) combined;

        NEW.invoice_number := v_prefix || LPAD((v_next_seq + 1)::TEXT, 4, '0');
    END IF;
    RETURN NEW;
END;
$function$
$inv_old$;
BEGIN
    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.generate_folio_number()');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'generate_folio_number() has incompatible definition: <missing>';
    ELSIF found_definition = folio_old THEN
        EXECUTE folio_current;
    ELSIF found_definition <> folio_current THEN
        RAISE EXCEPTION 'generate_folio_number() has incompatible definition: %', found_definition;
    END IF;

    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.generate_invoice_number()');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'generate_invoice_number() has incompatible definition: <missing>';
    ELSIF found_definition = inv_old THEN
        EXECUTE inv_current;
    ELSIF found_definition <> inv_current THEN
        RAISE EXCEPTION 'generate_invoice_number() has incompatible definition: %', found_definition;
    END IF;
END;
$folio_and_invoice_generators$;

-- 9. get_unposted_bookings(date) & run_night_audit(date, bigint)
DO $night_audit_routines$
DECLARE
    found_definition text;
    unposted_current constant text := $up_cur$CREATE OR REPLACE FUNCTION public.get_unposted_bookings(p_audit_date date)
 RETURNS TABLE(booking_id bigint, booking_number character varying, guest_name text, room_number character varying, check_in_date date, check_out_date date, status character varying, total_amount numeric)
 LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN QUERY
    SELECT
        b.id as booking_id,
        b.booking_number,
        CASE
            WHEN NULLIF(BTRIM(g.first_name), '') IS NOT NULL
                 AND NULLIF(BTRIM(g.last_name), '') IS NOT NULL
            THEN BTRIM(g.first_name) || ' ' || BTRIM(g.last_name)
            ELSE COALESCE(BTRIM(g.nick_name), '')
        END as guest_name,
        r.room_number,
        b.check_in_date,
        b.check_out_date,
        b.status,
        b.total_amount
    FROM bookings b
    JOIN guests g ON b.guest_id = g.id
    JOIN rooms r ON b.room_id = r.id
    WHERE b.is_posted = FALSE
    AND (
        (b.check_in_date <= p_audit_date AND b.check_out_date > p_audit_date)
        OR (b.check_out_date = p_audit_date AND b.status IN ('checked_out', 'completed'))
        OR (DATE(b.created_at) = p_audit_date OR DATE(b.updated_at) = p_audit_date)
    )
    AND b.status IN ('checked_in', 'auto_checked_in', 'late_checkout', 'checked_out', 'completed')
    ORDER BY b.check_in_date;
END;
$function$
$up_cur$;
    unposted_old constant text := $up_old$CREATE OR REPLACE FUNCTION public.get_unposted_bookings(p_audit_date date)
 RETURNS TABLE(booking_id bigint, booking_number character varying, guest_name text, room_number character varying, check_in_date date, check_out_date date, status character varying, total_amount numeric)
 LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN QUERY
    SELECT
        b.id as booking_id,
        b.booking_number,
        g.first_name || ' ' || g.last_name as guest_name,
        r.room_number,
        b.check_in_date,
        b.check_out_date,
        b.status,
        b.total_amount
    FROM bookings b
    JOIN guests g ON b.guest_id = g.id
    JOIN rooms r ON b.room_id = r.id
    WHERE b.is_posted = FALSE
    AND (
        (b.check_in_date <= p_audit_date AND b.check_out_date > p_audit_date)
        OR (b.check_out_date = p_audit_date AND b.status = 'checked_out')
        OR (DATE(b.created_at) = p_audit_date OR DATE(b.updated_at) = p_audit_date)
    )
    AND b.status NOT IN ('voided', 'no_show', 'confirmed', 'pending')
    ORDER BY b.check_in_date;
END;
$function$
$up_old$;
    rna_current constant text := $rna_cur$CREATE OR REPLACE FUNCTION public.run_night_audit(p_audit_date date, p_user_id bigint)
 RETURNS bigint
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_audit_run_id BIGINT;
    v_bookings_posted INTEGER := 0;
    v_checkins INTEGER := 0;
    v_checkouts INTEGER := 0;
    v_revenue DECIMAL(12, 2) := 0;
    v_rooms_occupied INTEGER := 0;
    v_rooms_available INTEGER := 0;
    v_rooms_reserved INTEGER := 0;
    v_rooms_maintenance INTEGER := 0;
    v_rooms_dirty INTEGER := 0;
    v_total_rooms INTEGER := 0;
    v_occupancy_rate DECIMAL(5, 2) := 0;
    v_booking RECORD;
    v_tax_rate DECIMAL(5, 4) := 0.08;
    v_night_rate DECIMAL(10, 2);
    v_allocated_net DECIMAL(10, 2);
    v_room_charge DECIMAL(10, 2);
    v_service_tax DECIMAL(10, 2);
    v_tourism_tax_per_night DECIMAL(10, 2);
    v_nights INTEGER;
    v_billable_nights INTEGER;
    v_night_discount DECIMAL(10, 2);
    v_base_extra_bed DECIMAL(10, 2);
    v_extra_bed_gross_per_night DECIMAL(10, 2);
    v_extra_bed_charge_per_night DECIMAL(10, 2);
    v_extra_bed_tax DECIMAL(10, 2);
    v_night_total DECIMAL(10, 2);
BEGIN
    IF EXISTS (SELECT 1 FROM night_audit_runs WHERE audit_date = p_audit_date AND status = 'completed') THEN
        RAISE EXCEPTION 'Night audit already completed for date %', p_audit_date;
    END IF;

    BEGIN
        SELECT CAST(value AS DECIMAL) / 100.0 INTO v_tax_rate
        FROM system_settings WHERE key = 'service_tax_rate';
    EXCEPTION WHEN OTHERS THEN
        v_tax_rate := 0.08;
    END;
    v_tax_rate := COALESCE(v_tax_rate, 0.08);

    INSERT INTO night_audit_runs (audit_date, run_by, status)
    VALUES (p_audit_date, p_user_id, 'in_progress')
    RETURNING id INTO v_audit_run_id;

    FOR v_booking IN
        SELECT b.id, b.booking_number, b.status, b.room_rate, b.total_amount,
               COALESCE(b.discount_amount, 0) as discount_amount,
               b.check_in_date, b.check_out_date, b.guest_id, b.room_id,
               b.daily_rates,
               COALESCE(b.is_complimentary, false) as is_complimentary,
               b.complimentary_start_date,
               b.complimentary_end_date,
               COALESCE(b.complimentary_nights, 0) as complimentary_nights,
               COALESCE(b.is_tourist, false) as is_tourist,
               COALESCE(b.tourism_tax_amount, 0) as tourism_tax_amount,
               COALESCE(b.extra_bed_charge, 0) as extra_bed_charge
        FROM bookings b
        WHERE b.status IN ('checked_in', 'auto_checked_in', 'late_checkout', 'checked_out', 'completed')
        AND b.check_in_date <= p_audit_date
        AND b.check_out_date > p_audit_date
        AND NOT EXISTS (
            SELECT 1 FROM night_audit_posted_nights napn
            WHERE napn.booking_id = b.id AND napn.audit_date = p_audit_date
        )
    LOOP
        v_nights := GREATEST((v_booking.check_out_date - v_booking.check_in_date), 1);

        IF v_booking.is_complimentary
           AND ((v_booking.complimentary_start_date IS NOT NULL
                 AND v_booking.complimentary_end_date IS NOT NULL
                 AND p_audit_date >= v_booking.complimentary_start_date
                 AND p_audit_date < v_booking.complimentary_end_date)
                OR v_booking.total_amount <= 0) THEN
            v_night_rate := 0;
        ELSE
            SELECT vra.net_amount
              INTO v_allocated_net
              FROM voucher_redemption_allocations vra
              JOIN voucher_redemptions vr ON vr.id = vra.redemption_id
             WHERE vra.booking_id = v_booking.id
               AND vra.stay_date = p_audit_date
               AND vr.status = 'applied'
             LIMIT 1;

            IF FOUND THEN
                v_night_rate := v_allocated_net;
            ELSE
                IF v_booking.daily_rates IS NOT NULL
                   AND v_booking.daily_rates ? p_audit_date::TEXT THEN
                    v_night_rate := (v_booking.daily_rates ->> p_audit_date::TEXT)::DECIMAL;
                ELSE
                    v_night_rate := v_booking.room_rate;
                END IF;

                IF v_booking.discount_amount > 0 THEN
                    v_billable_nights := GREATEST(v_nights - LEAST(v_booking.complimentary_nights, v_nights), 1);
                    v_night_discount := ROUND(v_booking.discount_amount / v_billable_nights, 2);
                    v_night_rate := GREATEST(v_night_rate - v_night_discount, 0);
                END IF;
            END IF;
        END IF;

        v_room_charge := ROUND(v_night_rate / (1 + v_tax_rate), 2);
        v_service_tax := v_night_rate - v_room_charge;

        v_tourism_tax_per_night := 0;
        IF v_booking.is_tourist AND v_booking.tourism_tax_amount > 0 THEN
            v_tourism_tax_per_night := ROUND(v_booking.tourism_tax_amount / v_nights, 2);
        END IF;

        v_extra_bed_gross_per_night := 0;
        v_extra_bed_charge_per_night := 0;
        v_extra_bed_tax := 0;
        IF v_booking.extra_bed_charge > 0 THEN
            v_base_extra_bed := ROUND(v_booking.extra_bed_charge / v_nights, 2);
            IF p_audit_date = v_booking.check_out_date - 1 THEN
                v_extra_bed_gross_per_night := GREATEST(v_booking.extra_bed_charge - v_base_extra_bed * (v_nights - 1), 0);
            ELSE
                v_extra_bed_gross_per_night := v_base_extra_bed;
            END IF;
            v_extra_bed_charge_per_night := ROUND(v_extra_bed_gross_per_night / (1 + v_tax_rate), 2);
            v_extra_bed_tax := v_extra_bed_gross_per_night - v_extra_bed_charge_per_night;
        END IF;

        v_night_total := v_night_rate + v_extra_bed_gross_per_night + v_tourism_tax_per_night;

        INSERT INTO night_audit_posted_nights
            (booking_id, audit_date, room_rate, room_charge, service_tax, tourism_tax,
             extra_bed_charge, extra_bed_tax, total_posted, audit_run_id, posted_by)
        VALUES
            (v_booking.id, p_audit_date, v_night_rate, v_room_charge, v_service_tax,
             v_tourism_tax_per_night, v_extra_bed_charge_per_night, v_extra_bed_tax,
             v_night_total, v_audit_run_id, p_user_id);

        INSERT INTO night_audit_details (audit_run_id, booking_id, record_type, action, data)
        VALUES (v_audit_run_id, v_booking.id, 'booking', 'night_posted',
            jsonb_build_object(
                'status', v_booking.status,
                'room_rate', v_booking.room_rate,
                'night_rate', v_night_rate,
                'has_daily_rates', (v_booking.daily_rates IS NOT NULL),
                'night_date', p_audit_date,
                'room_charge', v_room_charge,
                'service_tax', v_service_tax,
                'tourism_tax', v_tourism_tax_per_night,
                'extra_bed_charge', v_extra_bed_charge_per_night,
                'extra_bed_tax', v_extra_bed_tax,
                'check_in_date', v_booking.check_in_date,
                'check_out_date', v_booking.check_out_date
            )
        );

        v_bookings_posted := v_bookings_posted + 1;
        v_revenue := v_revenue + v_night_total;
    END LOOP;

    -- Same-day checkout (hourly stays)
    FOR v_booking IN
        SELECT b.id, b.booking_number, b.status, b.room_rate, b.total_amount,
               COALESCE(b.discount_amount, 0) as discount_amount,
               b.check_in_date, b.check_out_date, b.guest_id, b.room_id,
               b.daily_rates,
               COALESCE(b.is_complimentary, false) as is_complimentary,
               COALESCE(b.is_tourist, false) as is_tourist,
               COALESCE(b.tourism_tax_amount, 0) as tourism_tax_amount,
               COALESCE(b.extra_bed_charge, 0) as extra_bed_charge
        FROM bookings b
        WHERE b.status IN ('checked_out', 'completed')
        AND b.check_in_date = p_audit_date
        AND b.check_out_date = p_audit_date
        AND NOT EXISTS (
            SELECT 1 FROM night_audit_posted_nights napn
            WHERE napn.booking_id = b.id AND napn.audit_date = p_audit_date
        )
    LOOP
        IF v_booking.is_complimentary AND v_booking.total_amount <= 0 THEN
            v_night_rate := 0;
        ELSE
            SELECT vra.net_amount
              INTO v_allocated_net
              FROM voucher_redemption_allocations vra
              JOIN voucher_redemptions vr ON vr.id = vra.redemption_id
             WHERE vra.booking_id = v_booking.id
               AND vra.stay_date = p_audit_date
               AND vr.status = 'applied'
             LIMIT 1;

            IF FOUND THEN
                v_night_rate := v_allocated_net;
            ELSE
                IF v_booking.daily_rates IS NOT NULL
                   AND v_booking.daily_rates ? p_audit_date::TEXT THEN
                    v_night_rate := (v_booking.daily_rates ->> p_audit_date::TEXT)::DECIMAL;
                ELSE
                    v_night_rate := v_booking.room_rate;
                END IF;

                IF v_booking.discount_amount > 0 THEN
                    v_night_rate := GREATEST(v_night_rate - v_booking.discount_amount, 0);
                END IF;
            END IF;
        END IF;

        v_room_charge := ROUND(v_night_rate / (1 + v_tax_rate), 2);
        v_service_tax := v_night_rate - v_room_charge;

        v_tourism_tax_per_night := 0;
        IF v_booking.is_tourist AND v_booking.tourism_tax_amount > 0 THEN
            v_tourism_tax_per_night := v_booking.tourism_tax_amount;
        END IF;

        v_extra_bed_gross_per_night := 0;
        v_extra_bed_charge_per_night := 0;
        v_extra_bed_tax := 0;
        IF v_booking.extra_bed_charge > 0 THEN
            v_extra_bed_gross_per_night := v_booking.extra_bed_charge;
            v_extra_bed_charge_per_night := ROUND(v_extra_bed_gross_per_night / (1 + v_tax_rate), 2);
            v_extra_bed_tax := v_extra_bed_gross_per_night - v_extra_bed_charge_per_night;
        END IF;

        v_night_total := v_night_rate + v_extra_bed_gross_per_night + v_tourism_tax_per_night;

        INSERT INTO night_audit_posted_nights
            (booking_id, audit_date, room_rate, room_charge, service_tax, tourism_tax,
             extra_bed_charge, extra_bed_tax, total_posted, audit_run_id, posted_by)
        VALUES
            (v_booking.id, p_audit_date, v_night_rate, v_room_charge, v_service_tax,
             v_tourism_tax_per_night, v_extra_bed_charge_per_night, v_extra_bed_tax,
             v_night_total, v_audit_run_id, p_user_id);

        INSERT INTO night_audit_details (audit_run_id, booking_id, record_type, action, data)
        VALUES (v_audit_run_id, v_booking.id, 'booking', 'night_posted',
            jsonb_build_object(
                'status', v_booking.status,
                'room_rate', v_booking.room_rate,
                'night_rate', v_night_rate,
                'has_daily_rates', (v_booking.daily_rates IS NOT NULL),
                'night_date', p_audit_date,
                'room_charge', v_room_charge,
                'service_tax', v_service_tax,
                'tourism_tax', v_tourism_tax_per_night,
                'extra_bed_charge', v_extra_bed_charge_per_night,
                'extra_bed_tax', v_extra_bed_tax,
                'check_in_date', v_booking.check_in_date,
                'check_out_date', v_booking.check_out_date
            )
        );

        v_bookings_posted := v_bookings_posted + 1;
        v_revenue := v_revenue + v_night_total;
    END LOOP;

    SELECT COUNT(*) INTO v_checkins FROM bookings
    WHERE status IN ('checked_in', 'auto_checked_in', 'late_checkout', 'checked_out', 'completed')
      AND check_in_date = p_audit_date;

    SELECT COUNT(*) INTO v_checkouts FROM bookings
    WHERE status IN ('checked_out', 'completed')
    AND COALESCE((actual_check_out AT TIME ZONE COALESCE((SELECT value FROM system_settings WHERE key = 'timezone'), 'UTC'))::date, check_out_date) = p_audit_date;

    SELECT COUNT(*) INTO v_total_rooms FROM rooms WHERE is_active = true;

    SELECT
        COUNT(*) FILTER (WHERE status = 'available'),
        COUNT(*) FILTER (WHERE status = 'occupied'),
        COUNT(*) FILTER (WHERE status = 'reserved'),
        COUNT(*) FILTER (WHERE status IN ('maintenance', 'out_of_order')),
        COUNT(*) FILTER (WHERE status IN ('dirty', 'cleaning', 'reserved_dirty'))
    INTO v_rooms_available, v_rooms_occupied, v_rooms_reserved, v_rooms_maintenance, v_rooms_dirty
    FROM rooms
    WHERE is_active = true;

    SELECT COUNT(DISTINCT r.id) INTO v_rooms_occupied
    FROM rooms r
    JOIN bookings b ON r.id = b.room_id
    WHERE r.is_active = true
      AND b.status IN ('checked_in', 'auto_checked_in', 'late_checkout', 'checked_out', 'completed')
      AND b.check_in_date <= p_audit_date
      AND b.check_out_date > p_audit_date;

    IF v_total_rooms > 0 THEN
        v_occupancy_rate := ROUND((v_rooms_occupied::DECIMAL / v_total_rooms) * 100, 2);
    END IF;

    UPDATE rooms
    SET last_posted_status = status, last_posted_date = p_audit_date;

    UPDATE night_audit_runs
    SET status = 'completed',
        total_bookings_posted = v_bookings_posted,
        total_checkins = v_checkins,
        total_checkouts = v_checkouts,
        total_revenue = v_revenue,
        total_rooms_occupied = v_rooms_occupied,
        total_rooms_available = v_rooms_available,
        occupancy_rate = v_occupancy_rate,
        rooms_available = v_rooms_available,
        rooms_occupied = v_rooms_occupied,
        rooms_reserved = v_rooms_reserved,
        rooms_maintenance = v_rooms_maintenance,
        rooms_dirty = v_rooms_dirty,
        run_at = CURRENT_TIMESTAMP
    WHERE id = v_audit_run_id;

    RETURN v_audit_run_id;
END;
$function$
$rna_cur$;
    rna_old constant text := $rna_old$CREATE OR REPLACE FUNCTION public.run_night_audit(p_audit_date date, p_user_id bigint)
 RETURNS bigint
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_audit_run_id BIGINT;
    v_bookings_posted INTEGER := 0;
    v_checkins INTEGER := 0;
    v_checkouts INTEGER := 0;
    v_revenue DECIMAL(12, 2) := 0;
    v_rooms_occupied INTEGER := 0;
    v_rooms_available INTEGER := 0;
    v_rooms_reserved INTEGER := 0;
    v_rooms_maintenance INTEGER := 0;
    v_rooms_dirty INTEGER := 0;
    v_total_rooms INTEGER := 0;
    v_occupancy_rate DECIMAL(5, 2) := 0;
    v_booking RECORD;
    v_tax_rate DECIMAL(5, 4) := 0.08;
    v_night_rate DECIMAL(10, 2);
    v_room_charge DECIMAL(10, 2);
    v_service_tax DECIMAL(10, 2);
    v_tourism_tax_per_night DECIMAL(10, 2);
    v_nights INTEGER;
    v_extra_bed_charge_per_night DECIMAL(10, 2);
    v_extra_bed_tax DECIMAL(10, 2);
    v_night_total DECIMAL(10, 2);
BEGIN
    IF EXISTS (SELECT 1 FROM night_audit_runs WHERE audit_date = p_audit_date AND status = 'completed') THEN
        RAISE EXCEPTION 'Night audit already completed for date %', p_audit_date;
    END IF;

    BEGIN
        SELECT CAST(value AS DECIMAL) / 100.0 INTO v_tax_rate
        FROM system_settings WHERE key = 'service_tax_rate';
    EXCEPTION WHEN OTHERS THEN
        v_tax_rate := 0.08;
    END;

    INSERT INTO night_audit_runs (audit_date, run_by, status)
    VALUES (p_audit_date, p_user_id, 'in_progress')
    RETURNING id INTO v_audit_run_id;

    FOR v_booking IN
        SELECT b.id, b.booking_number, b.status, b.room_rate, b.total_amount,
               b.check_in_date, b.check_out_date, b.guest_id, b.room_id,
               b.daily_rates,
               COALESCE(b.is_tourist, false) as is_tourist,
               COALESCE(b.tourism_tax_amount, 0) as tourism_tax_amount,
               COALESCE(b.extra_bed_charge, 0) as extra_bed_charge
        FROM bookings b
        WHERE b.status NOT IN ('pending', 'confirmed', 'no_show', 'voided')
        AND b.check_in_date <= p_audit_date
        AND b.check_out_date > p_audit_date
        AND NOT EXISTS (
            SELECT 1 FROM night_audit_posted_nights napn
            WHERE napn.booking_id = b.id AND napn.audit_date = p_audit_date
        )
    LOOP
        IF v_booking.daily_rates IS NOT NULL
           AND v_booking.daily_rates ? p_audit_date::TEXT THEN
            v_night_rate := (v_booking.daily_rates ->> p_audit_date::TEXT)::DECIMAL;
        ELSE
            v_night_rate := v_booking.room_rate;
        END IF;

        v_room_charge := ROUND(v_night_rate / (1 + v_tax_rate), 2);
        v_service_tax := v_night_rate - v_room_charge;

        v_tourism_tax_per_night := 0;
        IF v_booking.is_tourist AND v_booking.tourism_tax_amount > 0 THEN
            v_nights := GREATEST((v_booking.check_out_date - v_booking.check_in_date), 1);
            v_tourism_tax_per_night := ROUND(v_booking.tourism_tax_amount / v_nights, 2);
        END IF;

        v_extra_bed_charge_per_night := 0;
        v_extra_bed_tax := 0;
        IF v_booking.extra_bed_charge > 0 THEN
            v_extra_bed_charge_per_night := ROUND(v_booking.extra_bed_charge / (1 + v_tax_rate), 2);
            v_extra_bed_tax := v_booking.extra_bed_charge - v_extra_bed_charge_per_night;
        END IF;

        v_night_total := v_night_rate + v_booking.extra_bed_charge + v_tourism_tax_per_night;

        INSERT INTO night_audit_posted_nights
            (booking_id, audit_date, room_rate, room_charge, service_tax, tourism_tax,
             extra_bed_charge, extra_bed_tax, total_posted, audit_run_id, posted_by)
        VALUES
            (v_booking.id, p_audit_date, v_night_rate, v_room_charge, v_service_tax,
             v_tourism_tax_per_night, v_extra_bed_charge_per_night, v_extra_bed_tax,
             v_night_total, v_audit_run_id, p_user_id);

        INSERT INTO night_audit_details (audit_run_id, booking_id, record_type, action, data)
        VALUES (v_audit_run_id, v_booking.id, 'booking', 'night_posted',
            jsonb_build_object(
                'status', v_booking.status,
                'room_rate', v_booking.room_rate,
                'night_rate', v_night_rate,
                'has_daily_rates', (v_booking.daily_rates IS NOT NULL),
                'night_date', p_audit_date,
                'room_charge', v_room_charge,
                'service_tax', v_service_tax,
                'tourism_tax', v_tourism_tax_per_night,
                'extra_bed_charge', v_extra_bed_charge_per_night,
                'extra_bed_tax', v_extra_bed_tax,
                'check_in_date', v_booking.check_in_date,
                'check_out_date', v_booking.check_out_date
            )
        );

        v_bookings_posted := v_bookings_posted + 1;
        v_revenue := v_revenue + v_night_total;
    END LOOP;

    -- Same-day checkout (hourly stays)
    FOR v_booking IN
        SELECT b.id, b.booking_number, b.status, b.room_rate, b.total_amount,
               b.check_in_date, b.check_out_date, b.guest_id, b.room_id,
               b.daily_rates,
               COALESCE(b.is_tourist, false) as is_tourist,
               COALESCE(b.tourism_tax_amount, 0) as tourism_tax_amount,
               COALESCE(b.extra_bed_charge, 0) as extra_bed_charge
        FROM bookings b
        WHERE b.status = 'checked_out'
        AND b.check_in_date = p_audit_date
        AND b.check_out_date = p_audit_date
        AND NOT EXISTS (
            SELECT 1 FROM night_audit_posted_nights napn
            WHERE napn.booking_id = b.id AND napn.audit_date = p_audit_date
        )
    LOOP
        IF v_booking.daily_rates IS NOT NULL
           AND v_booking.daily_rates ? p_audit_date::TEXT THEN
            v_night_rate := (v_booking.daily_rates ->> p_audit_date::TEXT)::DECIMAL;
        ELSE
            v_night_rate := v_booking.room_rate;
        END IF;

        v_room_charge := ROUND(v_night_rate / (1 + v_tax_rate), 2);
        v_service_tax := v_night_rate - v_room_charge;

        v_tourism_tax_per_night := 0;
        IF v_booking.is_tourist AND v_booking.tourism_tax_amount > 0 THEN
            v_tourism_tax_per_night := v_booking.tourism_tax_amount;
        END IF;

        v_extra_bed_charge_per_night := 0;
        v_extra_bed_tax := 0;
        IF v_booking.extra_bed_charge > 0 THEN
            v_extra_bed_charge_per_night := ROUND(v_booking.extra_bed_charge / (1 + v_tax_rate), 2);
            v_extra_bed_tax := v_booking.extra_bed_charge - v_extra_bed_charge_per_night;
        END IF;

        v_night_total := v_night_rate + v_booking.extra_bed_charge + v_tourism_tax_per_night;

        INSERT INTO night_audit_posted_nights
            (booking_id, audit_date, room_rate, room_charge, service_tax, tourism_tax,
             extra_bed_charge, extra_bed_tax, total_posted, audit_run_id, posted_by)
        VALUES
            (v_booking.id, p_audit_date, v_night_rate, v_room_charge, v_service_tax,
             v_tourism_tax_per_night, v_extra_bed_charge_per_night, v_extra_bed_tax,
             v_night_total, v_audit_run_id, p_user_id);

        INSERT INTO night_audit_details (audit_run_id, booking_id, record_type, action, data)
        VALUES (v_audit_run_id, v_booking.id, 'booking', 'night_posted',
            jsonb_build_object(
                'status', v_booking.status,
                'room_rate', v_booking.room_rate,
                'night_rate', v_night_rate,
                'has_daily_rates', (v_booking.daily_rates IS NOT NULL),
                'night_date', p_audit_date,
                'room_charge', v_room_charge,
                'service_tax', v_service_tax,
                'tourism_tax', v_tourism_tax_per_night,
                'extra_bed_charge', v_extra_bed_charge_per_night,
                'extra_bed_tax', v_extra_bed_tax,
                'check_in_date', v_booking.check_in_date,
                'check_out_date', v_booking.check_out_date
            )
        );

        v_bookings_posted := v_bookings_posted + 1;
        v_revenue := v_revenue + v_night_total;
        v_checkouts := v_checkouts + 1;
    END LOOP;

    SELECT COUNT(*) INTO v_checkins FROM bookings
    WHERE status IN ('checked_in', 'auto_checked_in') AND check_in_date = p_audit_date;

    SELECT COUNT(*) INTO v_checkouts FROM bookings
    WHERE status = 'checked_out'
    AND COALESCE((actual_check_out AT TIME ZONE COALESCE((SELECT value FROM system_settings WHERE key = 'timezone'), 'UTC'))::date, check_out_date) = p_audit_date;

    SELECT COUNT(*) INTO v_total_rooms FROM rooms;

    SELECT
        COUNT(*) FILTER (WHERE status = 'available' OR status = 'clean'),
        COUNT(*) FILTER (WHERE status = 'occupied'),
        COUNT(*) FILTER (WHERE status = 'reserved'),
        COUNT(*) FILTER (WHERE status IN ('maintenance', 'out_of_order')),
        COUNT(*) FILTER (WHERE status IN ('dirty', 'cleaning', 'reserved_dirty'))
    INTO v_rooms_available, v_rooms_occupied, v_rooms_reserved, v_rooms_maintenance, v_rooms_dirty
    FROM rooms;

    SELECT COUNT(DISTINCT r.id) INTO v_rooms_occupied
    FROM rooms r
    JOIN bookings b ON r.id = b.room_id
    WHERE b.status IN ('checked_in', 'auto_checked_in')
    AND b.check_in_date <= p_audit_date
    AND b.check_out_date > p_audit_date;

    IF v_total_rooms > 0 THEN
        v_occupancy_rate := ROUND((v_rooms_occupied::DECIMAL / v_total_rooms) * 100, 2);
    END IF;

    UPDATE rooms
    SET last_posted_status = status, last_posted_date = p_audit_date;

    UPDATE night_audit_runs
    SET status = 'completed',
        total_bookings_posted = v_bookings_posted,
        total_checkins = v_checkins,
        total_checkouts = v_checkouts,
        total_revenue = v_revenue,
        total_rooms_occupied = v_rooms_occupied,
        total_rooms_available = v_rooms_available,
        occupancy_rate = v_occupancy_rate,
        rooms_available = v_rooms_available,
        rooms_occupied = v_rooms_occupied,
        rooms_reserved = v_rooms_reserved,
        rooms_maintenance = v_rooms_maintenance,
        rooms_dirty = v_rooms_dirty,
        run_at = NOW()
    WHERE id = v_audit_run_id;

    RETURN v_audit_run_id;
END;
$function$
$rna_old$;
BEGIN
    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.get_unposted_bookings(date)');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'get_unposted_bookings(date) has incompatible definition: <missing>';
    ELSIF found_definition = unposted_old THEN
        EXECUTE unposted_current;
    ELSIF found_definition <> unposted_current THEN
        RAISE EXCEPTION 'get_unposted_bookings(date) has incompatible definition: %', found_definition;
    END IF;

    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.run_night_audit(date,bigint)');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'run_night_audit(date, bigint) has incompatible definition: <missing>';
    ELSIF found_definition = rna_old THEN
        EXECUTE rna_current;
    ELSIF found_definition <> rna_current THEN
        RAISE EXCEPTION 'run_night_audit(date, bigint) has incompatible definition: %', found_definition;
    END IF;
END;
$night_audit_routines$;

-- 10. sync_booking_payment_status() (leaves baseline at 0002's definition so 0002 passes on fresh installs)
DO $payment_sync$
DECLARE
    found_definition text;
    current_definition constant text := $ps_cur$CREATE OR REPLACE FUNCTION public.sync_booking_payment_status()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_booking_id BIGINT;
    v_settled NUMERIC;
    v_new_status TEXT;
BEGIN
    -- Determine the affected booking_id (NEW for INSERT/UPDATE, OLD for DELETE)
    v_booking_id := COALESCE(NEW.booking_id, OLD.booking_id);

    -- Money that settles the booking's charges: completed payments excluding
    -- refunds, deposits, and auxiliary non-room charges (service/damage).
    -- Mirrors PaymentRepository::recompute_booking_payment_status.
    SELECT COALESCE(SUM(amount), 0)
      INTO v_settled
      FROM payments
     WHERE booking_id = v_booking_id
       AND status = 'completed'
       AND COALESCE(payment_type, 'booking') NOT IN ('refund', 'deposit', 'deposit_forfeited', 'service', 'damage');

    SELECT CASE
        WHEN b.status = 'voided' THEN 'void'
        WHEN (b.total_amount + COALESCE(b.tourism_tax_amount, 0)
                + COALESCE(b.extra_bed_charge, 0)) <= 0 THEN 'paid'
        WHEN v_settled >= (b.total_amount + COALESCE(b.tourism_tax_amount, 0)
                + COALESCE(b.extra_bed_charge, 0)) THEN 'paid'
        WHEN v_settled > 0 THEN 'partial'
        ELSE 'unpaid'
    END INTO v_new_status
    FROM bookings b
    WHERE b.id = v_booking_id;

    UPDATE bookings
       SET payment_status = v_new_status,
           updated_at = CURRENT_TIMESTAMP
     WHERE id = v_booking_id;

    RETURN COALESCE(NEW, OLD);
END;
$function$
$ps_cur$;
    old_definition constant text := $ps_old$CREATE OR REPLACE FUNCTION public.sync_booking_payment_status()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_booking_id INTEGER;
    v_settled NUMERIC;
    v_new_status TEXT;
BEGIN
    -- Determine the affected booking_id (NEW for INSERT/UPDATE, OLD for DELETE)
    v_booking_id := COALESCE(NEW.booking_id, OLD.booking_id);

    -- Money that settles the booking's charges: completed payments excluding
    -- refunds and deposits. A held deposit is collateral, not a room payment;
    -- a forfeited deposit is money the hotel kept, not a bill settlement.
    -- Mirrors PaymentRepository::recompute_booking_payment_status.
    SELECT COALESCE(SUM(amount), 0)
      INTO v_settled
      FROM payments
     WHERE booking_id = v_booking_id
       AND status = 'completed'
       AND COALESCE(payment_type, 'booking') NOT IN ('refund', 'deposit', 'deposit_forfeited');

    SELECT CASE
        WHEN b.status = 'voided' THEN 'void'
        WHEN COALESCE(b.is_complimentary, false) THEN COALESCE(b.payment_status, 'paid')
        WHEN (b.total_amount + COALESCE(b.tourism_tax_amount, 0)
                + COALESCE(b.extra_bed_charge, 0)) <= 0 THEN 'paid'
        WHEN v_settled >= (b.total_amount + COALESCE(b.tourism_tax_amount, 0)
                + COALESCE(b.extra_bed_charge, 0)) THEN 'paid'
        WHEN v_settled > 0 THEN 'partial'
        ELSE 'unpaid'
    END INTO v_new_status
    FROM bookings b
    WHERE b.id = v_booking_id;

    UPDATE bookings
       SET payment_status = v_new_status,
           updated_at = CURRENT_TIMESTAMP
     WHERE id = v_booking_id;

    RETURN COALESCE(NEW, OLD);
END;
$function$
$ps_old$;
BEGIN
    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.sync_booking_payment_status()');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'sync_booking_payment_status() has incompatible definition: <missing>';
    ELSIF found_definition = old_definition THEN
        EXECUTE current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'sync_booking_payment_status() has incompatible definition: %', found_definition;
    END IF;
END;
$payment_sync$;

-- 11. Room status transitions, housekeeping task deduplication, and booking->room sync routines/trigger
--     (reconcile_room_holds, sync_all_room_statuses, and sync_room_status_with_booking stay at 0018's definition in baseline so 0018 passes)
DO $room_sync_and_transitions$
DECLARE
    found_definition text;
    urs_current constant text := $urs_cur$CREATE OR REPLACE FUNCTION public.update_room_status(p_room_id bigint, p_new_status character varying, p_notes text DEFAULT NULL::text, p_user_id bigint DEFAULT NULL::bigint, p_start_date timestamp with time zone DEFAULT NULL::timestamp with time zone, p_end_date timestamp with time zone DEFAULT NULL::timestamp with time zone)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_old_status VARCHAR(20);
BEGIN
    SELECT status INTO v_old_status FROM rooms WHERE id = p_room_id;
    INSERT INTO room_status_change_log (room_id, from_status, to_status, trigger_source, reason)
    VALUES (p_room_id, v_old_status, p_new_status, 'update_room_status', p_notes);
    PERFORM validate_room_status_transition(p_room_id, p_new_status, p_user_id);
    UPDATE rooms SET status = p_new_status, status_notes = COALESCE(p_notes, '') || ' [via update_room_status]',
        updated_at = CURRENT_TIMESTAMP,
        reserved_start_date = CASE WHEN p_new_status = 'reserved' THEN COALESCE(p_start_date, CURRENT_TIMESTAMP) ELSE NULL END,
        reserved_end_date = CASE WHEN p_new_status = 'reserved' THEN p_end_date ELSE NULL END,
        maintenance_start_date = CASE WHEN p_new_status = 'maintenance' THEN COALESCE(p_start_date, CURRENT_TIMESTAMP) ELSE NULL END,
        maintenance_end_date = CASE WHEN p_new_status = 'maintenance' THEN p_end_date ELSE NULL END,
        cleaning_start_date = CASE WHEN p_new_status IN ('cleaning', 'dirty', 'reserved_dirty') THEN COALESCE(p_start_date, CURRENT_TIMESTAMP) ELSE NULL END,
        cleaning_end_date = CASE WHEN p_new_status IN ('cleaning', 'dirty', 'reserved_dirty') THEN p_end_date ELSE NULL END
    WHERE id = p_room_id;
    INSERT INTO room_history (room_id, from_status, to_status, notes, start_date, end_date, changed_by, is_auto_generated)
    VALUES (p_room_id, v_old_status, p_new_status, p_notes, p_start_date, p_end_date, p_user_id, p_user_id IS NULL);
    IF p_new_status IN ('dirty', 'cleaning', 'reserved_dirty') THEN
        INSERT INTO housekeeping_tasks (room_id, task_type, priority, status, created_by, notes)
        SELECT p_room_id, 'cleaning', 'normal',
               CASE WHEN p_new_status = 'cleaning' THEN 'in_progress' ELSE 'pending' END,
               p_user_id, p_notes
        WHERE NOT EXISTS (
            SELECT 1 FROM housekeeping_tasks ht
             WHERE ht.room_id = p_room_id
               AND ht.task_type = 'cleaning'
               AND ht.status IN ('pending', 'in_progress')
        );
    END IF;
END;
$function$
$urs_cur$;
    urs_old constant text := $urs_old$CREATE OR REPLACE FUNCTION public.update_room_status(p_room_id bigint, p_new_status character varying, p_notes text DEFAULT NULL::text, p_user_id bigint DEFAULT NULL::bigint, p_start_date timestamp with time zone DEFAULT NULL::timestamp with time zone, p_end_date timestamp with time zone DEFAULT NULL::timestamp with time zone)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_old_status VARCHAR(20);
BEGIN
    SELECT status INTO v_old_status FROM rooms WHERE id = p_room_id;
    INSERT INTO room_status_change_log (room_id, from_status, to_status, trigger_source, reason)
    VALUES (p_room_id, v_old_status, p_new_status, 'update_room_status', p_notes);
    PERFORM validate_room_status_transition(p_room_id, p_new_status, p_user_id);
    UPDATE rooms SET status = p_new_status, status_notes = COALESCE(p_notes, '') || ' [via update_room_status]',
        updated_at = CURRENT_TIMESTAMP,
        reserved_start_date = CASE WHEN p_new_status = 'reserved' THEN COALESCE(p_start_date, CURRENT_TIMESTAMP) ELSE NULL END,
        reserved_end_date = CASE WHEN p_new_status = 'reserved' THEN p_end_date ELSE NULL END,
        maintenance_start_date = CASE WHEN p_new_status = 'maintenance' THEN COALESCE(p_start_date, CURRENT_TIMESTAMP) ELSE NULL END,
        maintenance_end_date = CASE WHEN p_new_status = 'maintenance' THEN p_end_date ELSE NULL END,
        cleaning_start_date = CASE WHEN p_new_status IN ('cleaning', 'dirty', 'reserved_dirty') THEN COALESCE(p_start_date, CURRENT_TIMESTAMP) ELSE NULL END,
        cleaning_end_date = CASE WHEN p_new_status IN ('cleaning', 'dirty', 'reserved_dirty') THEN p_end_date ELSE NULL END
    WHERE id = p_room_id;
    INSERT INTO room_history (room_id, from_status, to_status, notes, start_date, end_date, changed_by, is_auto_generated)
    VALUES (p_room_id, v_old_status, p_new_status, p_notes, p_start_date, p_end_date, p_user_id, p_user_id IS NULL);
    IF p_new_status IN ('dirty', 'cleaning', 'reserved_dirty') THEN
        INSERT INTO housekeeping_tasks (room_id, task_type, priority, status, created_by, notes)
        VALUES (p_room_id, 'cleaning', 'normal', CASE WHEN p_new_status = 'cleaning' THEN 'in_progress' ELSE 'pending' END, p_user_id, p_notes)
        ON CONFLICT DO NOTHING;
    END IF;
END;
$function$
$urs_old$;
    vrst_current constant text := $vrst_cur$CREATE OR REPLACE FUNCTION public.validate_room_status_transition(p_room_id bigint, p_new_status character varying, p_user_id bigint DEFAULT NULL::bigint)
 RETURNS boolean
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_current_status VARCHAR(20);
    v_is_allowed BOOLEAN;
    v_count INT;
BEGIN
    SELECT status INTO v_current_status FROM rooms WHERE id = p_room_id;
    IF v_current_status IS NULL THEN RAISE EXCEPTION 'Room % not found', p_room_id; END IF;
    IF v_current_status = p_new_status THEN RETURN true; END IF;

    -- Auto-seed transitions if table is empty
    SELECT COUNT(*) INTO v_count FROM room_status_transitions;
    IF v_count = 0 THEN
        INSERT INTO room_status_transitions (from_status, to_status, is_allowed) VALUES
        ('available', 'occupied', true), ('available', 'reserved', true),
        ('available', 'reserved_dirty', true), ('available', 'cleaning', true),
        ('available', 'dirty', true), ('available', 'maintenance', true),
        ('available', 'out_of_order', true),
        ('occupied', 'available', true), ('occupied', 'dirty', true),
        ('occupied', 'maintenance', true), ('occupied', 'out_of_order', true),
        ('occupied', 'reserved', true), ('occupied', 'reserved_dirty', true),
        ('reserved', 'occupied', true), ('reserved', 'available', true),
        ('reserved', 'dirty', true), ('reserved', 'reserved_dirty', true),
        ('reserved', 'cleaning', true), ('reserved', 'maintenance', true),
        ('reserved', 'out_of_order', true),
        ('dirty', 'available', true), ('dirty', 'cleaning', true),
        ('dirty', 'maintenance', true), ('dirty', 'out_of_order', true),
        ('dirty', 'reserved', true), ('dirty', 'reserved_dirty', true),
        ('dirty', 'occupied', true),
        ('cleaning', 'available', true), ('cleaning', 'dirty', true),
        ('cleaning', 'reserved', true), ('cleaning', 'reserved_dirty', true),
        ('cleaning', 'maintenance', true), ('cleaning', 'out_of_order', true),
        ('reserved_dirty', 'reserved', true), ('reserved_dirty', 'dirty', true),
        ('reserved_dirty', 'cleaning', true), ('reserved_dirty', 'maintenance', true),
        ('reserved_dirty', 'out_of_order', true), ('reserved_dirty', 'available', true),
        ('reserved_dirty', 'occupied', true),
        ('maintenance', 'available', true), ('maintenance', 'dirty', true),
        ('maintenance', 'reserved', true), ('maintenance', 'reserved_dirty', true),
        ('maintenance', 'out_of_order', true),
        ('out_of_order', 'available', true), ('out_of_order', 'maintenance', true),
        ('out_of_order', 'dirty', true), ('out_of_order', 'reserved', true),
        ('out_of_order', 'reserved_dirty', true)
        ON CONFLICT (from_status, to_status) DO NOTHING;
    END IF;

    SELECT is_allowed INTO v_is_allowed FROM room_status_transitions
    WHERE from_status = v_current_status AND to_status = p_new_status;
    IF NOT FOUND THEN RAISE EXCEPTION 'Transition from % to % is not defined', v_current_status, p_new_status; END IF;
    IF NOT v_is_allowed THEN RAISE EXCEPTION 'Transition from % to % is not allowed', v_current_status, p_new_status; END IF;
    RETURN true;
END;
$function$
$vrst_cur$;
    vrst_old constant text := $vrst_old$CREATE OR REPLACE FUNCTION public.validate_room_status_transition(p_room_id bigint, p_new_status character varying, p_user_id bigint DEFAULT NULL::bigint)
 RETURNS boolean
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_current_status VARCHAR(20);
    v_is_allowed BOOLEAN;
    v_count INT;
BEGIN
    SELECT status INTO v_current_status FROM rooms WHERE id = p_room_id;
    IF v_current_status IS NULL THEN RAISE EXCEPTION 'Room % not found', p_room_id; END IF;
    IF v_current_status = p_new_status THEN RETURN true; END IF;

    -- Auto-seed transitions if table is empty
    SELECT COUNT(*) INTO v_count FROM room_status_transitions;
    IF v_count = 0 THEN
        INSERT INTO room_status_transitions (from_status, to_status, is_allowed) VALUES
        ('available', 'occupied', true), ('available', 'reserved', true),
        ('available', 'reserved_dirty', true),
        ('available', 'dirty', true), ('available', 'maintenance', true),
        ('available', 'out_of_order', true),
        ('occupied', 'available', true), ('occupied', 'dirty', true),
        ('occupied', 'maintenance', true), ('occupied', 'reserved', true),
        ('occupied', 'reserved_dirty', true),
        ('reserved', 'occupied', true), ('reserved', 'available', true),
        ('reserved', 'dirty', true), ('reserved', 'reserved_dirty', true),
        ('reserved', 'maintenance', true),
        ('dirty', 'available', true), ('dirty', 'maintenance', true),
        ('dirty', 'reserved', true), ('dirty', 'reserved_dirty', true),
        ('dirty', 'occupied', true),
        ('cleaning', 'available', true), ('cleaning', 'dirty', true),
        ('cleaning', 'reserved_dirty', true), ('cleaning', 'maintenance', true),
        ('reserved_dirty', 'reserved', true), ('reserved_dirty', 'dirty', true),
        ('reserved_dirty', 'maintenance', true),
        ('reserved_dirty', 'available', true),
        ('maintenance', 'available', true), ('maintenance', 'dirty', true),
        ('maintenance', 'out_of_order', true),
        ('out_of_order', 'available', true), ('out_of_order', 'maintenance', true),
        ('out_of_order', 'dirty', true)
        ON CONFLICT DO NOTHING;
    END IF;

    SELECT is_allowed INTO v_is_allowed FROM room_status_transitions
    WHERE from_status = v_current_status AND to_status = p_new_status;
    IF NOT FOUND THEN RAISE EXCEPTION 'Transition from % to % is not defined', v_current_status, p_new_status; END IF;
    IF NOT v_is_allowed THEN RAISE EXCEPTION 'Transition from % to % is not allowed', v_current_status, p_new_status; END IF;
    RETURN true;
END;
$function$
$vrst_old$;
    reconcile_current constant text := $rec_cur$CREATE OR REPLACE FUNCTION public.reconcile_room_holds(p_room_id bigint DEFAULT NULL::bigint)
 RETURNS TABLE(room_id bigint, room_number character varying, old_status character varying, new_status character varying)
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_room RECORD;
    v_desired VARCHAR(20);
    v_in_house BOOLEAN;
    v_arrival_id BIGINT;
    v_arrival_in DATE;
    v_arrival_out DATE;
    v_reason TEXT;
BEGIN
    FOR v_room IN
        SELECT r.id, r.room_number, r.status
        FROM public.rooms r
        WHERE r.is_active = true
          AND r.status IN ('available', 'reserved', 'dirty', 'reserved_dirty')
          AND (p_room_id IS NULL OR r.id = p_room_id)
        ORDER BY r.id
        FOR UPDATE OF r
    LOOP
        SELECT EXISTS (
            SELECT 1 FROM public.bookings b
            WHERE b.room_id = v_room.id
              AND b.status IN ('checked_in', 'auto_checked_in', 'late_checkout')
              AND b.check_in_date <= CURRENT_DATE
              AND b.check_out_date >= CURRENT_DATE
        ) INTO v_in_house;

        v_arrival_id := NULL;
        v_arrival_in := NULL;
        v_arrival_out := NULL;
        SELECT b.id, b.check_in_date, b.check_out_date
          INTO v_arrival_id, v_arrival_in, v_arrival_out
          FROM public.bookings b
         WHERE b.room_id = v_room.id
           AND b.status IN ('confirmed', 'pending', 'pending_payment', 'pending_confirmation', 'partial_complimentary', 'fully_complimentary')
           AND b.check_in_date <= CURRENT_DATE
           AND b.check_out_date >= CURRENT_DATE
         ORDER BY b.check_in_date, b.id
         LIMIT 1;

        IF v_room.status IN ('available', 'reserved') AND v_in_house THEN
            v_desired := 'occupied';
            v_reason := 'Guest in house';
        ELSIF v_room.status IN ('available', 'reserved') THEN
            v_desired := CASE WHEN v_arrival_id IS NULL THEN 'available' ELSE 'reserved' END;
        ELSE
            v_desired := CASE WHEN v_arrival_id IS NULL THEN 'dirty' ELSE 'reserved_dirty' END;
        END IF;

        IF v_desired <> v_room.status THEN
            IF v_desired IN ('reserved', 'reserved_dirty') THEN
                v_reason := 'Arrival day - Booking #' || v_arrival_id;
            ELSIF v_desired IN ('available', 'dirty') THEN
                v_reason := 'No arrival today - reservation hold released';
            END IF;

            INSERT INTO public.room_status_change_log (room_id, from_status, to_status, trigger_source, booking_id, reason)
            VALUES (v_room.id, v_room.status, v_desired, 'reconcile_room_holds', v_arrival_id, v_reason);

            UPDATE public.rooms AS r
               SET status = v_desired,
                   status_notes = v_reason,
                   reserved_start_date = CASE WHEN v_desired IN ('reserved', 'reserved_dirty')
                       THEN v_arrival_in::timestamp AT TIME ZONE 'UTC' END,
                   reserved_end_date = CASE WHEN v_desired IN ('reserved', 'reserved_dirty')
                       THEN v_arrival_out::timestamp AT TIME ZONE 'UTC' END,
                   updated_at = CURRENT_TIMESTAMP
             WHERE r.id = v_room.id;

            room_id := v_room.id;
            room_number := v_room.room_number;
            old_status := v_room.status;
            new_status := v_desired;
            RETURN NEXT;
        END IF;
    END LOOP;
END;
$function$
$rec_cur$;
    reconcile_old constant text := $rec_old$CREATE OR REPLACE FUNCTION public.reconcile_room_holds(p_room_id bigint DEFAULT NULL::bigint)
 RETURNS TABLE(room_id bigint, room_number character varying, old_status character varying, new_status character varying)
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_room RECORD;
    v_desired VARCHAR(20);
    v_in_house BOOLEAN;
    v_arrival_id BIGINT;
    v_arrival_in DATE;
    v_arrival_out DATE;
    v_reason TEXT;
BEGIN
    -- A reservation holds its room from its arrival day only. A room reads
    -- reserved (reserved_dirty while it still needs cleaning) when a
    -- room-holding booking has check_in_date <= CURRENT_DATE <= check_out_date;
    -- future stays stay on the timeline and never mark the room. Occupied,
    -- cleaning, maintenance and out_of_order rooms are never touched, except
    -- that an available or reserved room with a guest in house reads occupied.
    -- Idempotent: rooms already in the right state are skipped.
    FOR v_room IN
        SELECT r.id, r.room_number, r.status
        FROM public.rooms r
        WHERE r.is_active = true
          AND r.status IN ('available', 'reserved', 'dirty', 'reserved_dirty')
          AND (p_room_id IS NULL OR r.id = p_room_id)
        ORDER BY r.id
        FOR UPDATE OF r
    LOOP
        SELECT EXISTS (
            SELECT 1 FROM public.bookings b
            WHERE b.room_id = v_room.id
              AND b.status IN ('checked_in', 'auto_checked_in', 'late_checkout')
              AND b.check_in_date <= CURRENT_DATE
              AND b.check_out_date >= CURRENT_DATE
        ) INTO v_in_house;

        v_arrival_id := NULL;
        v_arrival_in := NULL;
        v_arrival_out := NULL;
        SELECT b.id, b.check_in_date, b.check_out_date
          INTO v_arrival_id, v_arrival_in, v_arrival_out
          FROM public.bookings b
         WHERE b.room_id = v_room.id
           AND b.status IN ('confirmed', 'pending', 'pending_payment', 'pending_confirmation')
           AND b.check_in_date <= CURRENT_DATE
           AND b.check_out_date >= CURRENT_DATE
         ORDER BY b.check_in_date, b.id
         LIMIT 1;

        IF v_room.status IN ('available', 'reserved') AND v_in_house THEN
            v_desired := 'occupied';
            v_reason := 'Guest in house';
        ELSIF v_room.status IN ('available', 'reserved') THEN
            v_desired := CASE WHEN v_arrival_id IS NULL THEN 'available' ELSE 'reserved' END;
        ELSE
            v_desired := CASE WHEN v_arrival_id IS NULL THEN 'dirty' ELSE 'reserved_dirty' END;
        END IF;

        IF v_desired <> v_room.status THEN
            IF v_desired IN ('reserved', 'reserved_dirty') THEN
                v_reason := 'Arrival day - Booking #' || v_arrival_id;
            ELSIF v_desired IN ('available', 'dirty') THEN
                v_reason := 'No arrival today - reservation hold released';
            END IF;

            INSERT INTO public.room_status_change_log (room_id, from_status, to_status, trigger_source, booking_id, reason)
            VALUES (v_room.id, v_room.status, v_desired, 'reconcile_room_holds', v_arrival_id, v_reason);

            UPDATE public.rooms AS r
               SET status = v_desired,
                   status_notes = v_reason,
                   reserved_start_date = CASE WHEN v_desired IN ('reserved', 'reserved_dirty')
                       THEN v_arrival_in::timestamp AT TIME ZONE 'UTC' END,
                   reserved_end_date = CASE WHEN v_desired IN ('reserved', 'reserved_dirty')
                       THEN v_arrival_out::timestamp AT TIME ZONE 'UTC' END,
                   updated_at = CURRENT_TIMESTAMP
             WHERE r.id = v_room.id;

            room_id := v_room.id;
            room_number := v_room.room_number;
            old_status := v_room.status;
            new_status := v_desired;
            RETURN NEXT;
        END IF;
    END LOOP;
END;
$function$
$rec_old$;
    sync_all_current constant text := $sa_cur$CREATE OR REPLACE FUNCTION public.sync_all_room_statuses(p_user_id bigint DEFAULT NULL::bigint)
 RETURNS TABLE(room_id bigint, room_number character varying, old_status character varying, new_status character varying)
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_room RECORD;
    v_desired VARCHAR(20);
    v_res_start DATE;
    v_res_end DATE;
BEGIN
    FOR v_room IN
        SELECT r.id, r.room_number, r.status
        FROM rooms r
        WHERE r.is_active = true
          AND r.status IN ('available', 'occupied', 'reserved')
        ORDER BY r.id
    LOOP
        v_res_start := NULL;
        v_res_end := NULL;

        IF EXISTS (
            SELECT 1 FROM bookings b
            WHERE b.room_id = v_room.id
              AND b.status IN ('checked_in', 'auto_checked_in', 'late_checkout')
              AND b.check_in_date <= CURRENT_DATE
              AND b.check_out_date >= CURRENT_DATE
        ) THEN
            v_desired := 'occupied';
        ELSE
            SELECT b.check_in_date, b.check_out_date
              INTO v_res_start, v_res_end
              FROM bookings b
             WHERE b.room_id = v_room.id
               AND b.status IN ('confirmed', 'pending', 'pending_payment', 'pending_confirmation', 'partial_complimentary', 'fully_complimentary')
               AND b.check_in_date <= CURRENT_DATE
               AND b.check_out_date >= CURRENT_DATE
             ORDER BY b.check_in_date
             LIMIT 1;
            IF FOUND THEN
                v_desired := 'reserved';
            ELSE
                v_desired := 'available';
            END IF;
        END IF;

        IF v_desired <> v_room.status THEN
            PERFORM update_room_status(v_room.id, v_desired,
                'Bulk status sync from bookings', p_user_id, v_res_start, v_res_end);
            room_id := v_room.id;
            room_number := v_room.room_number;
            old_status := v_room.status;
            new_status := v_desired;
            RETURN NEXT;
        END IF;
    END LOOP;
END;
$function$
$sa_cur$;
    sync_all_old constant text := $sa_old$CREATE OR REPLACE FUNCTION public.sync_all_room_statuses(p_user_id bigint DEFAULT NULL::bigint)
 RETURNS TABLE(room_id bigint, room_number character varying, old_status character varying, new_status character varying)
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_room RECORD;
    v_desired VARCHAR(20);
    v_res_start DATE;
    v_res_end DATE;
BEGIN
    -- Bulk reconciliation of rooms.status against bookings. Mirrors the policy
    -- of the sync_room_status_with_booking() trigger: housekeeping and
    -- maintenance states (dirty, cleaning, reserved_dirty, maintenance,
    -- out_of_order) are never overridden; only the booking-derived states
    -- (available, occupied, reserved) are recomputed. A reservation holds its
    -- room from its arrival day only, as in reconcile_room_holds().
    FOR v_room IN
        SELECT r.id, r.room_number, r.status
        FROM rooms r
        WHERE r.is_active = true
          AND r.status IN ('available', 'occupied', 'reserved')
        ORDER BY r.id
    LOOP
        v_res_start := NULL;
        v_res_end := NULL;

        IF EXISTS (
            SELECT 1 FROM bookings b
            WHERE b.room_id = v_room.id
              AND b.status IN ('checked_in', 'auto_checked_in', 'late_checkout')
              AND b.check_in_date <= CURRENT_DATE
              AND b.check_out_date >= CURRENT_DATE
        ) THEN
            v_desired := 'occupied';
        ELSE
            SELECT b.check_in_date, b.check_out_date
              INTO v_res_start, v_res_end
              FROM bookings b
             WHERE b.room_id = v_room.id
               AND b.status IN ('confirmed', 'pending', 'pending_payment', 'pending_confirmation')
               AND b.check_in_date <= CURRENT_DATE
               AND b.check_out_date >= CURRENT_DATE
             ORDER BY b.check_in_date
             LIMIT 1;
            IF FOUND THEN
                v_desired := 'reserved';
            ELSE
                v_desired := 'available';
            END IF;
        END IF;

        IF v_desired <> v_room.status THEN
            PERFORM update_room_status(v_room.id, v_desired,
                'Bulk status sync from bookings', p_user_id, v_res_start, v_res_end);
            room_id := v_room.id;
            room_number := v_room.room_number;
            old_status := v_room.status;
            new_status := v_desired;
            RETURN NEXT;
        END IF;
    END LOOP;
END;
$function$
$sa_old$;
    sync_trig_current constant text := $st_cur$CREATE OR REPLACE FUNCTION public.sync_room_status_with_booking()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_current_room_status VARCHAR(20);
    v_has_other_current_stay BOOLEAN;
    v_old_room_status VARCHAR(20);
    v_old_has_other_stay BOOLEAN;
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.room_id IS DISTINCT FROM NEW.room_id THEN
        SELECT status INTO v_old_room_status FROM rooms WHERE id = OLD.room_id;
        SELECT EXISTS (
            SELECT 1 FROM bookings
            WHERE room_id = OLD.room_id
              AND id != OLD.id
              AND status IN ('checked_in', 'auto_checked_in', 'late_checkout')
              AND check_in_date <= CURRENT_DATE
              AND check_out_date >= CURRENT_DATE
        ) INTO v_old_has_other_stay;
        IF v_old_room_status = 'occupied' AND NOT v_old_has_other_stay THEN
            PERFORM update_room_status(OLD.room_id, 'available',
                'Booking moved from room - Booking #' || NEW.id, NULL, NULL, NULL);
        END IF;
        PERFORM reconcile_room_holds(OLD.room_id);
    END IF;

    -- Skip room status changes for back-dated stays that have already ended.
    IF NEW.check_out_date < CURRENT_DATE
       AND NEW.status IN ('checked_in', 'auto_checked_in', 'late_checkout', 'checked_out', 'completed') THEN
        RETURN NEW;
    END IF;

    SELECT status INTO v_current_room_status FROM rooms WHERE id = NEW.room_id;

    SELECT EXISTS (
        SELECT 1 FROM bookings
        WHERE room_id = NEW.room_id
          AND id != NEW.id
          AND status IN ('checked_in', 'auto_checked_in', 'late_checkout')
          AND check_in_date <= CURRENT_DATE
          AND check_out_date >= CURRENT_DATE
    ) INTO v_has_other_current_stay;

    IF NEW.status IN ('checked_in', 'auto_checked_in', 'late_checkout')
       AND v_current_room_status != 'occupied' THEN
        PERFORM update_room_status(NEW.room_id, 'occupied',
            'Guest checked in - Booking #' || NEW.id, NULL,
            NEW.check_in_date, NEW.check_out_date);

    ELSIF NEW.status IN ('checked_out', 'completed')
          AND v_current_room_status = 'occupied'
          AND NOT v_has_other_current_stay THEN
        PERFORM update_room_status(NEW.room_id, 'dirty',
            'Guest checked out - Needs cleaning - Booking #' || NEW.id,
            NULL, CURRENT_TIMESTAMP, NULL);
        -- A guest arriving today keeps the room held while it is cleaned.
        PERFORM reconcile_room_holds(NEW.room_id);

    ELSIF NEW.status IN ('confirmed', 'pending', 'pending_payment', 'pending_confirmation', 'partial_complimentary', 'fully_complimentary') THEN
        -- A reservation holds its room from its arrival day only; future
        -- stays leave the room alone and never overwrite an in-house stay.
        PERFORM reconcile_room_holds(NEW.room_id);

    ELSIF NEW.status IN ('no_show', 'voided', 'comp_void') THEN
        IF v_current_room_status = 'occupied' AND NOT v_has_other_current_stay THEN
            PERFORM update_room_status(NEW.room_id, 'available',
                'Booking no-show/voided - Booking #' || NEW.id, NULL, NULL, NULL);
        END IF;
        PERFORM reconcile_room_holds(NEW.room_id);
    END IF;

    RETURN NEW;
END;
$function$
$st_cur$;
    sync_trig_old constant text := $st_old$CREATE OR REPLACE FUNCTION public.sync_room_status_with_booking()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_current_room_status VARCHAR(20);
    v_has_other_current_stay BOOLEAN;
BEGIN
    -- Skip room status changes for back-dated stays that have already ended.
    IF NEW.check_out_date < CURRENT_DATE
       AND NEW.status IN ('checked_in', 'auto_checked_in', 'checked_out', 'completed') THEN
        RETURN NEW;
    END IF;

    SELECT status INTO v_current_room_status FROM rooms WHERE id = NEW.room_id;

    SELECT EXISTS (
        SELECT 1 FROM bookings
        WHERE room_id = NEW.room_id
          AND id != NEW.id
          AND status IN ('checked_in', 'auto_checked_in', 'late_checkout')
          AND check_in_date <= CURRENT_DATE
          AND check_out_date >= CURRENT_DATE
    ) INTO v_has_other_current_stay;

    IF NEW.status IN ('checked_in', 'auto_checked_in', 'late_checkout')
       AND v_current_room_status != 'occupied' THEN
        PERFORM update_room_status(NEW.room_id, 'occupied',
            'Guest checked in - Booking #' || NEW.id, NULL,
            NEW.check_in_date, NEW.check_out_date);

    ELSIF NEW.status IN ('checked_out', 'completed')
          AND v_current_room_status = 'occupied' THEN
        PERFORM update_room_status(NEW.room_id, 'dirty',
            'Guest checked out - Needs cleaning - Booking #' || NEW.id,
            NULL, CURRENT_TIMESTAMP, NULL);
        -- A guest arriving today keeps the room held while it is cleaned.
        PERFORM reconcile_room_holds(NEW.room_id);

    ELSIF NEW.status IN ('confirmed', 'pending', 'pending_payment', 'pending_confirmation') THEN
        -- A reservation holds its room from its arrival day only; future
        -- stays leave the room alone and never overwrite an in-house stay.
        PERFORM reconcile_room_holds(NEW.room_id);

    ELSIF NEW.status IN ('no_show', 'voided') THEN
        IF v_current_room_status = 'occupied' AND NOT v_has_other_current_stay THEN
            PERFORM update_room_status(NEW.room_id, 'available',
                'Booking no-show/voided - Booking #' || NEW.id, NULL, NULL, NULL);
        END IF;
        PERFORM reconcile_room_holds(NEW.room_id);
    END IF;

    RETURN NEW;
END;
$function$
$st_old$;
    trig_current constant text := 'CREATE TRIGGER trg_sync_room_status_booking AFTER INSERT OR UPDATE OF status, check_in_date, check_out_date, room_id ON public.bookings FOR EACH ROW EXECUTE FUNCTION sync_room_status_with_booking()';
    trig_old constant text := 'CREATE TRIGGER trg_sync_room_status_booking AFTER INSERT OR UPDATE OF status, check_in_date ON public.bookings FOR EACH ROW EXECUTE FUNCTION sync_room_status_with_booking()';
BEGIN
    INSERT INTO public.room_status_transitions (from_status, to_status, is_allowed) VALUES
        ('available', 'occupied', true), ('available', 'reserved', true),
        ('available', 'reserved_dirty', true), ('available', 'cleaning', true),
        ('available', 'dirty', true), ('available', 'maintenance', true),
        ('available', 'out_of_order', true),
        ('occupied', 'available', true), ('occupied', 'dirty', true),
        ('occupied', 'maintenance', true), ('occupied', 'out_of_order', true),
        ('occupied', 'reserved', true), ('occupied', 'reserved_dirty', true),
        ('reserved', 'occupied', true), ('reserved', 'available', true),
        ('reserved', 'dirty', true), ('reserved', 'reserved_dirty', true),
        ('reserved', 'cleaning', true), ('reserved', 'maintenance', true),
        ('reserved', 'out_of_order', true),
        ('dirty', 'available', true), ('dirty', 'cleaning', true),
        ('dirty', 'maintenance', true), ('dirty', 'out_of_order', true),
        ('dirty', 'reserved', true), ('dirty', 'reserved_dirty', true),
        ('dirty', 'occupied', true),
        ('cleaning', 'available', true), ('cleaning', 'dirty', true),
        ('cleaning', 'reserved', true), ('cleaning', 'reserved_dirty', true),
        ('cleaning', 'maintenance', true), ('cleaning', 'out_of_order', true),
        ('reserved_dirty', 'reserved', true), ('reserved_dirty', 'dirty', true),
        ('reserved_dirty', 'cleaning', true), ('reserved_dirty', 'maintenance', true),
        ('reserved_dirty', 'out_of_order', true), ('reserved_dirty', 'available', true),
        ('reserved_dirty', 'occupied', true),
        ('maintenance', 'available', true), ('maintenance', 'dirty', true),
        ('maintenance', 'reserved', true), ('maintenance', 'reserved_dirty', true),
        ('maintenance', 'out_of_order', true),
        ('out_of_order', 'available', true), ('out_of_order', 'maintenance', true),
        ('out_of_order', 'dirty', true), ('out_of_order', 'reserved', true),
        ('out_of_order', 'reserved_dirty', true)
    ON CONFLICT (from_status, to_status) DO UPDATE SET is_allowed = EXCLUDED.is_allowed;

    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.update_room_status(bigint,character varying,text,bigint,timestamp with time zone,timestamp with time zone)');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'update_room_status has incompatible definition: <missing>';
    ELSIF found_definition = urs_old THEN
        EXECUTE urs_current;
    ELSIF found_definition <> urs_current THEN
        RAISE EXCEPTION 'update_room_status has incompatible definition: %', found_definition;
    END IF;

    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.validate_room_status_transition(bigint,character varying,bigint)');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'validate_room_status_transition has incompatible definition: <missing>';
    ELSIF found_definition = vrst_old THEN
        EXECUTE vrst_current;
    ELSIF found_definition <> vrst_current THEN
        RAISE EXCEPTION 'validate_room_status_transition has incompatible definition: %', found_definition;
    END IF;

    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.reconcile_room_holds(bigint)');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'reconcile_room_holds(bigint) has incompatible definition: <missing>';
    ELSIF found_definition = reconcile_old THEN
        EXECUTE reconcile_current;
    ELSIF found_definition <> reconcile_current THEN
        RAISE EXCEPTION 'reconcile_room_holds(bigint) has incompatible definition: %', found_definition;
    END IF;

    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.sync_all_room_statuses(bigint)');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'sync_all_room_statuses(bigint) has incompatible definition: <missing>';
    ELSIF found_definition = sync_all_old THEN
        EXECUTE sync_all_current;
    ELSIF found_definition <> sync_all_current THEN
        RAISE EXCEPTION 'sync_all_room_statuses(bigint) has incompatible definition: %', found_definition;
    END IF;

    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.sync_room_status_with_booking()');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'sync_room_status_with_booking() has incompatible definition: <missing>';
    ELSIF found_definition = sync_trig_old THEN
        EXECUTE sync_trig_current;
    ELSIF found_definition <> sync_trig_current THEN
        RAISE EXCEPTION 'sync_room_status_with_booking() has incompatible definition: %', found_definition;
    END IF;

    SELECT pg_get_triggerdef(oid) INTO found_definition
    FROM pg_trigger WHERE tgrelid = 'public.bookings'::regclass AND tgname = 'trg_sync_room_status_booking';
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'trg_sync_room_status_booking has incompatible definition: <missing>';
    ELSIF found_definition = trig_old THEN
        EXECUTE 'DROP TRIGGER trg_sync_room_status_booking ON public.bookings';
        EXECUTE trig_current;
    ELSIF found_definition <> trig_current THEN
        RAISE EXCEPTION 'trg_sync_room_status_booking has incompatible definition: %', found_definition;
    END IF;
END;
$room_sync_and_transitions$;

-- 12. Reporting & occupancy views
DO $reporting_views$
DECLARE
    found_definition text;
    v_booking_summary_cur constant text := $vbs_c$ SELECT b.id,
    b.uuid,
    b.booking_number,
    b.status,
    b.payment_status,
    g.nick_name AS guest_name,
    g.email AS guest_email,
    g.phone AS guest_phone,
    r.room_number,
    rt.name AS room_type,
    b.check_in_date,
    b.check_out_date,
    b.nights,
    b.adults,
    b.children,
    b.total_amount,
    b.currency,
    b.source,
    b.is_tourist,
    b.tourism_tax_amount,
    b.extra_bed_count,
    b.extra_bed_charge,
    b.room_card_deposit,
    b.late_checkout_penalty,
    b.payment_method,
    b.created_at,
        CASE
            WHEN b.status::text = ANY (ARRAY['checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text]) THEN 'In House'::text
            WHEN b.check_in_date = CURRENT_DATE THEN 'Arriving Today'::text
            WHEN b.check_out_date = CURRENT_DATE THEN 'Departing Today'::text
            WHEN b.check_in_date > CURRENT_DATE THEN 'Future'::text
            ELSE 'Past'::text
        END AS booking_category
   FROM bookings b
     JOIN guests g ON b.guest_id = g.id
     JOIN rooms r ON b.room_id = r.id
     JOIN room_types rt ON r.room_type_id = rt.id;$vbs_c$;
    v_booking_summary_old constant text := $vbs_o$ SELECT b.id,
    b.uuid,
    b.booking_number,
    b.status,
    b.payment_status,
    g.nick_name AS guest_name,
    g.email AS guest_email,
    g.phone AS guest_phone,
    r.room_number,
    rt.name AS room_type,
    b.check_in_date,
    b.check_out_date,
    b.nights,
    b.adults,
    b.children,
    b.total_amount,
    b.currency,
    b.source,
    b.is_tourist,
    b.tourism_tax_amount,
    b.extra_bed_count,
    b.extra_bed_charge,
    b.room_card_deposit,
    b.late_checkout_penalty,
    b.payment_method,
    b.created_at,
        CASE
            WHEN b.status::text = 'checked_in'::text THEN 'In House'::text
            WHEN b.check_in_date = CURRENT_DATE THEN 'Arriving Today'::text
            WHEN b.check_out_date = CURRENT_DATE THEN 'Departing Today'::text
            WHEN b.check_in_date > CURRENT_DATE THEN 'Future'::text
            ELSE 'Past'::text
        END AS booking_category
   FROM bookings b
     JOIN guests g ON b.guest_id = g.id
     JOIN rooms r ON b.room_id = r.id
     JOIN room_types rt ON r.room_type_id = rt.id;$vbs_o$;

    v_daily_arrivals_cur constant text := $vda_c$ SELECT check_in_date AS date,
    count(*) AS total_arrivals,
    sum(adults + children) AS total_guests,
    array_agg(booking_number ORDER BY check_in_date) AS booking_numbers
   FROM bookings b
  WHERE (status::text = ANY (ARRAY['confirmed'::character varying::text, 'checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text, 'partial_complimentary'::character varying::text, 'fully_complimentary'::character varying::text])) AND check_in_date >= CURRENT_DATE
  GROUP BY check_in_date
  ORDER BY check_in_date;$vda_c$;
    v_daily_arrivals_old constant text := $vda_o$ SELECT check_in_date AS date,
    count(*) AS total_arrivals,
    sum(adults + children) AS total_guests,
    array_agg(booking_number ORDER BY check_in_date) AS booking_numbers
   FROM bookings b
  WHERE (status::text = ANY (ARRAY['confirmed'::character varying::text, 'checked_in'::character varying::text])) AND check_in_date >= CURRENT_DATE
  GROUP BY check_in_date
  ORDER BY check_in_date;$vda_o$;

    v_daily_departures_cur constant text := $vdd_c$ SELECT check_out_date AS date,
    count(*) AS total_departures,
    sum(adults + children) AS total_guests,
    array_agg(booking_number ORDER BY check_out_date) AS booking_numbers
   FROM bookings b
  WHERE (status::text = ANY (ARRAY['confirmed'::character varying::text, 'checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text, 'partial_complimentary'::character varying::text, 'fully_complimentary'::character varying::text])) AND check_out_date >= CURRENT_DATE
  GROUP BY check_out_date
  ORDER BY check_out_date;$vdd_c$;
    v_daily_departures_old constant text := $vdd_o$ SELECT check_out_date AS date,
    count(*) AS total_departures,
    sum(adults + children) AS total_guests,
    array_agg(booking_number ORDER BY check_out_date) AS booking_numbers
   FROM bookings b
  WHERE (status::text = ANY (ARRAY['confirmed'::character varying::text, 'checked_in'::character varying::text])) AND check_out_date >= CURRENT_DATE
  GROUP BY check_out_date
  ORDER BY check_out_date;$vdd_o$;

    v_occ_rt_cur constant text := $vort_c$ SELECT rt.id AS room_type_id,
    rt.name AS room_type_name,
    rt.max_occupancy AS capacity_per_room,
    count(r.id) AS total_rooms,
    count(r.id) FILTER (WHERE b.id IS NOT NULL) AS occupied_rooms,
    round(count(r.id) FILTER (WHERE b.id IS NOT NULL)::numeric / NULLIF(count(r.id), 0)::numeric * 100::numeric, 1) AS room_occupancy_rate,
    COALESCE(sum(COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0)), 0::bigint) AS total_guests,
    count(r.id) * rt.max_occupancy AS total_capacity,
        CASE
            WHEN (count(r.id) * rt.max_occupancy) > 0 THEN round(COALESCE(sum(COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0)), 0::bigint)::numeric / NULLIF(count(r.id) * rt.max_occupancy, 0)::numeric * 100::numeric, 1)
            ELSE NULL::numeric
        END AS guest_occupancy_rate
   FROM room_types rt
     LEFT JOIN rooms r ON r.room_type_id = rt.id AND r.is_active = true
     LEFT JOIN bookings b ON r.id = b.room_id AND (b.status::text = ANY (ARRAY['checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text])) AND CURRENT_DATE >= b.check_in_date AND CURRENT_DATE <= b.check_out_date
  WHERE rt.is_active = true
  GROUP BY rt.id, rt.name, rt.max_occupancy;$vort_c$;
    v_occ_rt_old constant text := $vort_o$ SELECT rt.id AS room_type_id,
    rt.name AS room_type_name,
    rt.max_occupancy AS capacity_per_room,
    count(r.id) AS total_rooms,
    count(r.id) FILTER (WHERE b.id IS NOT NULL) AS occupied_rooms,
    round(count(r.id) FILTER (WHERE b.id IS NOT NULL)::numeric / NULLIF(count(r.id), 0)::numeric * 100::numeric, 1) AS room_occupancy_rate,
    COALESCE(sum(COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0)), 0::bigint) AS total_guests,
    count(r.id) * rt.max_occupancy AS total_capacity,
        CASE
            WHEN (count(r.id) * rt.max_occupancy) > 0 THEN round(COALESCE(sum(COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0)), 0::bigint)::numeric / NULLIF(count(r.id) * rt.max_occupancy, 0)::numeric * 100::numeric, 1)
            ELSE NULL::numeric
        END AS guest_occupancy_rate
   FROM room_types rt
     LEFT JOIN rooms r ON r.room_type_id = rt.id AND r.is_active = true
     LEFT JOIN bookings b ON r.id = b.room_id AND b.status::text = 'checked_in'::text AND CURRENT_DATE >= b.check_in_date AND CURRENT_DATE <= b.check_out_date
  WHERE rt.is_active = true
  GROUP BY rt.id, rt.name, rt.max_occupancy;$vort_o$;

    v_occ_stats_cur constant text := $vos_c$ SELECT date_trunc('day'::text, CURRENT_TIMESTAMP) AS date,
    count(DISTINCT r.id) AS total_rooms,
    count(DISTINCT
        CASE
            WHEN b.status::text = ANY (ARRAY['checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text]) THEN r.id
            ELSE NULL::bigint
        END) AS occupied_rooms,
    count(DISTINCT
        CASE
            WHEN r.status::text = 'available'::text THEN r.id
            ELSE NULL::bigint
        END) AS available_rooms,
    round(count(DISTINCT
        CASE
            WHEN b.status::text = ANY (ARRAY['checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text]) THEN r.id
            ELSE NULL::bigint
        END)::numeric / NULLIF(count(DISTINCT r.id), 0)::numeric * 100::numeric, 2) AS occupancy_percentage
   FROM rooms r
     LEFT JOIN bookings b ON r.id = b.room_id AND (b.status::text = ANY (ARRAY['checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text])) AND CURRENT_DATE >= b.check_in_date AND CURRENT_DATE <= b.check_out_date
  WHERE r.is_active = true;$vos_c$;
    v_occ_stats_old constant text := $vos_o$ SELECT date_trunc('day'::text, CURRENT_TIMESTAMP) AS date,
    count(DISTINCT r.id) AS total_rooms,
    count(DISTINCT
        CASE
            WHEN b.status::text = 'checked_in'::text THEN r.id
            ELSE NULL::bigint
        END) AS occupied_rooms,
    count(DISTINCT
        CASE
            WHEN r.status::text = 'available'::text THEN r.id
            ELSE NULL::bigint
        END) AS available_rooms,
    round(count(DISTINCT
        CASE
            WHEN b.status::text = 'checked_in'::text THEN r.id
            ELSE NULL::bigint
        END)::numeric / NULLIF(count(DISTINCT r.id), 0)::numeric * 100::numeric, 2) AS occupancy_percentage
   FROM rooms r
     LEFT JOIN bookings b ON r.id = b.room_id AND b.status::text = 'checked_in'::text AND CURRENT_DATE >= b.check_in_date AND CURRENT_DATE <= b.check_out_date
  WHERE r.is_active = true;$vos_o$;

    v_rev_cur constant text := $vrev_c$ SELECT date_trunc('month'::text, check_in_date::timestamp with time zone) AS month,
    count(*) AS total_bookings,
    sum(total_amount) AS total_revenue,
    sum(subtotal) AS room_revenue,
    sum(tax_amount) AS tax_collected,
    avg(total_amount) AS average_booking_value,
    sum(
        CASE
            WHEN payment_status::text = 'paid'::text THEN total_amount
            ELSE 0::numeric
        END) AS collected_revenue
   FROM bookings b
  WHERE status::text <> ALL (ARRAY['pending'::character varying::text, 'pending_payment'::character varying::text, 'pending_confirmation'::character varying::text, 'voided'::character varying::text, 'comp_void'::character varying::text, 'no_show'::character varying::text])
  GROUP BY (date_trunc('month'::text, check_in_date::timestamp with time zone))
  ORDER BY (date_trunc('month'::text, check_in_date::timestamp with time zone)) DESC;$vrev_c$;
    v_rev_old constant text := $vrev_o$ SELECT date_trunc('month'::text, check_in_date::timestamp with time zone) AS month,
    count(*) AS total_bookings,
    sum(total_amount) AS total_revenue,
    sum(subtotal) AS room_revenue,
    sum(tax_amount) AS tax_collected,
    avg(total_amount) AS average_booking_value,
    sum(
        CASE
            WHEN payment_status::text = 'paid'::text THEN total_amount
            ELSE 0::numeric
        END) AS collected_revenue
   FROM bookings b
  WHERE status::text <> ALL (ARRAY['voided'::character varying::text, 'no_show'::character varying::text])
  GROUP BY (date_trunc('month'::text, check_in_date::timestamp with time zone))
  ORDER BY (date_trunc('month'::text, check_in_date::timestamp with time zone)) DESC;$vrev_o$;

    v_rco_cur constant text := $vrco_c$ SELECT r.id AS room_id,
    r.room_number,
    r.room_type_id,
    rt.name AS room_type_name,
    rt.max_occupancy,
    r.status AS room_status,
    COALESCE(b.adults, 0) AS current_adults,
    COALESCE(b.children, 0) AS current_children,
    COALESCE(b.infants, 0) AS current_infants,
    COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0) AS current_total_guests,
        CASE
            WHEN rt.max_occupancy > 0 THEN round((COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0))::numeric / rt.max_occupancy::numeric * 100::numeric, 1)
            ELSE NULL::numeric
        END AS occupancy_percentage,
    b.id AS current_booking_id,
    b.booking_number AS current_booking_number,
    b.guest_id AS current_guest_id,
    b.check_in_date,
    b.check_out_date,
        CASE
            WHEN b.id IS NOT NULL THEN true
            ELSE false
        END AS is_occupied
   FROM rooms r
     LEFT JOIN room_types rt ON r.room_type_id = rt.id
     LEFT JOIN bookings b ON r.id = b.room_id AND (b.status::text = ANY (ARRAY['checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text])) AND CURRENT_DATE >= b.check_in_date AND CURRENT_DATE <= b.check_out_date
  WHERE r.is_active = true;$vrco_c$;
    v_rco_old constant text := $vrco_o$ SELECT r.id AS room_id,
    r.room_number,
    r.room_type_id,
    rt.name AS room_type_name,
    rt.max_occupancy,
    r.status AS room_status,
    COALESCE(b.adults, 0) AS current_adults,
    COALESCE(b.children, 0) AS current_children,
    COALESCE(b.infants, 0) AS current_infants,
    COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0) AS current_total_guests,
        CASE
            WHEN rt.max_occupancy > 0 THEN round((COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0))::numeric / rt.max_occupancy::numeric * 100::numeric, 1)
            ELSE NULL::numeric
        END AS occupancy_percentage,
    b.id AS current_booking_id,
    b.booking_number AS current_booking_number,
    b.guest_id AS current_guest_id,
    b.check_in_date,
    b.check_out_date,
        CASE
            WHEN b.id IS NOT NULL THEN true
            ELSE false
        END AS is_occupied
   FROM rooms r
     LEFT JOIN room_types rt ON r.room_type_id = rt.id
     LEFT JOIN bookings b ON r.id = b.room_id AND b.status::text = 'checked_in'::text AND CURRENT_DATE >= b.check_in_date AND CURRENT_DATE <= b.check_out_date
  WHERE r.is_active = true;$vrco_o$;
BEGIN
    SELECT pg_get_viewdef('public.booking_summary'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'booking_summary view missing';
    ELSIF found_definition = v_booking_summary_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.booking_summary AS ' || v_booking_summary_cur;
    ELSIF found_definition <> v_booking_summary_cur THEN RAISE EXCEPTION 'booking_summary view has incompatible definition: %', found_definition;
    END IF;

    SELECT pg_get_viewdef('public.daily_arrivals'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'daily_arrivals view missing';
    ELSIF found_definition = v_daily_arrivals_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.daily_arrivals AS ' || v_daily_arrivals_cur;
    ELSIF found_definition <> v_daily_arrivals_cur THEN RAISE EXCEPTION 'daily_arrivals view has incompatible definition: %', found_definition;
    END IF;

    SELECT pg_get_viewdef('public.daily_departures'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'daily_departures view missing';
    ELSIF found_definition = v_daily_departures_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.daily_departures AS ' || v_daily_departures_cur;
    ELSIF found_definition <> v_daily_departures_cur THEN RAISE EXCEPTION 'daily_departures view has incompatible definition: %', found_definition;
    END IF;

    SELECT pg_get_viewdef('public.occupancy_by_room_type'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'occupancy_by_room_type view missing';
    ELSIF found_definition = v_occ_rt_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.occupancy_by_room_type AS ' || v_occ_rt_cur;
    ELSIF found_definition <> v_occ_rt_cur THEN RAISE EXCEPTION 'occupancy_by_room_type view has incompatible definition: %', found_definition;
    END IF;

    SELECT pg_get_viewdef('public.occupancy_stats'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'occupancy_stats view missing';
    ELSIF found_definition = v_occ_stats_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.occupancy_stats AS ' || v_occ_stats_cur;
    ELSIF found_definition <> v_occ_stats_cur THEN RAISE EXCEPTION 'occupancy_stats view has incompatible definition: %', found_definition;
    END IF;

    SELECT pg_get_viewdef('public.revenue_summary'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'revenue_summary view missing';
    ELSIF found_definition = v_rev_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.revenue_summary AS ' || v_rev_cur;
    ELSIF found_definition <> v_rev_cur THEN RAISE EXCEPTION 'revenue_summary view has incompatible definition: %', found_definition;
    END IF;

    SELECT pg_get_viewdef('public.room_current_occupancy'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'room_current_occupancy view missing';
    ELSIF found_definition = v_rco_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.room_current_occupancy AS ' || v_rco_cur;
    ELSIF found_definition <> v_rco_cur THEN RAISE EXCEPTION 'room_current_occupancy view has incompatible definition: %', found_definition;
    END IF;
END;
$reporting_views$;

-- 13. Align loyalty route access policy permissions with loyalty:read / loyalty:manage
UPDATE public.route_access_policies
   SET required_permissions = '["loyalty:read","loyalty:manage","analytics:read"]'::jsonb,
       nav_permissions = '["loyalty:read","loyalty:manage","analytics:read"]'::jsonb,
       updated_at = CURRENT_TIMESTAMP
 WHERE route_id = 'loyalty'
   AND (
       required_permissions = '["analytics:read"]'::jsonb
       OR nav_permissions = '["analytics:read"]'::jsonb
   );
