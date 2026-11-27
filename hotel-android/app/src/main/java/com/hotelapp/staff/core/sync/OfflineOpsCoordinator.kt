package com.hotelapp.staff.core.sync

import com.hotelapp.staff.core.network.ApiException
import com.hotelapp.staff.core.network.HotelJson
import com.hotelapp.staff.core.network.runSuspendCatching
import com.hotelapp.staff.core.storage.HotelLocalStore
import com.hotelapp.staff.core.storage.LocalDataScope
import com.hotelapp.staff.core.storage.StoredPendingMutation
import com.hotelapp.staff.data.dto.CreateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.UpdateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.repository.HousekeepingRepository
import com.hotelapp.staff.data.repository.MaintenanceRepository
import com.hotelapp.staff.data.repository.RoomsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import java.util.UUID

sealed interface SyncReplayResult {
    data class Completed(val processed: Int, val remaining: Int) : SyncReplayResult
    data class RetryNeeded(val processed: Int, val remaining: Int) : SyncReplayResult
}

class OfflineOpsCoordinator(
    private val localStore: HotelLocalStore,
    private val scheduleSyncWorker: () -> Unit = {},
    private val principalProvider: () -> LocalDataScope = { LocalDataScope() },
    private val generationProvider: () -> Long = { 0L },
) {
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _lastSyncedAtMs = MutableStateFlow<Long?>(null)
    val lastSyncedAtMs: StateFlow<Long?> = _lastSyncedAtMs.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val replayMutex = Mutex()

    fun clearLastSyncError() {
        _lastSyncError.value = null
    }

    suspend fun refreshPendingCount() {
        val count = runSuspendCatching { localStore.listPendingMutations().size }.getOrNull() ?: return
        _pendingCount.value = count
    }

    suspend fun <T> saveCache(key: String, serializer: KSerializer<T>, value: T) {
        runSuspendCatching {
            localStore.putCache(key, HotelJson.encodeToString(serializer, value))
        }
    }

    suspend fun <T> loadCache(key: String, serializer: KSerializer<T>): T? =
        runSuspendCatching {
            val raw = localStore.getCache(key)?.payloadJson ?: return@runSuspendCatching null
            HotelJson.decodeFromString(serializer, raw)
        }.getOrNull()

    fun isQueueableNetworkFailure(error: Throwable?): Boolean {
        if (error == null) return false
        if (error is ApiException) {
            if (error.statusCode == null) {
                return !error.message.contains("Unexpected response format", ignoreCase = true) &&
                    !error.message.contains("Invalid configured server URL", ignoreCase = true)
            }
            return error.statusCode in QUEUEABLE_HTTP_STATUSES
        }
        return true
    }

    suspend fun enqueueHousekeepingTaskUpdate(
        taskId: Long,
        request: UpdateHousekeepingTaskRequestDto,
    ) {
        enqueue(
            kind = PendingMutationKind.HOUSEKEEPING_TASK_UPDATE,
            targetId = taskId,
            payloadJson = HotelJson.encodeToString(UpdateHousekeepingTaskRequestDto.serializer(), request),
        )
    }

    suspend fun enqueueRoomStatusUpdate(
        roomId: Long,
        status: String,
        notes: String?,
    ) {
        enqueue(
            kind = PendingMutationKind.ROOM_STATUS_UPDATE,
            targetId = roomId,
            payloadJson = HotelJson.encodeToString(
                QueuedRoomStatusPayload.serializer(),
                QueuedRoomStatusPayload(status = status, notes = notes),
            ),
        )
    }

    suspend fun enqueueRoomEndCleaning(roomId: Long) {
        enqueue(
            kind = PendingMutationKind.ROOM_END_CLEANING,
            targetId = roomId,
            payloadJson = "{}",
        )
    }

    suspend fun enqueueRoomEndMaintenance(roomId: Long) {
        enqueue(
            kind = PendingMutationKind.ROOM_END_MAINTENANCE,
            targetId = roomId,
            payloadJson = "{}",
        )
    }

    suspend fun enqueueMaintenanceCreate(
        request: CreateMaintenanceTicketRequestDto,
        idempotencyKey: String? = null,
    ) {
        enqueue(
            kind = PendingMutationKind.MAINTENANCE_CREATE,
            targetId = request.roomId ?: 0L,
            payloadJson = HotelJson.encodeToString(CreateMaintenanceTicketRequestDto.serializer(), request),
            customId = idempotencyKey,
        )
    }

    private suspend fun enqueue(
        kind: PendingMutationKind,
        targetId: Long,
        payloadJson: String,
        customId: String? = null,
    ) {
        val scope = principalProvider()
        localStore.enqueueMutation(
            StoredPendingMutation(
                id = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                kind = kind.name,
                targetId = targetId,
                payloadJson = payloadJson,
                createdAtMs = System.currentTimeMillis(),
                serverOrigin = scope.serverOrigin,
                userId = scope.userId,
                sessionGeneration = generationProvider(),
            ),
        )
        refreshPendingCount()
        scheduleSyncWorker()
    }

    suspend fun replayPending(
        housekeepingRepository: HousekeepingRepository,
        roomsRepository: RoomsRepository,
        maintenanceRepository: MaintenanceRepository,
    ): SyncReplayResult = replayMutex.withLock {
        val pending = localStore.listPendingMutations()
        if (pending.isEmpty()) {
            _pendingCount.value = 0
            return@withLock SyncReplayResult.Completed(processed = 0, remaining = 0)
        }

        val startScope = principalProvider()
        val startGeneration = generationProvider()
        var processed = 0
        var needsRetry = false

        for (item in pending) {
            val currentScope = principalProvider()
            val currentGeneration = generationProvider()
            if (currentGeneration != startGeneration) {
                break
            }
            val itemIsScoped = item.userId > 0L || item.serverOrigin.isNotEmpty()
            if (itemIsScoped && currentScope != LocalDataScope(item.serverOrigin, item.userId)) {
                if (currentScope.userId > 0L) {
                    // Never execute another user's or server's mutation; discard mismatched row.
                    localStore.deleteMutation(item.id)
                    continue
                } else {
                    break
                }
            }
            if (startScope.userId > 0L && currentScope != startScope) {
                break
            }

            val kind = runCatching { PendingMutationKind.valueOf(item.kind) }.getOrNull()
            if (kind == null) {
                localStore.deleteMutation(item.id)
                continue
            }

            val execution = runSuspendCatching {
                when (kind) {
                    PendingMutationKind.HOUSEKEEPING_TASK_UPDATE -> {
                        val req = HotelJson.decodeFromString(UpdateHousekeepingTaskRequestDto.serializer(), item.payloadJson)
                        housekeepingRepository.updateTask(item.targetId, req)
                    }
                    PendingMutationKind.ROOM_STATUS_UPDATE -> {
                        val req = HotelJson.decodeFromString(QueuedRoomStatusPayload.serializer(), item.payloadJson)
                        roomsRepository.updateStatus(item.targetId, req.status, req.notes)
                    }
                    PendingMutationKind.ROOM_END_CLEANING -> {
                        roomsRepository.endCleaning(item.targetId)
                    }
                    PendingMutationKind.ROOM_END_MAINTENANCE -> {
                        roomsRepository.endMaintenance(item.targetId)
                    }
                    PendingMutationKind.MAINTENANCE_CREATE -> {
                        val req = HotelJson.decodeFromString(CreateMaintenanceTicketRequestDto.serializer(), item.payloadJson)
                        maintenanceRepository.create(req, idempotencyKey = item.id)
                    }
                }
            }

            if (execution.isFailure) {
                // Corrupt / schema-drifted JSON row: drop poison-pill entry so remaining queue continues.
                localStore.deleteMutation(item.id)
                continue
            }

            val result = execution.getOrThrow()
            if (result.isSuccess) {
                localStore.deleteMutation(item.id)
                processed++
            } else {
                val err = result.exceptionOrNull()
                val errMessage = err?.message ?: "Sync failed"
                val statusCode = (err as? ApiException)?.statusCode
                val isPermanentClientError = statusCode != null &&
                    statusCode in 400..499 &&
                    statusCode !in RETRYABLE_CLIENT_STATUSES
                if (isPermanentClientError) {
                    localStore.deleteMutation(item.id)
                    _lastSyncError.value = errMessage
                } else if (item.attempts + 1 >= MAX_RETRY_ATTEMPTS) {
                    localStore.deleteMutation(item.id)
                    _lastSyncError.value = "Removed an offline action after $MAX_RETRY_ATTEMPTS failed attempts: $errMessage"
                } else {
                    localStore.recordMutationFailure(item.id, errMessage)
                    needsRetry = true
                    break
                }
            }
        }

        val remaining = localStore.listPendingMutations().size
        _pendingCount.value = remaining
        if (processed > 0 || remaining == 0) {
            _lastSyncedAtMs.value = System.currentTimeMillis()
        }
        return@withLock if (needsRetry) {
            SyncReplayResult.RetryNeeded(processed, remaining)
        } else {
            SyncReplayResult.Completed(processed, remaining)
        }
    }

    companion object {
        const val MAX_RETRY_ATTEMPTS = 6
        private val RETRYABLE_CLIENT_STATUSES = setOf(401, 408, 429)
        private val QUEUEABLE_HTTP_STATUSES = setOf(408, 429, 502, 503, 504)
    }

    suspend fun clearAll() = replayMutex.withLock {
        localStore.clearAll()
        _pendingCount.value = 0
        _lastSyncedAtMs.value = null
        _lastSyncError.value = null
    }
}
