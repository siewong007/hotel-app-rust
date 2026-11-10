-- Money columns follow the hotel's configured currency, never a hard-coded USD.
--
-- The V1 baseline gave bookings, payments, invoices, loyalty_programs and
-- promotions `DEFAULT 'USD'`. Every creation path that did not pass a currency
-- (staff/walk-in/OTA bookings, desk payments, deposits, refunds, invoices) was
-- therefore stored as USD even though the hotel's `system_settings.currency`
-- is MYR, and staff booking groups copied the USD from their first room.
--
-- * public.hotel_currency() reads `system_settings.currency` (3-letter code,
--   upper-cased), falling back to MYR when unset or malformed.
-- * bookings, booking_groups, loyalty_programs, promotions and
--   customer_ledgers default to public.hotel_currency().
-- * payments and invoices take their booking's currency (hotel currency when
--   the booking has none) through a BEFORE INSERT trigger, so a payment is
--   always recorded in the currency its booking is priced in.
-- * A booking inserted with a NULL/blank currency gets the hotel currency.
--
-- Existing rows are not touched. Idempotent: CREATE OR REPLACE, SET DEFAULT
-- and DROP TRIGGER IF EXISTS re-run cleanly.

CREATE OR REPLACE FUNCTION public.hotel_currency() RETURNS character varying
    LANGUAGE sql STABLE
    SET search_path TO 'pg_catalog', 'public'
    AS $$
    SELECT COALESCE(
        (SELECT upper(btrim(s.value))
           FROM public.system_settings s
          WHERE s.key = 'currency'
            AND btrim(s.value) ~ '^[A-Za-z]{3}$'
          LIMIT 1),
        'MYR'
    )::character varying(3)
$$;

CREATE OR REPLACE FUNCTION public.fill_booking_currency() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
BEGIN
    IF NEW.currency IS NULL OR btrim(NEW.currency) = '' THEN
        NEW.currency := public.hotel_currency();
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.fill_currency_from_booking() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
BEGIN
    IF NEW.currency IS NULL OR btrim(NEW.currency) = '' THEN
        SELECT NULLIF(btrim(b.currency), '') INTO NEW.currency
          FROM public.bookings b
         WHERE b.id = NEW.booking_id;
        NEW.currency := COALESCE(NEW.currency, public.hotel_currency());
    END IF;
    RETURN NEW;
END;
$$;

ALTER TABLE public.bookings ALTER COLUMN currency SET DEFAULT public.hotel_currency();
ALTER TABLE public.booking_groups ALTER COLUMN currency SET DEFAULT public.hotel_currency();
ALTER TABLE public.loyalty_programs ALTER COLUMN currency SET DEFAULT public.hotel_currency();
ALTER TABLE public.promotions ALTER COLUMN currency SET DEFAULT public.hotel_currency();
ALTER TABLE public.customer_ledgers ALTER COLUMN currency SET DEFAULT public.hotel_currency();
-- No column default: the trigger below fills the booking's currency.
ALTER TABLE public.payments ALTER COLUMN currency DROP DEFAULT;
ALTER TABLE public.invoices ALTER COLUMN currency DROP DEFAULT;

DROP TRIGGER IF EXISTS trg_fill_booking_currency ON public.bookings;
CREATE TRIGGER trg_fill_booking_currency
    BEFORE INSERT ON public.bookings
    FOR EACH ROW EXECUTE FUNCTION public.fill_booking_currency();

DROP TRIGGER IF EXISTS trg_fill_payment_currency ON public.payments;
CREATE TRIGGER trg_fill_payment_currency
    BEFORE INSERT ON public.payments
    FOR EACH ROW EXECUTE FUNCTION public.fill_currency_from_booking();

DROP TRIGGER IF EXISTS trg_fill_invoice_currency ON public.invoices;
CREATE TRIGGER trg_fill_invoice_currency
    BEFORE INSERT ON public.invoices
    FOR EACH ROW EXECUTE FUNCTION public.fill_currency_from_booking();
