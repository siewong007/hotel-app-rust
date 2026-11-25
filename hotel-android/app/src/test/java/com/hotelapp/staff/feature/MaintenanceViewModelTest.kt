package com.hotelapp.staff.feature

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

        var done = false
        vm.createTicket(1L, "Thermostat fault", "hvac", "critical", "Room too warm") { done = true }
        advanceUntilIdle()
        assertTrue(done)
        assertEquals("Thermostat fault", repo.created.single().title)
    }
}
