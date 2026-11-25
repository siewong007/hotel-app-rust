package com.hotelapp.staff.feature

import com.hotelapp.staff.data.dto.GlobalSearchResponseDto
import com.hotelapp.staff.data.dto.SearchGroupDto
import com.hotelapp.staff.data.dto.SearchResultItemDto
import com.hotelapp.staff.feature.search.SearchViewModel
import com.hotelapp.staff.testutil.FakeSearchRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `short queries clear results without hitting repository`() = runTest {
        val repo = FakeSearchRepository()
        var hits = 0
        repo.handler = { q, _ ->
            hits++
            Result.success(GlobalSearchResponseDto(query = q))
        }
        val vm = SearchViewModel(repo, null)
        vm.setQuery("a")
        advanceUntilIdle()

        assertEquals(0, hits)
        assertFalse(vm.uiState.value.hasSearched)
        assertTrue(vm.uiState.value.groups.isEmpty())
    }

    @Test
    fun `debounced search filters non-empty groups and switches type`() = runTest {
        val repo = FakeSearchRepository()
        repo.handler = { q, type ->
            Result.success(
                GlobalSearchResponseDto(
                    query = q,
                    groups = listOf(
                        SearchGroupDto(
                            type = type ?: "bookings",
                            label = "Bookings",
                            results = listOf(SearchResultItemDto(11L, "BK-11", "Room 101", "/bookings/11")),
                        ),
                        SearchGroupDto(type = "rooms", label = "Rooms", results = emptyList()),
                    ),
                ),
            )
        }
        val vm = SearchViewModel(repo, null)
        vm.setQuery("BK-11")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasSearched)
        assertEquals(1, vm.uiState.value.groups.size)
        assertEquals("BK-11", vm.uiState.value.groups.first().results.first().title)

        vm.setType("guests")
        advanceUntilIdle()
        assertEquals("guests", vm.uiState.value.groups.first().type)
    }

    @Test
    fun `saveCurrentSearchToHistory does not re-trigger network search`() = runTest {
        val repo = FakeSearchRepository()
        var hits = 0
        repo.handler = { q, _ ->
            hits++
            Result.success(GlobalSearchResponseDto(query = q))
        }
        val vm = SearchViewModel(repo, null)
        vm.setQuery("Amina")
        advanceTimeBy(350)
        advanceUntilIdle()
        assertEquals(1, hits)

        vm.saveCurrentSearchToHistory()
        advanceUntilIdle()
        assertEquals(1, hits)
    }
}
