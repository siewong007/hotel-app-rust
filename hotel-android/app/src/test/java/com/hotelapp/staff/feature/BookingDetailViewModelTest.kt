package com.hotelapp.staff.feature

import com.hotelapp.staff.feature.bookings.BookingDetailViewModel
import com.hotelapp.staff.testutil.FakeBookingsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `checkIn forwards guest update and optional payment`() = runTest {
        val repo = FakeBookingsRepository()
        val vm = BookingDetailViewModel(101L, repo)
        advanceUntilIdle()

        var succeeded = false
        vm.checkIn(
            firstName = "Amina",
            lastName = "Rahman",
            phone = "+60123456789",
            paymentAmount = "180.00",
            paymentMethod = "card",
            onSuccess = { succeeded = true },
        )
        advanceUntilIdle()

        assertTrue(succeeded)
        assertFalse(vm.uiState.value.isSubmittingAction)
        assertEquals("Amina", repo.lastCheckInRequest?.guestUpdate?.firstName)
        assertEquals(180.0, repo.lastCheckInRequest?.paymentRecord?.amount ?: 0.0, 0.001)
        assertEquals("card", repo.lastCheckInRequest?.paymentRecord?.paymentMethod)
    }

    @Test
    fun `releaseHold rejects blank reason locally`() = runTest {
        val repo = FakeBookingsRepository()
        val vm = BookingDetailViewModel(101L, repo)
        advanceUntilIdle()

        vm.releaseHold("   ") {}
        assertEquals("A reason is required to release an unpaid hold.", vm.uiState.value.actionError)
    }
}
