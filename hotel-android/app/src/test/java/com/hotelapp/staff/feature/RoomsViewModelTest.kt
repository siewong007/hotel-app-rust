package com.hotelapp.staff.feature

import com.hotelapp.staff.feature.rooms.RoomsViewModel
import com.hotelapp.staff.testutil.FakeRoomsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
}
