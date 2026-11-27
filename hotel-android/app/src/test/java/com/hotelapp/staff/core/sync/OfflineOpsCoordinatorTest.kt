package com.hotelapp.staff.core.sync

import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.core.network.RateLimitedException
import com.hotelapp.staff.core.storage.HotelLocalStore
import com.hotelapp.staff.core.storage.LocalDataScope
import com.hotelapp.staff.core.storage.StoredCacheEntry
import com.hotelapp.staff.core.storage.StoredPendingMutation
import com.hotelapp.staff.data.dto.CreateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.HotelOccupancySummaryDto
import com.hotelapp.staff.data.dto.UpdateHousekeepingTaskRequestDto
import com.hotelapp.staff.testutil.FakeHousekeepingRepository
import com.hotelapp.staff.testutil.FakeMaintenanceRepository
import com.hotelapp.staff.testutil.FakeRoomsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class MemoryHotelLocalStore : HotelLocalStore {
    val cache = mutableMapOf<String, StoredCacheEntry>()
    val mutations = mutableListOf<StoredPendingMutation>()

    override suspend fun putCache(key: String, payloadJson: String, nowMs: Long) {
        cache[key] = StoredCacheEntry(key, payloadJson, nowMs)
    }

    override suspend fun getCache(key: String): StoredCacheEntry? = cache[key]

    override suspend fun enqueueMutation(mutation: StoredPendingMutation) {
        mutations.add(mutation)
    }

    override suspend fun listPendingMutations(): List<StoredPendingMutation> = mutations.toList()

    override suspend fun deleteMutation(id: String) {
        mutations.removeAll { it.id == id }
    }

    override suspend fun recordMutationFailure(id: String, error: String) {
        val idx = mutations.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val cur = mutations[idx]
            mutations[idx] = cur.copy(attempts = cur.attempts + 1, lastError = error)
        }
    }

    override suspend fun clearAll() {
        cache.clear()
        mutations.clear()
    }
}

class OfflineOpsCoordinatorTest {

    @Test
    fun `cache round-trip stores and restores serialized payloads`() = runTest {
        val store = MemoryHotelLocalStore()
        val coordinator = OfflineOpsCoordinator(store)
        val summary = HotelOccupancySummaryDto(totalRooms = 24, occupiedRooms = 18, availableRooms = 6)

        coordinator.saveCache("occ", HotelOccupancySummaryDto.serializer(), summary)
        val loaded = coordinator.loadCache("occ", HotelOccupancySummaryDto.serializer())

        assertEquals(summary, loaded)
    }

    @Test
    fun `enqueues non-financial mutations and replays them when online`() = runTest {
        val store = MemoryHotelLocalStore()
        var workerScheduled = 0
        val coordinator = OfflineOpsCoordinator(store, scheduleSyncWorker = { workerScheduled++ })

        coordinator.enqueueHousekeepingTaskUpdate(12L, UpdateHousekeepingTaskRequestDto(status = "completed"))
        coordinator.enqueueRoomStatusUpdate(4L, "available", "Cleaned offline")
        coordinator.enqueueRoomEndCleaning(7L)
        coordinator.enqueueMaintenanceCreate(CreateMaintenanceTicketRequestDto(roomId = 3L, title = "Broken bulb"))

        assertEquals(4, coordinator.pendingCount.value)
        assertEquals(4, workerScheduled)

        val hkRepo = FakeHousekeepingRepository()
        val roomsRepo = FakeRoomsRepository()
        val maintRepo = FakeMaintenanceRepository()

        val result = coordinator.replayPending(hkRepo, roomsRepo, maintRepo)

        assertTrue(result is SyncReplayResult.Completed)
        assertEquals(0, coordinator.pendingCount.value)
        assertEquals(1, hkRepo.updatedTasks.size)
        assertEquals(1, roomsRepo.statusCalls.size)
        assertEquals(listOf(7L), roomsRepo.endCleaningCalls)
        assertEquals("Broken bulb", maintRepo.created.single().title)
        assertFalse(maintRepo.createdIdempotencyKeys.single().isNullOrBlank())
    }

