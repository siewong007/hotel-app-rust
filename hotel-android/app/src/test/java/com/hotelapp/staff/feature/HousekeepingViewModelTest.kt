package com.hotelapp.staff.feature

import com.hotelapp.staff.feature.housekeeping.HousekeepingViewModel
import com.hotelapp.staff.testutil.FakeHousekeepingRepository
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
class HousekeepingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `updateTaskStatus and createTask forward mutations`() = runTest {
        val repo = FakeHousekeepingRepository()
        val vm = HousekeepingViewModel(TestFixtures.adminSession(), repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        vm.updateTaskStatus(12L, "completed")
        advanceUntilIdle()
        assertEquals("completed", repo.updatedTasks.single().second.status)

        var created = false
        vm.createTask(1L, "inspection", "urgent", 5L, "Check balcony") { created = true }
        advanceUntilIdle()
        assertTrue(created)
        assertEquals(1L, repo.createdTasks.single().roomId)
        assertEquals("urgent", repo.createdTasks.single().priority)
    }
}
