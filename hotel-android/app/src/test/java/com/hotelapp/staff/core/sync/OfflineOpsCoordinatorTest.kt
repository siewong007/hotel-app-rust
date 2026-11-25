package com.hotelapp.staff.core.sync

import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.core.storage.HotelLocalStore
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
        val coordinator = OfflineOpsCoordinator(store) { workerScheduled++ }

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
    }

    @Test
    fun `distinguishes network failures from client 4xx validation rejections`() {
        val coordinator = OfflineOpsCoordinator(MemoryHotelLocalStore())
        assertTrue(coordinator.isQueueableNetworkFailure(IOException("timeout")))
        assertTrue(coordinator.isQueueableNetworkFailure(GenericApiException("Server error", statusCode = 503)))
        assertFalse(coordinator.isQueueableNetworkFailure(GenericApiException("Validation error", statusCode = 400)))
        assertFalse(coordinator.isQueueableNetworkFailure(GenericApiException("Unexpected response format from the server.")))
    }
}
