package com.hotelapp.staff.navigation

import com.hotelapp.staff.core.auth.StaffSession
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
data class RoomsRoute(val focusRoomId: Long? = null)

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
    private val ALLOWED_BOOKING_VIEWS = setOf(
        "all",
        "arriving",
        "in_house",
        "departing",
        "upcoming",
        "balance",
    )

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
                if (idx <= 0) {
                    null
                } else {
                    val key = decodeComponent(pair.substring(0, idx)) ?: return@mapNotNull null
                    val value = decodeComponent(pair.substring(idx + 1)) ?: return@mapNotNull null
                    key to value
                }
            }
            ?.toMap()
            .orEmpty()

        return when (host) {
            "dashboard" -> DashboardRoute
            "bookings" -> {
                val requestedView = queryParams["view"]?.trim()?.lowercase()
                val safeView = if (requestedView in ALLOWED_BOOKING_VIEWS) requestedView!! else "all"
                BookingsRoute(view = safeView)
            }
            "booking" -> segments.firstOrNull()?.toLongOrNull()?.takeIf { it > 0L }?.let { BookingDetailRoute(it) }
            "rooms" -> RoomsRoute(focusRoomId = queryParams["room_id"]?.toLongOrNull()?.takeIf { it > 0L })
            "housekeeping" -> HousekeepingRoute
            "maintenance" -> MaintenanceRoute
            "guests" -> GuestsRoute
            "guest" -> segments.firstOrNull()?.toLongOrNull()?.takeIf { it > 0L }?.let { GuestDetailRoute(it) }
            "search" -> SearchRoute
            "payments" -> if (segments.firstOrNull().equals("pending", ignoreCase = true)) {
                PendingPaymentsRoute
            } else {
                DashboardRoute
            }
            "ekyc" -> EkycQueueRoute
            "more" -> MoreRoute
            else -> null
        }
    }

    private fun decodeComponent(raw: String): String? =
        runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrNull()

    fun canAccess(destination: Any, session: StaffSession): Boolean =
        session.canAccessDestination(destination)
}

fun StaffSession.canAccessDestination(destination: Any): Boolean =
    when (destination) {
        DashboardRoute, MoreRoute, ServerConfigRoute, LoginRoute -> true
        is BookingsRoute, is BookingDetailRoute -> hasPermission("bookings:read")
        is RoomsRoute -> hasPermission("rooms:read")
        HousekeepingRoute -> hasPermission("housekeeping:read")
        MaintenanceRoute -> hasPermission("maintenance:read")
        GuestsRoute, is GuestDetailRoute -> hasPermission("guests:read")
        PendingPaymentsRoute -> hasPermission("payments:read")
        EkycQueueRoute -> hasPermission("ekyc:read")
        SearchRoute -> hasAnyPermission("bookings:read", "guests:read", "rooms:read", "ledgers:read")
        else -> false
    }
