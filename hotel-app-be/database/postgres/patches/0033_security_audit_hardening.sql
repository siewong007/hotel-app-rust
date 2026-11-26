-- Security audit hardening:
--   1. Bind `app.allow_audit_mutation` in `prevent_audit_log_mutation()` to
--      membership in the `audit_logs` table owner role (`current_user`), so an
--      unprivileged runtime role (`hotel_app`) cannot bypass the append-only
--      guard with `SET LOCAL app.allow_audit_mutation = 'on'`.
--   2. Revoke `rooms:read`, `reviews:read`, and `reviews:update` from the
--      self-registered `guest` role (which only needs `reviews:create`),
--      closing hotel-wide room occupancy/rate disclosure and management review
--      response forgery on existing databases.
--
-- Guarded on patch 0022's `prevent_audit_log_mutation()` body (which is also
-- what `0001_v1_baseline.sql` keeps so patch 0022 passes on fresh installs),
-- and idempotent when already at 0033's definition.

DO $audit_guard_owner$
DECLARE
    found_definition text;
    current_definition constant text := $fn_current$CREATE OR REPLACE FUNCTION public.prevent_audit_log_mutation()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
BEGIN
    -- Escape hatch for fixture cleanup only: integration tests set this GUC
    -- per pooled connection as the table owner so they can purge rows they
    -- wrote. Nothing in the application sets it, and unprivileged runtime roles
    -- such as `hotel_app` are rejected even if they run SET LOCAL.
    IF current_setting('app.allow_audit_mutation', true) IS DISTINCT FROM 'on'
       OR NOT pg_has_role(
           current_user,
           (SELECT relowner FROM pg_catalog.pg_class WHERE oid = 'public.audit_logs'::regclass),
           'USAGE'
       ) THEN
        RAISE EXCEPTION 'audit_logs is append-only: UPDATE and DELETE are forbidden';
    END IF;
    -- A BEFORE ROW trigger that returns NULL silently skips its row, so with
    -- the escape hatch open the row must be handed back for the UPDATE or
    -- DELETE to happen. Statement-level calls ignore the return value.
    IF TG_LEVEL = 'ROW' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$function$
$fn_current$;
    old_definition constant text := $fn_old$CREATE OR REPLACE FUNCTION public.prevent_audit_log_mutation()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
BEGIN
    -- Escape hatch for fixture cleanup only: integration tests set this GUC
    -- per pooled connection so they can purge rows they wrote. Nothing in the
    -- application sets it. A principal that can run SET could equally drop the
    -- trigger, so the GUC widens nothing -- the trigger exists to stop
    -- accidental and application-level mutation, not the database owner.
    IF current_setting('app.allow_audit_mutation', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'audit_logs is append-only: UPDATE and DELETE are forbidden';
    END IF;
    -- A BEFORE ROW trigger that returns NULL silently skips its row, so with
    -- the escape hatch open the row must be handed back for the UPDATE or
    -- DELETE to happen. Statement-level calls ignore the return value.
    IF TG_LEVEL = 'ROW' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$function$
$fn_old$;
BEGIN
    SELECT replace(pg_get_functiondef(routine_row.oid), chr(13), '')
    INTO found_definition
    FROM pg_proc AS routine_row
    WHERE routine_row.oid = to_regprocedure('public.prevent_audit_log_mutation()');

    IF found_definition IS NULL OR found_definition = old_definition THEN
        EXECUTE current_definition;
    ELSIF found_definition <> current_definition THEN
        RAISE EXCEPTION 'prevent_audit_log_mutation() has incompatible definition: %', found_definition;
    END IF;
END;
$audit_guard_owner$;

DELETE FROM public.role_permissions rp
USING public.roles r, public.permissions p
WHERE r.name = 'guest'
  AND rp.role_id = r.id
  AND rp.permission_id = p.id
  AND p.name IN ('rooms:read', 'reviews:read', 'reviews:update');

DO $verify_0033$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.role_permissions rp
        JOIN public.roles r ON r.id = rp.role_id
        JOIN public.permissions p ON p.id = rp.permission_id
        WHERE r.name = 'guest'
          AND p.name IN ('rooms:read', 'reviews:read', 'reviews:update')
    ) THEN
        RAISE EXCEPTION 'patch 0033: guest role still holds restricted staff permissions';
    END IF;
END;
$verify_0033$;
