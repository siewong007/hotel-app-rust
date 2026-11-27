package com.hotelapp.staff.feature

import com.hotelapp.staff.data.dto.MaintenanceTicketDto
import com.hotelapp.staff.data.dto.MaintenanceTicketListResponseDto
import com.hotelapp.staff.feature.maintenance.MaintenanceViewModel
import com.hotelapp.staff.testutil.FakeHousekeepingRepository
import com.hotelapp.staff.testutil.FakeMaintenanceRepository
import com.hotelapp.staff.testutil.FakeRoomsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `createTicket validates non-blank title and submits valid tickets`() = runTest {
        val repo = FakeMaintenanceRepository()
        val vm = MaintenanceViewModel(
            session = TestFixtures.adminSession(),
            repository = repo,
            housekeepingRepository = FakeHousekeepingRepository(),
            roomsRepository = FakeRoomsRepository(),
            realtimeSocketClient = TestFixtures.idleSocketClient(),
        )
        advanceUntilIdle()

        vm.createTicket(1L, "   ", "hvac", "high", "") {}
        assertEquals("Title is required.", vm.uiState.value.sheetError)
        assertTrue(repo.created.isEmpty())

        vm.clearSheetError()
        assertNull(vm.uiState.value.sheetError)

        var done = false
        vm.createTicket(1L, "Thermostat fault", "hvac", "critical", "Room too warm") { done = true }
        advanceUntilIdle()
        assertTrue(done)
        assertEquals("Thermostat fault", repo.created.single().title)
    }

    @Test
    fun `loadMore appends maintenance ticket pages without duplicating`() = runTest {
        val t1 = MaintenanceTicketDto(
            id = 1L, roomNumber = "101", ticketNumber = "MT-1", title = "Tap leak",
            category = "plumbing", priority = "medium", status = "open",
            createdAt = "2026-11-25T08:00:00Z", updatedAt = "2026-11-25T08:00:00Z",
        )
        val t2 = t1.copy(id = 2L, ticketNumber = "MT-2", title = "Bulb out")
        val repo = FakeMaintenanceRepository()
        repo.listResult = Result.success(MaintenanceTicketListResponseDto(items = listOf(t1), total = 2, page = 1))

        val vm = MaintenanceViewModel(
            session = TestFixtures.adminSession(),
            repository = repo,
            housekeepingRepository = FakeHousekeepingRepository(),
            roomsRepository = FakeRoomsRepository(),
            realtimeSocketClient = TestFixtures.idleSocketClient(),
        )
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.tickets.size)

        repo.listResult = Result.success(MaintenanceTicketListResponseDto(items = listOf(t2), total = 2, page = 2))
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.uiState.value.tickets.map { it.id })
        assertTrue(vm.uiState.value.endReached)
    }
}
