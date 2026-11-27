package com.hotelapp.staff.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hotelapp.staff.HotelStaffApp
import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.network.HotelJson
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? HotelStaffApp ?: return Result.failure()
        val container = app.container
        container.serverConfigStore.loadInitial()
        if (container.serverConfigStore.config.value.biometricLockEnabled) {
            clearWidget(
                applicationContext,
                container.localStore,
                container.serverConfigStore.config.value.hotelName,
            )
            return Result.success()
        }
        if (container.sessionManager.authState.value is AuthState.Checking) {
            container.authRepository.restoreSession()
        }
        val auth = container.sessionManager.authState.value
        if (auth !is AuthState.Authenticated) {
            return if (container.authRepository.hasStoredSession()) {
                Result.retry()
            } else {
                clearWidget(applicationContext, container.localStore, container.serverConfigStore.config.value.hotelName)
                Result.success()
            }
        }

        val session = auth.session
        val startGeneration = container.sessionManager.currentGeneration()
        val startOrigin = container.serverConfigStore.currentBaseUrl()
        val startUserId = session.user.id
        var snapshotSaved = false
        coroutineScope {
            val bookingDef = if (session.hasPermission("bookings:read")) async { container.bookingsRepository.summary() } else null
            val occDef = if (session.hasPermission("rooms:read")) async { container.roomsRepository.occupancySummary() } else null
            val roomsDef = if (session.hasPermission("rooms:read")) async { container.roomsRepository.roomsWithOccupancy() } else null
            val maintDef = if (session.hasPermission("maintenance:read")) async { container.maintenanceRepository.list(status = "open", page = 1) } else null

            val bookingRes = bookingDef?.await()
            val occRes = occDef?.await()
            val roomsRes = roomsDef?.await()
            val maintRes = maintDef?.await()

            val results = listOfNotNull(bookingRes, occRes, roomsRes, maintRes)
            if (results.isNotEmpty() && results.all { it.isFailure }) {
                return@coroutineScope
            }

            // Re-verify session principal & generation after async fetches so an in-flight
            // worker cannot overwrite a cleared widget after logout or server switch.
            val currentAuth = container.sessionManager.authState.value as? AuthState.Authenticated
            if (currentAuth == null ||
                container.sessionManager.currentGeneration() != startGeneration ||
                currentAuth.session.user.id != startUserId ||
                container.serverConfigStore.currentBaseUrl() != startOrigin
            ) {
                return@coroutineScope
            }

            val bookingSummary = bookingRes?.getOrNull()
            val occSummary = occRes?.getOrNull()
            val rooms = roomsRes?.getOrNull()
            val dirtyCount = rooms?.count { !it.isOccupied && (it.status == "dirty" || it.status == "reserved_dirty") } ?: 0
            val openMaint = maintRes?.getOrNull()?.total?.toInt() ?: 0

            val rate = occSummary?.occupancyRate?.let { "${it.stripTrailingZeros().toPlainString()}%" } ?: "0%"
            val snapshot = OpsWidgetSnapshot(
                hotelName = container.serverConfigStore.config.value.hotelName,
                occupancyPercent = rate,
                occupiedRooms = occSummary?.occupiedRooms ?: 0L,
                totalRooms = occSummary?.totalRooms ?: 0L,
                arrivingToday = bookingSummary?.arriving ?: 0L,
                dirtyRooms = dirtyCount,
                openMaintenance = openMaint,
                updatedAtMs = System.currentTimeMillis(),
                isAuthenticated = true,
            )
            saveAndPush(applicationContext, container.localStore, snapshot)
            snapshotSaved = true
        }
        return if (snapshotSaved || container.sessionManager.authState.value !is AuthState.Authenticated) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "hotel_ops_widget_refresh"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        suspend fun saveAndPush(
            context: Context,
            localStore: com.hotelapp.staff.core.storage.HotelLocalStore,
            snapshot: OpsWidgetSnapshot,
        ) {
            runCatching {
                val json = HotelJson.encodeToString(OpsWidgetSnapshot.serializer(), snapshot)
                localStore.putCache(OpsWidgetSnapshot.CACHE_KEY, json)
                HotelOpsWidget().updateAll(context.applicationContext)
            }
        }

        suspend fun clearWidget(
            context: Context,
            localStore: com.hotelapp.staff.core.storage.HotelLocalStore,
            hotelName: String,
        ) {
            saveAndPush(
                context = context,
                localStore = localStore,
                snapshot = OpsWidgetSnapshot(hotelName = hotelName, isAuthenticated = false),
            )
        }
    }
}
