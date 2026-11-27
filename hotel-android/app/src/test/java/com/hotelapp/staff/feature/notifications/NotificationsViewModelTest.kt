package com.hotelapp.staff.feature.notifications

import com.hotelapp.staff.data.dto.StaffNotificationItemDto
import com.hotelapp.staff.data.dto.StaffNotificationsResponseDto
import com.hotelapp.staff.testutil.FakeNotificationsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun sampleItem(
        id: Long,
        kind: String = "maintenance_critical",
        priority: String = "urgent",
        readAt: String? = null,
        actionPending: Boolean = true,
    ) = StaffNotificationItemDto(
        id = id,
        kind = kind,
        subject = "MT-$id",
        title = "Alert $id",
        body = "Body $id",
        createdAt = "2026-11-26T10:00:00Z",
        readAt = readAt,
        priority = priority,
        category = "maintenance",
        actionLabelKey = "openMaintenance",
        requiresAction = true,
        actionPending = actionPending,
    )

    @Test
    fun `initial load populates counts without emitting toast, while live arrivals emit once`() = runTest {
        val repo = FakeNotificationsRepository()
        repo.listResult = Result.success(
            StaffNotificationsResponseDto(
                unread = 1,
                urgentUnread = 1,
                actionPendingCount = 1,
                items = listOf(sampleItem(id = 10L)),
            ),
        )

        val vm = NotificationsViewModel(repo, TestFixtures.idleSocketClient())
        val collected = mutableListOf<StaffNotificationItemDto>()
        val collectJob = launch {
            vm.incomingAlerts.collect { collected.add(it) }
        }
        advanceUntilIdle()

        assertEquals(1L, vm.uiState.value.unreadCount)
        assertEquals(1L, vm.uiState.value.urgentUnreadCount)
        assertEquals(NotificationFilter.ACTION_NEEDED, vm.uiState.value.filter)
        assertTrue(collected.isEmpty())

        repo.listResult = Result.success(
            StaffNotificationsResponseDto(
                unread = 2,
                urgentUnread = 2,
                actionPendingCount = 2,
                items = listOf(
                    sampleItem(id = 11L, kind = "payment_receipt_uploaded"),
                    sampleItem(id = 10L),
                ),
            ),
        )
        vm.refresh(silent = true)
        advanceUntilIdle()

        assertEquals(1, collected.size)
        assertEquals(11L, collected.single().id)
        assertEquals(2L, vm.uiState.value.urgentUnreadCount)

        collectJob.cancel()
    }

    @Test
    fun `markRead, markAllRead, and setFilter update UI state and call repository`() = runTest {
        val repo = FakeNotificationsRepository()
        val initial = StaffNotificationsResponseDto(
            unread = 1,
            urgentUnread = 1,
            actionPendingCount = 1,
            items = listOf(sampleItem(id = 5L)),
        )
        repo.listResult = Result.success(initial)

        val vm = NotificationsViewModel(repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        vm.setFilter(NotificationFilter.ALL)
        assertEquals(NotificationFilter.ALL, vm.uiState.value.filter)

        vm.markRead(5L)
        advanceUntilIdle()
        assertEquals(listOf(5L), repo.markedReadIds)
        assertEquals(0L, vm.uiState.value.unreadCount)
        assertEquals(0L, vm.uiState.value.urgentUnreadCount)

        vm.markAllRead()
        advanceUntilIdle()
        assertEquals(1, repo.markAllReadCalls)
    }

    @Test
    fun `markRead failure rolls back optimistic unread counts on refresh`() = runTest {
        val repo = FakeNotificationsRepository()
        val initial = StaffNotificationsResponseDto(
            unread = 1,
            urgentUnread = 1,
            actionPendingCount = 1,
            items = listOf(sampleItem(id = 7L)),
        )
        repo.listResult = Result.success(initial)
        repo.markReadResult = Result.failure(IllegalStateException("Network failed"))

        val vm = NotificationsViewModel(repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        vm.markRead(7L)
        advanceUntilIdle()

        assertEquals(1L, vm.uiState.value.unreadCount)
        assertEquals(1L, vm.uiState.value.urgentUnreadCount)
        assertTrue(vm.uiState.value.items.single().readAt == null)
        assertEquals("Network failed", vm.uiState.value.error)
    }

    @Test
    fun `markAllRead failure rolls back items and counts and surfaces error`() = runTest {
        val repo = FakeNotificationsRepository()
        val initial = StaffNotificationsResponseDto(
            unread = 2,
            urgentUnread = 1,
            actionPendingCount = 2,
            items = listOf(sampleItem(id = 8L), sampleItem(id = 9L, priority = "normal")),
        )
        repo.listResult = Result.success(initial)
        repo.markAllReadResult = Result.failure(IllegalStateException("Server error"))

        val vm = NotificationsViewModel(repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        vm.markAllRead()
        advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.unreadCount)
        assertEquals(1L, vm.uiState.value.urgentUnreadCount)
        assertTrue(vm.uiState.value.items.all { it.readAt == null })
        assertEquals("Server error", vm.uiState.value.error)
    }
}