    @Test
    fun `distinguishes network failures from client 4xx validation rejections`() {
        val coordinator = OfflineOpsCoordinator(MemoryHotelLocalStore())
        assertTrue(coordinator.isQueueableNetworkFailure(IOException("timeout")))
        assertTrue(coordinator.isQueueableNetworkFailure(GenericApiException("Server error", statusCode = 503)))
        assertFalse(coordinator.isQueueableNetworkFailure(GenericApiException("Internal bug", statusCode = 500)))
        assertFalse(coordinator.isQueueableNetworkFailure(GenericApiException("Validation error", statusCode = 400)))
        assertFalse(coordinator.isQueueableNetworkFailure(GenericApiException("Unexpected response format from the server.")))
    }

    @Test
    fun `drops poison-pill mutation after max attempts and discards foreign scope mutations`() = runTest {
        val store = MemoryHotelLocalStore()
        var currentScope = LocalDataScope("https://a.example", 1L)
        val coordinator = OfflineOpsCoordinator(
            localStore = store,
            principalProvider = { currentScope },
            generationProvider = { 1L },
        )

        store.enqueueMutation(
            StoredPendingMutation(
                id = "exhausted",
                kind = PendingMutationKind.ROOM_END_CLEANING.name,
                targetId = 5L,
                payloadJson = "{}",
                createdAtMs = 1L,
                attempts = OfflineOpsCoordinator.MAX_RETRY_ATTEMPTS - 1,
                serverOrigin = "https://a.example",
                userId = 1L,
            ),
        )
        store.enqueueMutation(
            StoredPendingMutation(
                id = "foreign",
                kind = PendingMutationKind.ROOM_END_CLEANING.name,
                targetId = 6L,
                payloadJson = "{}",
                createdAtMs = 2L,
                serverOrigin = "https://b.example",
                userId = 2L,
            ),
        )
        coordinator.enqueueRoomEndCleaning(8L)

        val roomsRepo = FakeRoomsRepository()
        roomsRepo.endCleaningResult = Result.failure(GenericApiException("Gateway down", statusCode = 503))

        // First run tries id="exhausted" (fails at attempt #6 -> evicted + stops before foreign & room 8).
        val first = coordinator.replayPending(FakeHousekeepingRepository(), roomsRepo, FakeMaintenanceRepository())
        assertTrue(first is SyncReplayResult.RetryNeeded || first is SyncReplayResult.Completed)
        // Re-run with healthy backend: foreign was dropped, exhausted was dropped, room 8 replays cleanly.
        roomsRepo.endCleaningResult = Result.success(Unit)
        val second = coordinator.replayPending(FakeHousekeepingRepository(), roomsRepo, FakeMaintenanceRepository())
        assertTrue(second is SyncReplayResult.Completed)
        assertEquals(0, coordinator.pendingCount.value)
        assertFalse(6L in roomsRepo.endCleaningCalls)
        assertTrue(8L in roomsRepo.endCleaningCalls)
    }

    @Test
    fun `retains 429 rate-limited mutations for retry and drops corrupt poison-pill payloads`() = runTest {
        val store = MemoryHotelLocalStore()
        store.enqueueMutation(
            StoredPendingMutation(
                id = "bad-json",
                kind = PendingMutationKind.ROOM_STATUS_UPDATE.name,
                targetId = 1L,
                payloadJson = "{not-valid-json",
                createdAtMs = 1L,
            ),
        )
        val coordinator = OfflineOpsCoordinator(store)
        coordinator.enqueueRoomEndCleaning(7L)

        val roomsRepo = FakeRoomsRepository().apply {
            endCleaningResult = Result.failure(RateLimitedException("Too many requests", 30))
        }
        val result = coordinator.replayPending(FakeHousekeepingRepository(), roomsRepo, FakeMaintenanceRepository())

        assertTrue(result is SyncReplayResult.RetryNeeded)
        assertEquals(1, coordinator.pendingCount.value)
        assertEquals(1, store.mutations.size)
        assertEquals(7L, store.mutations.single().targetId)
    }
}
