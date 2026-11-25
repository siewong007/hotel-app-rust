package com.hotelapp.staff.feature

import com.hotelapp.staff.data.dto.PendingPaymentEntryDto
import com.hotelapp.staff.data.dto.PendingPaymentPageDto
import com.hotelapp.staff.feature.payments.PendingPaymentsViewModel
import com.hotelapp.staff.testutil.FakePaymentsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PendingPaymentsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `approve and reject invoke repository cleanly`() = runTest {
        val repo = FakePaymentsRepository()
        repo.pendingResult = Result.success(
            PendingPaymentPageDto(
                items = listOf(
                    PendingPaymentEntryDto(
                        id = 55L,
                        bookingId = 101L,
                        bookingNumber = "BK-101",
                        guestName = "Amina",
                        amount = BigDecimal("250.00"),
                        paymentMethod = "bank_transfer",
                        status = "pending",
                        createdAt = "2026-11-25T10:00:00Z",
                        checkInDate = "2026-11-26",
                        checkOutDate = "2026-11-28",
                        roomNumber = "204",
                        bookingStatus = "pending_payment",
                    ),
                ),
                total = 1,
            ),
        )
        val vm = PendingPaymentsViewModel(repo, TestFixtures.idleSocketClient())
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.items.size)

        vm.approve(55L)
        advanceUntilIdle()
        assertEquals(listOf(55L), repo.approvedIds)

        vm.reject(55L, "Blur receipt")
        advanceUntilIdle()
        assertEquals(listOf(55L to "Blur receipt"), repo.rejectedCalls)
    }
}
