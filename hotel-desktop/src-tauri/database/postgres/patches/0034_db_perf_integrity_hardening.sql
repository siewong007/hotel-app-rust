-- Database performance, index hygiene, routine hardening, view accuracy,
-- overlap integrity, and reference lookup convergence.
--
-- Converges both the pre-0034 (patch 0033 head) database and the updated V1
-- baseline cleanly with exact definition guards on every previously-guarded
-- object.

-- 1. Drop verified redundant / duplicate indexes
DROP INDEX IF EXISTS public.idx_bookings_room;
DROP INDEX IF EXISTS public.idx_ekyc_guest;
DROP INDEX IF EXISTS public.uq_guest_preferences_key;
DROP INDEX IF EXISTS public.idx_guest_preferences_guest_id;
DROP INDEX IF EXISTS public.idx_guest_credits_guest_id;
DROP INDEX IF EXISTS public.idx_guest_notes_guest_id;
DROP INDEX IF EXISTS public.idx_housekeeping_room;
DROP INDEX IF EXISTS public.idx_loyalty_memberships_guest;
DROP INDEX IF EXISTS public.idx_loyalty_tiers_program;
DROP INDEX IF EXISTS public.idx_posted_nights_booking;
DROP INDEX IF EXISTS public.idx_night_audit_posted_nights_date_brin;
DROP INDEX IF EXISTS public.idx_audit_logs_created_at_brin;
DROP INDEX IF EXISTS public.idx_refresh_tokens_token_hash;
DROP INDEX IF EXISTS public.idx_role_permissions_role_id;
DROP INDEX IF EXISTS public.idx_room_rates_plan;
DROP INDEX IF EXISTS public.idx_room_type_amenities_type;
DROP INDEX IF EXISTS public.idx_user_guests_user_id;
DROP INDEX IF EXISTS public.idx_user_permissions_user_id;
DROP INDEX IF EXISTS public.idx_user_roles_user_id;
DROP INDEX IF EXISTS public.idx_vouchers_promotion_guest;

