package com.hotelapp.staff.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCheckerTest {

    @Test
    fun `exact match and manage wildcard inheritance`() {
        val perms = listOf("bookings:read", "housekeeping:manage")

        assertTrue(PermissionChecker.hasPermission(perms, isSuperAdmin = false, "bookings:read"))
        assertFalse(PermissionChecker.hasPermission(perms, isSuperAdmin = false, "bookings:update"))

        // :manage implies every action on that resource
        assertTrue(PermissionChecker.hasPermission(perms, isSuperAdmin = false, "housekeeping:read"))
        assertTrue(PermissionChecker.hasPermission(perms, isSuperAdmin = false, "housekeeping:create"))
        assertTrue(PermissionChecker.hasPermission(perms, isSuperAdmin = false, "housekeeping:update"))

        // unrelated resource stays denied
        assertFalse(PermissionChecker.hasPermission(perms, isSuperAdmin = false, "rooms:update"))
    }

    @Test
    fun `super admin flag does not bypass missing explicit permission grants`() {
        assertFalse(PermissionChecker.hasPermission(emptyList(), isSuperAdmin = true, "bookings:delete"))
        assertFalse(PermissionChecker.hasAnyPermission(emptyList(), isSuperAdmin = true, "rooms:read", "rooms:write"))
        assertTrue(PermissionChecker.hasPermission(listOf("bookings:manage"), isSuperAdmin = true, "bookings:delete"))
    }
}
