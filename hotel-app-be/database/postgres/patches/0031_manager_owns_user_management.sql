-- Manager owns user management and data transfer; admin no longer does.
--
-- The owner's rule: "Manager should have everything that admin has, plus data
-- transfer and user management." So after this patch:
--
--   * manager holds every permission admin holds, plus the moved set below;
--   * admin keeps everything else it had, minus the moved set;
--   * manager outranks admin (priority 150 > 100) so the role-priority guard
--     lets a manager assign and manage admin and below, but never another
--     manager or super_admin (super_admin stays at 1000).
--
-- The moved set ("user related" + data transfer):
--   users:*, roles:*, permissions:*      user accounts, role and permission admin
--   teams:create/update/delete/assign/manage
--                                        team membership confers roles
--   navigation_rbac:read                 Access Control menu entry
--   data_transfer:*                      import, export, restore
--   navigation_data_transfer:read        Data Transfer menu entry
-- `teams:read` stays on admin (reception holds it too; it is read-only).
--
-- Matching is by resource for users/roles/permissions/data_transfer, so a
-- custom permission added later on one of those resources follows the same
-- rule. super_admin's own grants are untouched.
--
-- Permission checks resolve through roles only; the `users.is_super_admin`
-- flag lifts the escalation guards but grants nothing by itself. The seeded
-- System Administrator account (`admin`, flagged) was assigned only the
-- `admin` role, so it would lose user management here. Every active flagged
-- account therefore also gets the `super_admin` role (step 0), which keeps
-- its access exactly as before. No other role assignment changes.
--
-- Idempotent: inserts skip existing rows, the delete and updates only touch
-- rows that still differ.

-- 0. Flagged super-admin accounts hold the super_admin role.
INSERT INTO public.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM public.users u
JOIN public.roles r ON r.name = 'super_admin'
WHERE u.is_super_admin
  AND u.is_active
  AND u.deleted_at IS NULL
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE hotel_patch_0031_moved ON COMMIT DROP AS
SELECT p.id
FROM public.permissions p
WHERE p.resource IN ('users', 'roles', 'permissions', 'data_transfer')
   OR p.name IN (
        'teams:create', 'teams:update', 'teams:delete', 'teams:assign', 'teams:manage',
        'navigation_rbac:read', 'navigation_data_transfer:read'
   );

-- 1. Manager gets every permission admin holds today (before the removal) ...
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT m.id, rp.permission_id
FROM public.roles m
JOIN public.roles a ON a.name = 'admin'
JOIN public.role_permissions rp ON rp.role_id = a.id
WHERE m.name = 'manager'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2. ... plus the whole moved set, even the parts admin never had.
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT m.id, moved.id
FROM public.roles m
CROSS JOIN hotel_patch_0031_moved moved
WHERE m.name = 'manager'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 3. Admin loses the moved set.
DELETE FROM public.role_permissions rp
USING public.roles a, hotel_patch_0031_moved moved
WHERE a.name = 'admin'
  AND rp.role_id = a.id
  AND rp.permission_id = moved.id;

-- 4. Manager outranks admin, still below super_admin.
UPDATE public.roles
SET priority = 150,
    description = 'Hotel management: everything admin has, plus user management and data transfer'
WHERE name = 'manager'
  AND (priority IS DISTINCT FROM 150
       OR description IS DISTINCT FROM 'Hotel management: everything admin has, plus user management and data transfer');

UPDATE public.roles
SET description = 'Hotel administration without user management or data transfer'
WHERE name = 'admin'
  AND description IS DISTINCT FROM 'Hotel administration without user management or data transfer';

-- 5. Verify the invariants this patch promises.
DO $verify_0031$
BEGIN
    IF EXISTS (SELECT 1 FROM public.roles WHERE name = 'manager')
       AND EXISTS (SELECT 1 FROM public.roles WHERE name = 'admin') THEN
        IF EXISTS (
            SELECT 1
            FROM public.role_permissions rp
            JOIN public.roles a ON a.id = rp.role_id AND a.name = 'admin'
            WHERE NOT EXISTS (
                SELECT 1
                FROM public.role_permissions mp
                JOIN public.roles m ON m.id = mp.role_id AND m.name = 'manager'
                WHERE mp.permission_id = rp.permission_id
            )
        ) THEN
            RAISE EXCEPTION 'patch 0031: manager does not hold every admin permission';
        END IF;
        IF EXISTS (
            SELECT 1
            FROM public.role_permissions rp
            JOIN public.roles a ON a.id = rp.role_id AND a.name = 'admin'
            JOIN hotel_patch_0031_moved moved ON moved.id = rp.permission_id
        ) THEN
            RAISE EXCEPTION 'patch 0031: admin still holds a user-management or data-transfer permission';
        END IF;
    END IF;
END;
$verify_0031$;
