package com.hotelapp.staff.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hotelapp.staff.HotelStaffApp
import com.hotelapp.staff.core.auth.AuthState
import java.util.concurrent.TimeUnit

class SyncPendingMutationsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? HotelStaffApp ?: return Result.failure()
        val container = app.container
        if (container.sessionManager.authState.value !is AuthState.Authenticated) {
            return Result.success()
        }

        return when (
            container.offlineOpsCoordinator.replayPending(
                housekeepingRepository = container.housekeepingRepository,
                roomsRepository = container.roomsRepository,
                maintenanceRepository = container.maintenanceRepository,
            )
        ) {
            is SyncReplayResult.Completed -> Result.success()
            is SyncReplayResult.RetryNeeded -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "hotel_sync_pending_mutations"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncPendingMutationsWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
