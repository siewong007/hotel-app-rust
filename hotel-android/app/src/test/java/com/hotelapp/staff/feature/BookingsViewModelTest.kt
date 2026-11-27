package com.hotelapp.staff.feature

import com.hotelapp.staff.data.dto.PaginatedResponseDto
import com.hotelapp.staff.feature.bookings.BookingsViewModel
import com.hotelapp.staff.testutil.FakeBookingsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial load and pagination append distinct items`() = runTest {
        val repo = FakeBookingsRepository()
        repo.listHandler = { page, _, _ ->
            Result.success(
                PaginatedResponseDto(
                    data = listOf(TestFixtures.sampleBooking(id = page * 10)),
                    total = 2,
                    page = page,
                    pageSize = 1,
                ),
            )
        }
        val vm = BookingsViewModel("all", repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(10L, vm.uiState.value.items.first().id)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.items.size)
        assertEquals(listOf(10L, 20L), vm.uiState.value.items.map { it.id })
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `setSearch debounces before querying repository`() = runTest {
        val queries = mutableListOf<String?>()
        val repo = FakeBookingsRepository()
        repo.listHandler = { page, search, _ ->
            queries.add(search)
            Result.success(PaginatedResponseDto(data = emptyList(), total = 0, page = page))
        }
        val vm = BookingsViewModel("arriving", repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()
        queries.clear()

        vm.setSearch("A")
        advanceTimeBy(150)
        vm.setSearch("Amina")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(listOf<String?>("Amina"), queries)
    }

    @Test
    fun `hasMore stops pagination when total reached or page returns empty`() = runTest {
        val repo = FakeBookingsRepository()
        repo.listHandler = { page, _, _ ->
            Result.success(
                PaginatedResponseDto(
                    data = if (page == 1L) listOf(TestFixtures.sampleBooking(id = 1L)) else emptyList(),
                    total = 1L,
                    page = page,
                ),
            )
        }
        val vm = BookingsViewModel("all", repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasMore)

        repo.listHandler = { page, _, _ ->
            Result.success(
                PaginatedResponseDto(
                    data = if (page == 1L) listOf(TestFixtures.sampleBooking(id = 1L)) else emptyList(),
                    total = 5L,
                    page = page,
                ),
            )
        }
        vm.refresh()
        advanceUntilIdle()
        org.junit.Assert.assertTrue(vm.uiState.value.hasMore)

        vm.loadMore()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasMore)
    }
}
