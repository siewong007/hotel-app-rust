package com.hotelapp.staff.navigation

import com.hotelapp.staff.core.auth.StaffSession
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data object LoginRoute

@Serializable
data object ServerConfigRoute

@Serializable
data object DashboardRoute

@Serializable
data class BookingsRoute(val view: String = "all")

@Serializable
data class BookingDetailRoute(val bookingId: Long)

@Serializable
data object RoomsRoute

@Serializable
data object HousekeepingRoute

@Serializable
data object MoreRoute

@Serializable
data object MaintenanceRoute

@Serializable
data object GuestsRoute

@Serializable
data class GuestDetailRoute(val guestId: Long)

@Serializable
data object SearchRoute

@Serializable
data object PendingPaymentsRoute

@Serializable
data object EkycQueueRoute

object DeepLinkRouter {
    fun parse(uriString: String?): Any? {
        val raw = uriString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI.create(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("hotelstaff", ignoreCase = true)) return null

        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.path
            ?.split('/')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val queryParams = uri.rawQuery
            ?.split('&')
            ?.mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
            }
            ?.toMap()
            .orEmpty()

        return when (host) {
            "dashboard" -> DashboardRoute
            "bookings" -> BookingsRoute(view = queryParams["view"]?.takeIf { it.isNotBlank() } ?: "all")
            "booking" -> segments.firstOrNull()?.toLongOrNull()?.let { BookingDetailRoute(it) }
            "rooms" -> RoomsRoute
            "housekeeping" -> HousekeepingRoute
            "maintenance" -> MaintenanceRoute
            "guests" -> GuestsRoute
            "guest" -> segments.firstOrNull()?.toLongOrNull()?.let { GuestDetailRoute(it) }
            "search" -> SearchRoute
            "payments" -> if (segments.firstOrNull() == "pending") PendingPaymentsRoute else DashboardRoute
            "ekyc" -> EkycQueueRoute
            "more" -> MoreRoute
            else -> null
        }
    }

    fun canAccess(destination: Any, session: StaffSession): Boolean =
        session.canAccessDestination(destination)
}

fun StaffSession.canAccessDestination(destination: Any): Boolean =
    when (destination) {
        DashboardRoute, MoreRoute, ServerConfigRoute, LoginRoute -> true
        is BookingsRoute, is BookingDetailRoute -> hasPermission("bookings:read")
        RoomsRoute -> hasPermission("rooms:read")
        HousekeepingRoute -> hasPermission("housekeeping:read")
        MaintenanceRoute -> hasPermission("maintenance:read")
        GuestsRoute, is GuestDetailRoute -> hasPermission("guests:read")
        PendingPaymentsRoute -> hasPermission("payments:read")
        EkycQueueRoute -> hasPermission("ekyc:read")
        SearchRoute -> hasAnyPermission("bookings:read", "guests:read", "rooms:read", "ledgers:read")
        else -> false
    }
