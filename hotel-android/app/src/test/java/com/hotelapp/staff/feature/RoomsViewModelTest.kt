package com.hotelapp.staff.feature

import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.core.storage.HotelLocalStore
import com.hotelapp.staff.core.storage.StoredCacheEntry
import com.hotelapp.staff.core.storage.StoredPendingMutation
import com.hotelapp.staff.core.sync.OfflineOpsCoordinator
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto
import com.hotelapp.staff.feature.rooms.RoomsViewModel
import com.hotelapp.staff.testutil.FakeHousekeepingRepository
import com.hotelapp.staff.testutil.FakeMaintenanceRepository
import com.hotelapp.staff.testutil.FakeRoomsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RoomsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `endCleaning and updateRoomStatus call repository and set notice`() = runTest {
        val repo = FakeRoomsRepository()
        val vm = RoomsViewModel(repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.rooms.size)

        vm.endCleaning(2L)
        advanceUntilIdle()
        assertEquals(listOf(2L), repo.endCleaningCalls)
        assertEquals("Room cleaning marked complete.", vm.uiState.value.actionNotice)

        vm.updateRoomStatus(1L, "available", "Inspected")
        advanceUntilIdle()
        assertEquals(Triple(1L, "available", "Inspected"), repo.statusCalls.first())
    }

    @Test
    fun `openRoomDetail falls back to room summary when detail endpoint returns 403`() = runTest {
        val repo = FakeRoomsRepository()
        repo.roomsResult = Result.success(
            listOf(
                RoomWithOccupancyDto(
                    id = 9L,
                    roomNumber = "209",
                    roomType = "Suite",
                    status = "available",
                    isOccupied = true,
                    available = false,
                    notes = "VIP setup",
                ),
            ),
        )
        repo.detailResult = Result.failure(GenericApiException("Forbidden", statusCode = 403))

        val vm = RoomsViewModel(repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        vm.openRoomDetail(9L)
        advanceUntilIdle()

        val detail = vm.uiState.value.selectedRoomDetail
        assertNotNull(detail)
        assertEquals("209", detail?.roomNumber)
        assertEquals("occupied", detail?.status)
        assertEquals("VIP setup", detail?.statusNotes)
        assertFalse(vm.uiState.value.isDetailOfflineFallback)
        assertNull(vm.uiState.value.sheetError)
    }

    @Test
    fun `natural room sorting orders numeric values correctly and offline detail plus endMaintenance fallbacks work`() = runTest {
        val store = object : HotelLocalStore {
            val cache = mutableMapOf<String, StoredCacheEntry>()
            val queue = mutableListOf<StoredPendingMutation>()
            override suspend fun putCache(key: String, payloadJson: String, nowMs: Long) { cache[key] = StoredCacheEntry(key, payloadJson, nowMs) }
            override suspend fun getCache(key: String): StoredCacheEntry? = cache[key]
            override suspend fun enqueueMutation(mutation: StoredPendingMutation) { queue.add(mutation) }
            override suspend fun listPendingMutations(): List<StoredPendingMutation> = queue.toList()
            override suspend fun deleteMutation(id: String) { queue.removeAll { it.id == id } }
            override suspend fun recordMutationFailure(id: String, error: String) {}
            override suspend fun clearAll() { cache.clear(); queue.clear() }
        }
        val coordinator = OfflineOpsCoordinator(store)
        val repo = FakeRoomsRepository()
        repo.roomsResult = Result.success(
            listOf(
                RoomWithOccupancyDto(id = 1L, roomNumber = "100", roomType = "Suite", status = "available"),
                RoomWithOccupancyDto(id = 2L, roomNumber = "20", roomType = "Standard", status = "maintenance"),
                RoomWithOccupancyDto(id = 3L, roomNumber = "101B", roomType = "Deluxe", status = "dirty"),
                RoomWithOccupancyDto(id = 4L, roomNumber = "101A", roomType = "Deluxe", status = "available"),
            ),
        )
        repo.detailResult = Result.failure(IOException("Offline"))
        repo.endMaintenanceResult = Result.failure(IOException("Offline"))

        val vm = RoomsViewModel(repo, TestFixtures.idleSocketClient(), offlineOps = coordinator)
        advanceUntilIdle()

        assertEquals(listOf("20", "100", "101A", "101B"), vm.uiState.value.rooms.map { it.roomNumber })

        vm.openRoomDetail(2L)
        advanceUntilIdle()
        assertEquals("20", vm.uiState.value.selectedRoomDetail?.roomNumber)
        assertTrue(vm.uiState.value.isDetailOfflineFallback)
        assertNull(vm.uiState.value.sheetError)

        vm.endMaintenance(2L)
        advanceUntilIdle()
        assertEquals(1, coordinator.pendingCount.value)
        assertEquals("available", vm.uiState.value.rooms.first { it.id == 2L }.status)

        repo.endMaintenanceResult = Result.success(Unit)
        coordinator.replayPending(FakeHousekeepingRepository(), repo, FakeMaintenanceRepository())
        assertEquals(listOf(2L, 2L), repo.endMaintenanceCalls)
        assertEquals(0, coordinator.pendingCount.value)
    }
}
