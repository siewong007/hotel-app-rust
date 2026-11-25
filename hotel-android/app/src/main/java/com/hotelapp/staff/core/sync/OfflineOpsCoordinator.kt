package com.hotelapp.staff.core.sync

import com.hotelapp.staff.core.network.ApiException
import com.hotelapp.staff.core.network.HotelJson
import com.hotelapp.staff.core.storage.HotelLocalStore
import com.hotelapp.staff.core.storage.StoredPendingMutation
import com.hotelapp.staff.data.dto.CreateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.UpdateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.repository.HousekeepingRepository
import com.hotelapp.staff.data.repository.MaintenanceRepository
import com.hotelapp.staff.data.repository.RoomsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import java.util.UUID

sealed interface SyncReplayResult {
    data class Completed(val processed: Int, val remaining: Int) : SyncReplayResult
    data class RetryNeeded(val processed: Int, val remaining: Int) : SyncReplayResult
}

class OfflineOpsCoordinator(
    private val localStore: HotelLocalStore,
    private val scheduleSyncWorker: () -> Unit = {},
) {
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _lastSyncedAtMs = MutableStateFlow<Long?>(null)
    val lastSyncedAtMs: StateFlow<Long?> = _lastSyncedAtMs.asStateFlow()

    suspend fun refreshPendingCount() {
        _pendingCount.value = localStore.listPendingMutations().size
    }

    suspend fun <T> saveCache(key: String, serializer: KSerializer<T>, value: T) {
        runCatching {
            localStore.putCache(key, HotelJson.encodeToString(serializer, value))
        }
    }

    suspend fun <T> loadCache(key: String, serializer: KSerializer<T>): T? =
        runCatching {
            val raw = localStore.getCache(key)?.payloadJson ?: return null
            HotelJson.decodeFromString(serializer, raw)
        }.getOrNull()

    fun isQueueableNetworkFailure(error: Throwable?): Boolean {
        if (error == null) return false
        if (error is ApiException) {
            if (error.statusCode == null) {
                return !error.message.contains("Unexpected response format", ignoreCase = true)
            }
            return error.statusCode >= 500
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

    suspend fun enqueueMaintenanceCreate(request: CreateMaintenanceTicketRequestDto) {
        enqueue(
            kind = PendingMutationKind.MAINTENANCE_CREATE,
            targetId = request.roomId ?: 0L,
            payloadJson = HotelJson.encodeToString(CreateMaintenanceTicketRequestDto.serializer(), request),
        )
    }

    private suspend fun enqueue(
        kind: PendingMutationKind,
        targetId: Long,
        payloadJson: String,
    ) {
        localStore.enqueueMutation(
            StoredPendingMutation(
                id = UUID.randomUUID().toString(),
                kind = kind.name,
                targetId = targetId,
                payloadJson = payloadJson,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
        refreshPendingCount()
        scheduleSyncWorker()
    }

    suspend fun replayPending(
        housekeepingRepository: HousekeepingRepository,
        roomsRepository: RoomsRepository,
        maintenanceRepository: MaintenanceRepository,
    ): SyncReplayResult {
        val pending = localStore.listPendingMutations()
        if (pending.isEmpty()) {
            _pendingCount.value = 0
            return SyncReplayResult.Completed(processed = 0, remaining = 0)
        }

        var processed = 0
        var needsRetry = false

        for (item in pending) {
            val kind = runCatching { PendingMutationKind.valueOf(item.kind) }.getOrNull()
            if (kind == null) {
                localStore.deleteMutation(item.id)
                continue
            }

            val result: Result<Any?> = when (kind) {
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
                PendingMutationKind.MAINTENANCE_CREATE -> {
                    val req = HotelJson.decodeFromString(CreateMaintenanceTicketRequestDto.serializer(), item.payloadJson)
                    maintenanceRepository.create(req)
                }
            }

            if (result.isSuccess) {
                localStore.deleteMutation(item.id)
                processed++
            } else {
                val err = result.exceptionOrNull()
                val statusCode = (err as? ApiException)?.statusCode
                if (statusCode != null && statusCode in 400..499) {
                    // Reject permanently invalid 4xx server errors rather than spinning forever.
                    localStore.deleteMutation(item.id)
                } else {
                    localStore.recordMutationFailure(item.id, err?.message ?: "Sync failed")
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
        return if (needsRetry) {
            SyncReplayResult.RetryNeeded(processed, remaining)
        } else {
            SyncReplayResult.Completed(processed, remaining)
        }
    }

    suspend fun clearAll() {
        localStore.clearAll()
        _pendingCount.value = 0
        _lastSyncedAtMs.value = null
    }
}
