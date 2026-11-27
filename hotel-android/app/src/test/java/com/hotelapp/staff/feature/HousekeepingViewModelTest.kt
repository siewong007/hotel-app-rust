package com.hotelapp.staff.feature

import com.hotelapp.staff.data.dto.HousekeepingBoardResponseDto
import com.hotelapp.staff.data.dto.HousekeepingBoardRoomDto
import com.hotelapp.staff.data.dto.HousekeepingTaskDto
import com.hotelapp.staff.data.dto.HousekeepingTaskListResponseDto
import com.hotelapp.staff.feature.housekeeping.HousekeepingViewModel
import com.hotelapp.staff.testutil.FakeHousekeepingRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `tasks failure surfaces tasksError even when board succeeds`() = runTest {
        val repo = FakeHousekeepingRepository()
        repo.boardResult = Result.success(
            HousekeepingBoardResponseDto(
                rooms = listOf(HousekeepingBoardRoomDto(1L, "101", "Standard", 1, "dirty")),
            ),
        )
        repo.tasksResult = Result.failure(IllegalStateException("Tasks API failed"))

        val vm = HousekeepingViewModel(TestFixtures.adminSession(), repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.boardRooms.size)
        assertEquals("Tasks API failed", vm.uiState.value.tasksError)
    }

    @Test
    fun `refresh resets isLoadingMoreTasks and preserves existing tasks on pull-to-refresh failure`() = runTest {
        val repo = FakeHousekeepingRepository()
        val task = HousekeepingTaskDto(
            id = 91L,
            roomId = 1L,
            roomNumber = "101",
            roomType = "Standard",
            taskType = "cleaning",
            priority = "normal",
            status = "pending",
            taskDate = "2026-11-26",
            createdAt = "2026-11-26T08:00:00Z",
            updatedAt = "2026-11-26T08:00:00Z",
        )
        repo.tasksResult = Result.success(HousekeepingTaskListResponseDto(items = listOf(task), total = 5L, page = 1L))
        val vm = HousekeepingViewModel(TestFixtures.adminSession(), repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        assertEquals(listOf(91L), vm.uiState.value.tasks.map { it.id })

        repo.tasksResult = Result.failure(IllegalStateException("Network timeout"))
        vm.loadMoreTasks()
        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoadingMoreTasks)
        assertEquals(listOf(91L), vm.uiState.value.tasks.map { it.id })
        assertEquals("Network timeout", vm.uiState.value.tasksError)
    }
}
