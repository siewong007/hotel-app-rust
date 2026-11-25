package com.hotelapp.staff.navigation

import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.data.dto.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkRouterTest {

    @Test
    fun `parses supported hotelstaff URIs`() {
        assertEquals(DashboardRoute, DeepLinkRouter.parse("hotelstaff://dashboard"))
        assertEquals(BookingsRoute("arriving"), DeepLinkRouter.parse("hotelstaff://bookings?view=arriving"))
        assertEquals(BookingsRoute("all"), DeepLinkRouter.parse("hotelstaff://bookings"))
        assertEquals(BookingDetailRoute(42L), DeepLinkRouter.parse("hotelstaff://booking/42"))
        assertEquals(RoomsRoute, DeepLinkRouter.parse("hotelstaff://rooms"))
        assertEquals(HousekeepingRoute, DeepLinkRouter.parse("hotelstaff://housekeeping"))
        assertEquals(MaintenanceRoute, DeepLinkRouter.parse("hotelstaff://maintenance"))
        assertEquals(GuestsRoute, DeepLinkRouter.parse("hotelstaff://guests"))
        assertEquals(GuestDetailRoute(9L), DeepLinkRouter.parse("hotelstaff://guest/9"))
        assertEquals(SearchRoute, DeepLinkRouter.parse("hotelstaff://search"))
        assertEquals(PendingPaymentsRoute, DeepLinkRouter.parse("hotelstaff://payments/pending"))
        assertEquals(EkycQueueRoute, DeepLinkRouter.parse("hotelstaff://ekyc"))
        assertEquals(MoreRoute, DeepLinkRouter.parse("hotelstaff://more"))
    }

    @Test
    fun `rejects non-matching schemes and invalid IDs`() {
        assertNull(DeepLinkRouter.parse(null))
        assertNull(DeepLinkRouter.parse(""))
        assertNull(DeepLinkRouter.parse("https://saliminn.my/bookings"))
        assertNull(DeepLinkRouter.parse("hotelstaff://booking/notanumber"))
        assertNull(DeepLinkRouter.parse("hotelstaff://unknown"))
    }

    @Test
    fun `canAccess enforces role permissions per destination`() {
        fun sessionWith(role: String, vararg perms: String, superAdmin: Boolean = false) = StaffSession(
            user = UserSummaryDto(id = 1L, username = role, isSuperAdmin = superAdmin),
            roles = listOf(role),
            permissions = perms.toList(),
        )

        val hk = sessionWith("housekeeping", "rooms:read", "rooms:update", "housekeeping:read", "housekeeping:update", "maintenance:read", "maintenance:write")
        assertTrue(DeepLinkRouter.canAccess(DashboardRoute, hk))
        assertTrue(DeepLinkRouter.canAccess(RoomsRoute, hk))
        assertTrue(DeepLinkRouter.canAccess(HousekeepingRoute, hk))
        assertTrue(DeepLinkRouter.canAccess(MaintenanceRoute, hk))
        assertTrue(DeepLinkRouter.canAccess(SearchRoute, hk))
        assertFalse(DeepLinkRouter.canAccess(BookingsRoute("all"), hk))
        assertFalse(DeepLinkRouter.canAccess(BookingDetailRoute(1L), hk))
        assertFalse(DeepLinkRouter.canAccess(GuestsRoute, hk))
        assertFalse(DeepLinkRouter.canAccess(PendingPaymentsRoute, hk))
        assertFalse(DeepLinkRouter.canAccess(EkycQueueRoute, hk))

        val ekyc = sessionWith("ekyc_reviewer", "ekyc:read", "ekyc:review", "ekyc:approve")
        assertTrue(DeepLinkRouter.canAccess(EkycQueueRoute, ekyc))
        assertFalse(DeepLinkRouter.canAccess(SearchRoute, ekyc))
        assertFalse(DeepLinkRouter.canAccess(BookingsRoute("all"), ekyc))
        assertFalse(DeepLinkRouter.canAccess(RoomsRoute, ekyc))

        val auditor = sessionWith("auditor", "payments:read", "ekyc:read", "ledgers:read")
        assertTrue(DeepLinkRouter.canAccess(PendingPaymentsRoute, auditor))
        assertTrue(DeepLinkRouter.canAccess(EkycQueueRoute, auditor))
        assertTrue(DeepLinkRouter.canAccess(SearchRoute, auditor))
        assertFalse(DeepLinkRouter.canAccess(BookingsRoute("all"), auditor))

        val superAdmin = sessionWith("super_admin", superAdmin = true)
        assertTrue(DeepLinkRouter.canAccess(BookingDetailRoute(9L), superAdmin))
        assertTrue(DeepLinkRouter.canAccess(EkycQueueRoute, superAdmin))
        assertTrue(DeepLinkRouter.canAccess(PendingPaymentsRoute, superAdmin))
    }
}