-- 2. Add high-impact composite, partial, pattern-ops, expression, and GIN indexes
CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created ON public.audit_logs USING btree (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource_created ON public.audit_logs USING btree (resource_type, resource_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_created ON public.audit_logs USING btree (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_booking_groups_portal_request_id ON public.booking_groups USING btree (portal_request_id) WHERE (portal_request_id IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_bookings_check_out_status ON public.bookings USING btree (check_out_date, status);
CREATE INDEX IF NOT EXISTS idx_bookings_created_by ON public.bookings USING btree (created_by) WHERE (created_by IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_bookings_guest_dates ON public.bookings USING btree (guest_id, check_in_date DESC, check_out_date DESC);
CREATE INDEX IF NOT EXISTS idx_bookings_pending_payment_created ON public.bookings USING btree (created_at) WHERE ((status)::text = 'pending_payment'::text);
CREATE INDEX IF NOT EXISTS idx_bookings_portal_request_id ON public.bookings USING btree (portal_request_id) WHERE (portal_request_id IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_customer_ledger_payments_idempotency_key ON public.customer_ledger_payments USING btree (idempotency_key) WHERE ((idempotency_key IS NOT NULL) AND (btrim((idempotency_key)::text) <> ''::text));
CREATE INDEX IF NOT EXISTS idx_customer_ledger_payments_payment_date ON public.customer_ledger_payments USING btree (payment_date);
CREATE INDEX IF NOT EXISTS idx_customer_ledgers_company_created ON public.customer_ledgers USING btree (company_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_customer_ledgers_inv_pattern ON public.customer_ledgers USING btree (invoice_number varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_customer_ledgers_status_due ON public.customer_ledgers USING btree (status, due_date) WHERE (void_at IS NULL);
CREATE INDEX IF NOT EXISTS idx_ekyc_guest_effective_submitted ON public.ekyc_verifications USING btree (guest_id, COALESCE(submitted_at, created_at) DESC, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_ekyc_idempotency_keys_created_at ON public.ekyc_idempotency_keys USING btree (created_at);
CREATE INDEX IF NOT EXISTS idx_ekyc_status_submitted ON public.ekyc_verifications USING btree (status, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_guest_reviews_booking_id ON public.guest_reviews USING btree (booking_id) WHERE (booking_id IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_guests_lower_email ON public.guests USING btree (lower(btrim((email)::text))) WHERE ((deleted_at IS NULL) AND (email IS NOT NULL));
CREATE INDEX IF NOT EXISTS idx_guests_phone_digits ON public.guests USING btree (regexp_replace(COALESCE((phone)::text, ''::text), '[^0-9]'::text, ''::text, 'g'::text)) WHERE ((deleted_at IS NULL) AND (phone IS NOT NULL));
CREATE INDEX IF NOT EXISTS idx_guests_tags_gin ON public.guests USING gin (tags);
CREATE INDEX IF NOT EXISTS idx_housekeeping_tasks_open_room ON public.housekeeping_tasks USING btree (room_id, created_at DESC) WHERE ((status)::text = ANY ((ARRAY['pending'::character varying, 'in_progress'::character varying])::text[]));
CREATE INDEX IF NOT EXISTS idx_housekeeping_tasks_status_created ON public.housekeeping_tasks USING btree (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_invoices_created_by_id ON public.invoices USING btree (created_by, id DESC) WHERE (created_by IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_invoices_invoice_number_pattern ON public.invoices USING btree (invoice_number varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_invoices_open_balance ON public.invoices USING btree (((total_amount - paid_amount)) DESC) WHERE (((status)::text = ANY ((ARRAY['issued'::character varying, 'overdue'::character varying])::text[])) AND (total_amount > paid_amount));
CREATE INDEX IF NOT EXISTS idx_loyalty_accounts_current_tier_id ON public.loyalty_accounts USING btree (current_tier_id);
CREATE INDEX IF NOT EXISTS idx_loyalty_transactions_member_delta ON public.loyalty_transactions USING btree (member_id) INCLUDE (available_delta);
CREATE INDEX IF NOT EXISTS idx_loyalty_transactions_payment_id ON public.loyalty_transactions USING btree (payment_id) WHERE (payment_id IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_maintenance_tickets_number_pattern ON public.maintenance_tickets USING btree (ticket_number varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_maintenance_tickets_status_created ON public.maintenance_tickets USING btree (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_receipt_requests_unfulfilled ON public.payment_receipt_requests USING btree (requested_at) WHERE (uploaded_at IS NULL);
CREATE INDEX IF NOT EXISTS idx_payments_booking_status_covering ON public.payments USING btree (booking_id, status) INCLUDE (amount, payment_type);
CREATE INDEX IF NOT EXISTS idx_payments_group_payment_ref ON public.payments USING btree (((metadata ->> 'group_payment_ref'::text))) WHERE (metadata ? 'group_payment_ref'::text);
CREATE INDEX IF NOT EXISTS idx_payments_pending_method_created ON public.payments USING btree (payment_method, created_at, id) WHERE ((status)::text = 'pending'::text);
CREATE INDEX IF NOT EXISTS idx_payments_refunded_at ON public.payments USING btree (refunded_at) WHERE (refund_amount IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_rate_limit_buckets_window_start ON public.rate_limit_buckets USING btree (window_start);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked_at ON public.refresh_tokens USING btree (revoked_at) WHERE (revoked_at IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_room_events_room_created ON public.room_events USING btree (room_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_room_history_room_created ON public.room_history USING btree (room_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_room_rates_room_type_effective ON public.room_rates USING btree (room_type_id, effective_from, effective_to);
CREATE INDEX IF NOT EXISTS idx_support_action_idempotency_created ON public.support_action_idempotency_keys USING btree (created_at);
CREATE INDEX IF NOT EXISTS idx_support_guest_req_idempotency_created ON public.support_guest_request_idempotency_keys USING btree (created_at);
CREATE INDEX IF NOT EXISTS idx_two_factor_challenges_expires_at ON public.two_factor_challenges USING btree (expires_at);
CREATE INDEX IF NOT EXISTS idx_users_lower_email ON public.users USING btree (lower((email)::text)) WHERE (deleted_at IS NULL);
CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_booking_id ON public.voucher_redemptions USING btree (booking_id);
CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_promotion_status ON public.voucher_redemptions USING btree (promotion_id, status);
CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_voucher_id ON public.voucher_redemptions USING btree (voucher_id);
CREATE INDEX IF NOT EXISTS idx_vouchers_lower_code ON public.vouchers USING btree (lower((code)::text));
CREATE INDEX IF NOT EXISTS idx_vouchers_status_expires ON public.vouchers USING btree (status, expires_at);

-- 3. Unguarded trigger/function fixes (mirrored identically in 0001_v1_baseline.sql)
CREATE OR REPLACE FUNCTION public.enforce_booking_tourism_tax() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $_$
DECLARE
    v_is_tourist BOOLEAN := false;
    v_tourism_tax_rate NUMERIC := 10;
    v_billable_nights INTEGER := 1;
BEGIN
    SELECT COALESCE(
        (
            SELECT CASE
                WHEN trim(value) ~ '^[0-9]+(\.[0-9]+)?$' AND trim(value)::numeric >= 0
                    THEN trim(value)::numeric
                ELSE NULL
            END
            FROM system_settings
            WHERE key = 'tourism_tax_rate'
            LIMIT 1
        ),
        10
    )
    INTO v_tourism_tax_rate;

    SELECT COALESCE(g.tourism_type::text = 'foreign', false)
    INTO v_is_tourist
    FROM guests g
    WHERE g.id = NEW.guest_id;

    v_billable_nights := GREATEST((NEW.check_out_date - NEW.check_in_date), 1);

    NEW.is_tourist := COALESCE(v_is_tourist, false);
    NEW.tourism_tax_amount := CASE
        WHEN NEW.is_tourist THEN v_tourism_tax_rate * v_billable_nights
        ELSE 0
    END;

    RETURN NEW;
END;
$_$;

CREATE OR REPLACE FUNCTION public.validate_booking_occupancy() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
DECLARE v_max_occupancy INTEGER; v_total_guests INTEGER;
BEGIN
    SELECT rt.max_occupancy INTO v_max_occupancy FROM rooms r JOIN room_types rt ON r.room_type_id = rt.id WHERE r.id = NEW.room_id;
    v_total_guests := COALESCE(NEW.adults, 1) + COALESCE(NEW.children, 0);
    IF v_max_occupancy IS NOT NULL AND v_total_guests > v_max_occupancy THEN
        RAISE EXCEPTION 'Total guests (%) exceeds room maximum occupancy (%)', v_total_guests, v_max_occupancy;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_validate_booking_occupancy ON public.bookings;
CREATE TRIGGER trigger_validate_booking_occupancy BEFORE INSERT OR UPDATE OF room_id, adults, children ON public.bookings FOR EACH ROW EXECUTE FUNCTION public.validate_booking_occupancy();

DROP TRIGGER IF EXISTS trg_sync_booking_payment_status ON public.payments;
CREATE TRIGGER trg_sync_booking_payment_status AFTER INSERT OR DELETE OR UPDATE OF booking_id, amount, status, payment_type, notes ON public.payments FOR EACH ROW EXECUTE FUNCTION public.sync_booking_payment_status();

-- 4. Sanity CHECK constraints (mirrored identically in 0001_v1_baseline.sql)
DO $checks_0034$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.guest_complimentary_credits'::regclass
          AND conname = 'guest_complimentary_credits_nights_non_negative'
    ) THEN
        ALTER TABLE public.guest_complimentary_credits
            ADD CONSTRAINT guest_complimentary_credits_nights_non_negative CHECK ((nights_available >= 0));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.room_rates'::regclass
          AND conname = 'room_rates_price_non_negative'
    ) THEN
        ALTER TABLE public.room_rates
            ADD CONSTRAINT room_rates_price_non_negative CHECK ((price >= (0)::numeric));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.room_rates'::regclass
          AND conname = 'room_rates_effective_range'
    ) THEN
        ALTER TABLE public.room_rates
            ADD CONSTRAINT room_rates_effective_range CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.job_runs'::regclass
          AND conname = 'job_runs_status_check'
    ) THEN
        ALTER TABLE public.job_runs
            ADD CONSTRAINT job_runs_status_check CHECK (((status)::text = ANY ((ARRAY['ok'::character varying, 'error'::character varying])::text[])));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.job_runs'::regclass
          AND conname = 'job_runs_duration_non_negative'
    ) THEN
        ALTER TABLE public.job_runs
            ADD CONSTRAINT job_runs_duration_non_negative CHECK ((duration_ms >= 0));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.payments'::regclass
          AND conname = 'payments_amount_positive'
    ) THEN
        ALTER TABLE public.payments
            ADD CONSTRAINT payments_amount_positive CHECK ((amount > (0)::numeric));
    END IF;
END;
$checks_0034$;

-- 5. Same-day (hourly) room overlap exclusion fix (guarded on patch 0032's definition)
DO $overlap_0034$
DECLARE
    found_definition text;
    current_definition constant text := $ov34_cur$EXCLUDE USING gist (room_id WITH =, daterange(check_in_date, GREATEST(check_out_date, (check_in_date + 1)), '[)'::text) WITH &&) WHERE (((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('pending_payment'::character varying)::text, ('pending_confirmation'::character varying)::text, ('confirmed'::character varying)::text, ('checked_in'::character varying)::text, ('auto_checked_in'::character varying)::text, ('late_checkout'::character varying)::text, ('partial_complimentary'::character varying)::text, ('fully_complimentary'::character varying)::text])))$ov34_cur$;
    old_definition constant text := $ov34_old$EXCLUDE USING gist (room_id WITH =, daterange(check_in_date, check_out_date, '[)'::text) WITH &&) WHERE (((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('pending_payment'::character varying)::text, ('pending_confirmation'::character varying)::text, ('confirmed'::character varying)::text, ('checked_in'::character varying)::text, ('auto_checked_in'::character varying)::text, ('late_checkout'::character varying)::text, ('partial_complimentary'::character varying)::text, ('fully_complimentary'::character varying)::text])))$ov34_old$;
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
$overlap_0034$;

-- 6. sync_booking_payment_status() refund & no-op guard fix (guarded on patch 0032)
DO $sync_pay_0034$
DECLARE
    found_definition text;
    current_definition constant text := $sp34_cur$CREATE OR REPLACE FUNCTION public.sync_booking_payment_status()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
DECLARE
    v_booking_id BIGINT;
    v_settled NUMERIC;
    v_refunded NUMERIC;
    v_net_paid NUMERIC;
    v_new_status TEXT;
BEGIN
    v_booking_id := COALESCE(NEW.booking_id, OLD.booking_id);

    -- Money that settles the booking's charges minus room-charge refunds.
    -- Mirrors PaymentRepository::recompute_booking_payment_status.
    SELECT
        COALESCE(SUM(amount) FILTER (
            WHERE status = 'completed'
              AND COALESCE(payment_type, 'booking') NOT IN ('refund', 'deposit', 'deposit_forfeited', 'service', 'damage')
        ), 0),
        COALESCE(SUM(amount) FILTER (
            WHERE status <> 'void'
              AND COALESCE(payment_type, 'booking') = 'refund'
              AND COALESCE(notes, '') LIKE 'Room charge refunded%'
        ), 0)
      INTO v_settled, v_refunded
      FROM public.payments
     WHERE booking_id = v_booking_id;

    v_net_paid := v_settled - v_refunded;

    SELECT CASE
        WHEN b.status IN ('voided', 'comp_void') THEN 'void'
        WHEN (b.total_amount + COALESCE(b.tourism_billable_amount, 0)
                + COALESCE(b.extra_bed_charge, 0)) <= 0 THEN 'paid'
        WHEN v_net_paid >= (b.total_amount + COALESCE(b.tourism_billable_amount, 0)
                + COALESCE(b.extra_bed_charge, 0)) THEN 'paid'
        WHEN v_net_paid > 0 THEN 'partial'
        ELSE 'unpaid'
    END INTO v_new_status
    FROM public.bookings b
    WHERE b.id = v_booking_id;

    IF v_new_status IS NOT NULL THEN
        UPDATE public.bookings
           SET payment_status = v_new_status,
               updated_at = CURRENT_TIMESTAMP
         WHERE id = v_booking_id
           AND payment_status IS DISTINCT FROM v_new_status;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$function$
$sp34_cur$;
    old_definition constant text := $sp34_old$CREATE OR REPLACE FUNCTION public.sync_booking_payment_status()
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
$sp34_old$;
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
$sync_pay_0034$;

-- 7. room_current_occupancy & occupancy_by_room_type turnover deduplication (guarded on patch 0032)
DO $occ_views_0034$
DECLARE
    found_definition text;
    v_rco_cur constant text := $vrco34_c$ SELECT r.id AS room_id,
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
     LEFT JOIN LATERAL ( SELECT b_1.id,
            b_1.booking_number,
            b_1.guest_id,
            b_1.check_in_date,
            b_1.check_out_date,
            b_1.adults,
            b_1.children,
            b_1.infants
           FROM bookings b_1
          WHERE b_1.room_id = r.id AND (b_1.status::text = ANY (ARRAY['checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text])) AND b_1.check_in_date <= CURRENT_DATE
          ORDER BY (b_1.check_out_date > CURRENT_DATE) DESC, b_1.check_in_date DESC, b_1.id DESC
         LIMIT 1) b ON true
  WHERE r.is_active = true;$vrco34_c$;
    v_rco_old constant text := $vrco34_o$ SELECT r.id AS room_id,
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
  WHERE r.is_active = true;$vrco34_o$;
    v_ort_cur constant text := $vort34_c$ SELECT rt.id AS room_type_id,
    rt.name AS room_type_name,
    rt.max_occupancy AS capacity_per_room,
    count(DISTINCT r.id) AS total_rooms,
    count(DISTINCT r.id) FILTER (WHERE b.id IS NOT NULL) AS occupied_rooms,
    round(count(DISTINCT r.id) FILTER (WHERE b.id IS NOT NULL)::numeric / NULLIF(count(DISTINCT r.id), 0)::numeric * 100::numeric, 1) AS room_occupancy_rate,
    COALESCE(sum(COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0)), 0::bigint) AS total_guests,
    count(DISTINCT r.id) * rt.max_occupancy AS total_capacity,
        CASE
            WHEN (count(DISTINCT r.id) * rt.max_occupancy) > 0 THEN round(COALESCE(sum(COALESCE(b.adults, 0) + COALESCE(b.children, 0) + COALESCE(b.infants, 0)), 0::bigint)::numeric / NULLIF(count(DISTINCT r.id) * rt.max_occupancy, 0)::numeric * 100::numeric, 1)
            ELSE NULL::numeric
        END AS guest_occupancy_rate
   FROM room_types rt
     LEFT JOIN rooms r ON r.room_type_id = rt.id AND r.is_active = true
     LEFT JOIN LATERAL ( SELECT b_1.id,
            b_1.adults,
            b_1.children,
            b_1.infants
           FROM bookings b_1
          WHERE b_1.room_id = r.id AND (b_1.status::text = ANY (ARRAY['checked_in'::character varying::text, 'auto_checked_in'::character varying::text, 'late_checkout'::character varying::text])) AND b_1.check_in_date <= CURRENT_DATE
          ORDER BY (b_1.check_out_date > CURRENT_DATE) DESC, b_1.check_in_date DESC, b_1.id DESC
         LIMIT 1) b ON true
  WHERE rt.is_active = true
  GROUP BY rt.id, rt.name, rt.max_occupancy;$vort34_c$;
    v_ort_old constant text := $vort34_o$ SELECT rt.id AS room_type_id,
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
  GROUP BY rt.id, rt.name, rt.max_occupancy;$vort34_o$;
BEGIN
    SELECT pg_get_viewdef('public.room_current_occupancy'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'room_current_occupancy view missing';
    ELSIF found_definition = v_rco_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.room_current_occupancy AS ' || v_rco_cur;
    ELSIF found_definition <> v_rco_cur THEN RAISE EXCEPTION 'room_current_occupancy view has incompatible definition: %', found_definition;
    END IF;

    SELECT pg_get_viewdef('public.occupancy_by_room_type'::regclass, true) INTO found_definition;
    IF found_definition IS NULL THEN RAISE EXCEPTION 'occupancy_by_room_type view missing';
    ELSIF found_definition = v_ort_old THEN EXECUTE 'CREATE OR REPLACE VIEW public.occupancy_by_room_type AS ' || v_ort_cur;
    ELSIF found_definition <> v_ort_cur THEN RAISE EXCEPTION 'occupancy_by_room_type view has incompatible definition: %', found_definition;
    END IF;
END;
$occ_views_0034$;

-- 8. Audit partition concurrency locks (guarded on 0021 and 0024 respectively)
DO $audit_part_0034$
DECLARE
    found_definition text;
    current_definition constant text := $ap34_cur$CREATE OR REPLACE FUNCTION public.ensure_audit_logs_partition(p_month date)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
DECLARE
    start_date date := date_trunc('month', p_month)::date;
    end_date   date := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
    part_name  text := format('audit_logs_%s', to_char(start_date, 'YYYY_MM'));
    late_month boolean;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('audit_logs_partition:' || to_char(start_date, 'YYYY_MM')));
    LOCK TABLE public.audit_logs_default IN SHARE ROW EXCLUSIVE MODE;
    -- Schema-qualify the DDL: the pinned search_path puts pg_catalog first, so
    -- an unqualified CREATE TABLE would (illegally) target the system catalog.
    IF NOT EXISTS (
        SELECT 1 FROM pg_class
        WHERE relname = part_name AND relnamespace = 'public'::regnamespace
    ) THEN
        -- A month created late already has rows in the DEFAULT partition, and
        -- PostgreSQL will not create a partition over them. Detach the default,
        -- create the month, move its rows across unchanged, and reattach: what
        -- ALTER TABLE ... SPLIT PARTITION did until PostgreSQL 19 Beta 4
        -- reverted it. DETACH holds ACCESS EXCLUSIVE on audit_logs until the
        -- calling transaction ends, so no session sees a half-moved month.
        late_month := EXISTS (
            SELECT 1
            FROM public.audit_logs_default
            WHERE created_at >= start_date::timestamptz
              AND created_at < end_date::timestamptz
        );
        IF late_month THEN
            EXECUTE 'ALTER TABLE public.audit_logs DETACH PARTITION public.audit_logs_default';
        END IF;
        EXECUTE format(
            'CREATE TABLE public.%I PARTITION OF public.audit_logs FOR VALUES FROM (%L) TO (%L)',
            part_name, start_date, end_date
        );
        IF late_month THEN
            WITH moved AS (
                DELETE FROM public.audit_logs_default
                WHERE created_at >= start_date::timestamptz
                  AND created_at < end_date::timestamptz
                RETURNING *
            )
            INSERT INTO public.audit_logs OVERRIDING SYSTEM VALUE
            SELECT * FROM moved;
            EXECUTE 'ALTER TABLE public.audit_logs ATTACH PARTITION public.audit_logs_default DEFAULT';
        END IF;
    END IF;
END;
$function$
$ap34_cur$;
    old_definition constant text := $ap34_old$CREATE OR REPLACE FUNCTION public.ensure_audit_logs_partition(p_month date)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
DECLARE
    start_date date := date_trunc('month', p_month)::date;
    end_date   date := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
    part_name  text := format('audit_logs_%s', to_char(start_date, 'YYYY_MM'));
    late_month boolean;
BEGIN
    -- Schema-qualify the DDL: the pinned search_path puts pg_catalog first, so
    -- an unqualified CREATE TABLE would (illegally) target the system catalog.
    IF NOT EXISTS (
        SELECT 1 FROM pg_class
        WHERE relname = part_name AND relnamespace = 'public'::regnamespace
    ) THEN
        -- A month created late already has rows in the DEFAULT partition, and
        -- PostgreSQL will not create a partition over them. Detach the default,
        -- create the month, move its rows across unchanged, and reattach: what
        -- ALTER TABLE ... SPLIT PARTITION did until PostgreSQL 19 Beta 4
        -- reverted it. DETACH holds ACCESS EXCLUSIVE on audit_logs until the
        -- calling transaction ends, so no session sees a half-moved month.
        late_month := EXISTS (
            SELECT 1
            FROM public.audit_logs_default
            WHERE created_at >= start_date::timestamptz
              AND created_at < end_date::timestamptz
        );
        IF late_month THEN
            EXECUTE 'ALTER TABLE public.audit_logs DETACH PARTITION public.audit_logs_default';
        END IF;
        EXECUTE format(
            'CREATE TABLE public.%I PARTITION OF public.audit_logs FOR VALUES FROM (%L) TO (%L)',
            part_name, start_date, end_date
        );
        IF late_month THEN
            -- One statement, so the rows inserted are exactly the rows removed.
            -- The DELETE runs on the detached table, which no append-only
            -- trigger guards, so app.allow_audit_mutation stays off. SELECT *
            -- is positional: audit_logs_default was created in the parent's
            -- column order, and ALTER TABLE on the parent changes both alike.
            WITH moved AS (
                DELETE FROM public.audit_logs_default
                WHERE created_at >= start_date::timestamptz
                  AND created_at < end_date::timestamptz
                RETURNING *
            )
            INSERT INTO public.audit_logs OVERRIDING SYSTEM VALUE
            SELECT * FROM moved;
            EXECUTE 'ALTER TABLE public.audit_logs ATTACH PARTITION public.audit_logs_default DEFAULT';
        END IF;
    END IF;
END;
$function$
$ap34_old$;
    function_body_hash text;
BEGIN
    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.ensure_audit_logs_partition(date)');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'ensure_audit_logs_partition(date) has incompatible definition: <missing>';
    ELSIF found_definition = old_definition THEN
        EXECUTE current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'ensure_audit_logs_partition(date) has incompatible definition: %', found_definition;
    END IF;

    SELECT md5(prosrc) INTO function_body_hash
    FROM pg_proc WHERE oid = 'public.maintain_audit_partition(date)'::regprocedure;
    IF function_body_hash IS NULL OR function_body_hash NOT IN ('562266b3b2accba8763a64804f2edf58', '924ba58757bbba8929f63c1a14d65f4c') THEN
        RAISE EXCEPTION 'Unexpected pre-existing maintain_audit_partition definition: %', COALESCE(function_body_hash, '<missing>');
    ELSIF function_body_hash = '562266b3b2accba8763a64804f2edf58' THEN
        CREATE OR REPLACE FUNCTION public.maintain_audit_partition(p_month date)
        RETURNS void LANGUAGE plpgsql SECURITY DEFINER
        SET search_path = pg_catalog, public, pg_temp
        AS $function$
DECLARE
    start_date date := date_trunc('month', p_month)::date;
    end_date date := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
    part_name text := format('audit_logs_%s', to_char(start_date, 'YYYY_MM'));
    late_month boolean;
    partition_row record;
BEGIN
    IF p_month IS NULL OR NOT isfinite(p_month) THEN
        RAISE EXCEPTION 'Invalid partition month' USING ERRCODE = '22023';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('audit_logs_partition:' || to_char(start_date, 'YYYY_MM')));
    LOCK TABLE public.audit_logs_default IN SHARE ROW EXCLUSIVE MODE;
    IF NOT EXISTS (
        SELECT 1 FROM pg_class
        WHERE relname = part_name AND relnamespace = 'public'::regnamespace
    ) THEN
        late_month := EXISTS (
            SELECT 1
            FROM public.audit_logs_default
            WHERE created_at >= start_date::timestamptz
              AND created_at < end_date::timestamptz
        );
        IF late_month THEN
            EXECUTE 'ALTER TABLE public.audit_logs DETACH PARTITION public.audit_logs_default';
        END IF;
        EXECUTE format(
            'CREATE TABLE public.%I PARTITION OF public.audit_logs FOR VALUES FROM (%L) TO (%L)',
            part_name, start_date, end_date
        );
        IF late_month THEN
            WITH moved AS (
                DELETE FROM public.audit_logs_default
                WHERE created_at >= start_date::timestamptz
                  AND created_at < end_date::timestamptz
                RETURNING *
            )
            INSERT INTO public.audit_logs OVERRIDING SYSTEM VALUE
            SELECT * FROM moved;
            EXECUTE 'ALTER TABLE public.audit_logs ATTACH PARTITION public.audit_logs_default DEFAULT';
        END IF;
    END IF;
    FOR partition_row IN
        SELECT partition_schema.nspname AS schema_name,
               partition_table.relname AS table_name
        FROM pg_inherits AS link
        JOIN pg_class AS partition_table ON partition_table.oid = link.inhrelid
        JOIN pg_namespace AS partition_schema
          ON partition_schema.oid = partition_table.relnamespace
        WHERE link.inhparent = 'public.audit_logs'::regclass
          AND NOT EXISTS (
              SELECT 1
              FROM pg_trigger AS guard
              WHERE guard.tgrelid = partition_table.oid
                AND guard.tgname = 'trg_audit_logs_no_truncate'
          )
        ORDER BY partition_table.relname
    LOOP
        EXECUTE format(
            'CREATE OR REPLACE TRIGGER trg_audit_logs_no_truncate BEFORE TRUNCATE ON %I.%I FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_audit_log_mutation()',
            partition_row.schema_name, partition_row.table_name
        );
    END LOOP;
END
$function$;
        REVOKE ALL ON FUNCTION public.maintain_audit_partition(date) FROM PUBLIC;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hotel_app') THEN
            GRANT EXECUTE ON FUNCTION public.maintain_audit_partition(date) TO hotel_app;
        END IF;
    END IF;
END;
$audit_part_0034$;

-- 9. validate_room_status_transition() quick existence check + pinned search_path (guarded on patch 0032)
DO $vrst_0034$
DECLARE
    found_definition text;
    current_definition constant text := $vrst34_cur$CREATE OR REPLACE FUNCTION public.validate_room_status_transition(p_room_id bigint, p_new_status character varying, p_user_id bigint DEFAULT NULL::bigint)
 RETURNS boolean
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
DECLARE
    v_current_status VARCHAR(20);
    v_is_allowed BOOLEAN;
BEGIN
    SELECT status INTO v_current_status FROM rooms WHERE id = p_room_id;
    IF v_current_status IS NULL THEN RAISE EXCEPTION 'Room % not found', p_room_id; END IF;
    IF v_current_status = p_new_status THEN RETURN true; END IF;

    -- Auto-seed transitions if table is empty
    IF NOT EXISTS (SELECT 1 FROM room_status_transitions LIMIT 1) THEN
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
$vrst34_cur$;
    old_definition constant text := $vrst34_old$CREATE OR REPLACE FUNCTION public.validate_room_status_transition(p_room_id bigint, p_new_status character varying, p_user_id bigint DEFAULT NULL::bigint)
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
$vrst34_old$;
BEGIN
    SELECT replace(pg_get_functiondef(oid), E'\r', '') INTO found_definition
    FROM pg_proc WHERE oid = to_regprocedure('public.validate_room_status_transition(bigint,character varying,bigint)');
    IF found_definition IS NULL THEN
        RAISE EXCEPTION 'validate_room_status_transition has incompatible definition: <missing>';
    ELSIF found_definition = old_definition THEN
        EXECUTE current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'validate_room_status_transition has incompatible definition: %', found_definition;
    END IF;
END;
$vrst_0034$;

-- 10. Reference lookup rows: ekyc_reason_codes (mirrored in seed.sql)
INSERT INTO public.ekyc_reason_codes (
    code, label, category, requires_details, customer_message_template, is_active
) VALUES
    ('blurry_document', 'Blurry or unreadable ID document', 'document', false, 'Your ID photo was too blurry to verify clearly. Please upload a sharp, well-lit photo of your document.', true),
    ('expired_document', 'Expired identification document', 'document', false, 'The identification document provided appears to be expired. Please submit a valid, unexpired ID or passport.', true),
    ('incomplete_document', 'Cropped or partially obscured document', 'document', false, 'Part of your ID document was cut off or covered. Please upload the full document with all four corners visible.', true),
    ('tampered_document', 'Suspected document alteration or forgery', 'document', true, 'We could not verify the authenticity of your submitted document. Please contact hotel support.', true),
    ('selfie_mismatch', 'Selfie does not match ID portrait', 'biometric', false, 'Your selfie did not clearly match your ID portrait. Please take a fresh, front-facing selfie in good lighting.', true),
    ('name_mismatch', 'Submitted details do not match ID', 'identity', true, 'The personal details entered do not match your identification document. Please review and resubmit.', true),
    ('high_risk_escalation', 'High-risk profile requires senior review', 'risk', true, 'Your verification requires additional manual review by our compliance team.', true),
    ('other_requires_details', 'Other reason (specify details)', 'other', true, 'Additional information is required to complete your identity verification. Please see reviewer notes.', true)
ON CONFLICT (code) DO NOTHING;
