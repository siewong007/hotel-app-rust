package com.hotelapp.staff.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.hotelapp.staff.MainActivity
import com.hotelapp.staff.R
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.config.ServerConfigStore
import java.util.concurrent.atomic.AtomicLong

class StaffNotificationManager(
    context: Context,
    private val serverConfigStore: ServerConfigStore,
    private val sessionProvider: () -> StaffSession? = { null },
) {
    private val appContext = context.applicationContext
    private val lastPostedByChannel = mutableMapOf<String, Long>()
    private var lastSpecificDomainPostedAt: Long = 0L
    private val idCounter = AtomicLong(2000L)

    fun ensureChannels() {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_URGENT,
                "Urgent Operations",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Critical maintenance tickets and urgent hotel operational alerts"
            },
            NotificationChannel(
                CHANNEL_ARRIVALS,
                "Arrivals & Bookings",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New reservations, arrivals, and check-in updates"
            },
            NotificationChannel(
                CHANNEL_ROOMS,
                "Rooms & Housekeeping",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Room cleaning completion and housekeeping task updates"
            },
            NotificationChannel(
                CHANNEL_PAYMENTS,
                "Payments & Approvals",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Payment verification claims and folio updates"
            },
        )
        manager.createNotificationChannels(channels)
    }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun onRealtimeDomainEvent(domain: String) {
        if (!serverConfigStore.config.value.osNotificationsEnabled) return
        if (!hasNotificationPermission()) return
        val session = sessionProvider() ?: return

        val (channel, title, body, deepLink) = when (domain.lowercase()) {
            "bookings" -> {
                if (!session.hasPermission("bookings:read")) return
                Quad(
                    CHANNEL_ARRIVALS,
                    "Booking activity updated",
                    "Tap to review today's arrivals and reservations.",
                    "hotelstaff://bookings?view=arriving",
                )
            }
            "payments" -> {
                if (!session.hasPermission("payments:read")) return
                Quad(
                    CHANNEL_PAYMENTS,
                    "Payment update received",
                    "Tap to check pending payment approvals.",
                    "hotelstaff://payments/pending",
                )
            }
            "housekeeping" -> {
                if (!session.hasPermission("housekeeping:read")) return
                Quad(
                    CHANNEL_ROOMS,
                    "Housekeeping update",
                    "Tap to view room cleaning assignments.",
                    "hotelstaff://housekeeping",
                )
            }
            "maintenance" -> {
                if (!session.hasPermission("maintenance:read")) return
                Quad(
                    CHANNEL_URGENT,
                    "Maintenance update",
                    "Tap to inspect open room or property repair tickets.",
                    "hotelstaff://maintenance",
                )
            }
            "ekyc" -> {
                if (!session.hasPermission("ekyc:read")) return
                Quad(
                    CHANNEL_URGENT,
                    "eKYC verification update",
                    "Tap to review identity verification queue.",
                    "hotelstaff://ekyc",
                )
            }
            "support" -> {
                if (!session.hasPermission("support:read")) return
                Quad(
                    CHANNEL_URGENT,
                    "Guest support alert",
                    "A guest support request needs staff attention.",
                    "hotelstaff://dashboard",
                )
            }
            "notifications" -> Quad(
                CHANNEL_URGENT,
                "New staff notification",
                "Tap to open your hotel workspace.",
                "hotelstaff://dashboard",
            )
            else -> return
        }

        // Throttle per channel (15s) to prevent flooding during burst syncs, and
        // skip the generic "notifications" toast when a specific domain already fired.
        val now = System.currentTimeMillis()
        val isGeneric = domain.equals("notifications", ignoreCase = true)
        synchronized(lastPostedByChannel) {
            if (isGeneric && now - lastSpecificDomainPostedAt < 3_000L) return
            val prev = lastPostedByChannel[channel] ?: 0L
            if (now - prev < 15_000L) return
            lastPostedByChannel[channel] = now
            if (!isGeneric) {
                lastSpecificDomainPostedAt = now
            }
        }

        postNotification(
            channelId = channel,
            title = title,
            body = body,
            deepLinkUri = deepLink,
        )
    }

    @SuppressLint("MissingPermission")
    fun postNotification(
        channelId: String,
        title: String,
        body: String,
        deepLinkUri: String,
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri.toUri(), appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            deepLinkUri.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_hotel_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (channelId == CHANNEL_URGENT || channelId == CHANNEL_PAYMENTS) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext)
                .notify(idCounter.incrementAndGet().toInt(), notification)
        }
    }

    private data class Quad(
        val channel: String,
        val title: String,
        val body: String,
        val deepLink: String,
    )

    companion object {
        const val CHANNEL_URGENT = "ops_urgent"
        const val CHANNEL_ARRIVALS = "ops_arrivals"
        const val CHANNEL_ROOMS = "ops_rooms"
        const val CHANNEL_PAYMENTS = "ops_payments"
    }
}
