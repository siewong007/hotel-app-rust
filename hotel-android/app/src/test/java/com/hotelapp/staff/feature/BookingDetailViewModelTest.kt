package com.hotelapp.staff.feature

import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.feature.bookings.BookingDetailViewModel
import com.hotelapp.staff.testutil.FakeBookingsRepository
import com.hotelapp.staff.testutil.FakePaymentsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

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
            icNumber = "900101-14-5555",
            paymentAmount = "180.00",
            paymentMethod = "card",
            onSuccess = { succeeded = true },
        )
        advanceUntilIdle()

        assertTrue(succeeded)
        assertFalse(vm.uiState.value.isSubmittingAction)
        assertEquals("Amina", repo.lastCheckInRequest?.guestUpdate?.firstName)
        assertEquals("900101-14-5555", repo.lastCheckInRequest?.guestUpdate?.icNumber)
        assertEquals(180.0, repo.lastCheckInRequest?.paymentRecord?.amount ?: 0.0, 0.001)
        assertEquals("card", repo.lastCheckInRequest?.paymentRecord?.paymentMethod)
    }

    @Test
    fun `releaseHold rejects blank or short reason locally`() = runTest {
        val repo = FakeBookingsRepository()
        val vm = BookingDetailViewModel(101L, repo)
        advanceUntilIdle()

        vm.releaseHold("   ") {}
        assertEquals("A reason is required to release an unpaid hold.", vm.uiState.value.actionError)

        vm.releaseHold("abc") {}
        assertEquals("Enter at least 4 characters to release an unpaid hold.", vm.uiState.value.actionError)
    }

    @Test
    fun `effectiveSettlementMethods mirrors direct-website settlement lock rules`() {
        fun booking(
            source: String? = "walk_in",
            channelType: String? = null,
            methods: List<String>? = null,
            payoutRecorded: Boolean? = null,
        ) = BookingWithDetailsDto(
            id = 1L,
            bookingNumber = "B-1",
            guestId = 1L,
            guestName = "Test Guest",
            roomId = 1L,
            roomNumber = "101",
            roomType = "Standard Room",
            checkInDate = "2026-11-26",
            checkOutDate = "2026-11-27",
            status = "confirmed",
            source = source,
            bookingChannelType = channelType,
            bookingChannelSettlementMethods = methods,
            channelPayoutRecorded = payoutRecorded,
        )

        assertNull(booking(source = "walk_in").effectiveSettlementMethods())
        assertEquals(
            listOf("bank_transfer", "paypal"),
            booking(source = "website", methods = null).effectiveSettlementMethods(),
        )
        assertEquals(
            listOf("bank_transfer", "paypal"),
            booking(source = "website", methods = emptyList()).effectiveSettlementMethods(),
        )
        assertEquals(
            listOf("bank_transfer", "paypal"),
            booking(source = "online", channelType = "website", methods = emptyList()).effectiveSettlementMethods(),
        )
        assertNull(
            booking(source = "website", methods = listOf("Bank Transfer", "PayPal"), payoutRecorded = true)
                .effectiveSettlementMethods(),
        )
        assertEquals(
            listOf("Booking.com"),
            booking(source = "online", channelType = "ota", methods = listOf("Booking.com"))
                .effectiveSettlementMethods(),
        )
    }

    @Test
    fun `setBookingId resets state and loads new booking`() = runTest {
        val repo = FakeBookingsRepository()
        val vm = BookingDetailViewModel(101L, repo)
        advanceUntilIdle()
        assertEquals(101L, vm.uiState.value.booking?.id)

        repo.detailResult = Result.success(TestFixtures.sampleBooking(id = 202L, guestName = "Farah Tan"))
        vm.setBookingId(202L)
        advanceUntilIdle()

        assertEquals(202L, vm.uiState.value.booking?.id)
        assertEquals("Farah Tan", vm.uiState.value.booking?.guestName)
        assertNull(vm.uiState.value.actionError)
    }

    @Test
    fun `checkIn aborts on invalid payment amount without calling repository`() = runTest {
        val repo = FakeBookingsRepository()
        val vm = BookingDetailViewModel(101L, repo)
        advanceUntilIdle()

        var succeeded = false
        vm.checkIn(
            firstName = "Amina",
            lastName = "Rahman",
            phone = "+60123456789",
            icNumber = "",
            paymentAmount = "12.345",
            paymentMethod = "cash",
            onSuccess = { succeeded = true },
        )
        advanceUntilIdle()

        assertFalse(succeeded)
        assertNull(repo.lastCheckInRequest)
        assertTrue(vm.uiState.value.actionError?.contains("valid payment amount", ignoreCase = true) == true)
    }

    @Test
    fun `deposit resolution validates remaining held deposit and reactivate routes single vs group`() = runTest {
        val bookingsRepo = FakeBookingsRepository()
        val paymentsRepo = FakePaymentsRepository()
        val vm = BookingDetailViewModel(101L, bookingsRepo, paymentsRepo, canReadPayments = true)
        advanceUntilIdle()

        // Remaining held = 100 - 40 - 30 = 30
        val collected = BigDecimal("100.00")
        val refunded = BigDecimal("40.00")
        val forfeited = BigDecimal("30.00")
        val remainingHeld = (collected - refunded - forfeited).coerceAtLeast(BigDecimal.ZERO)

        var refundedOk = false
        vm.refundDeposit(BigDecimal("35.00"), remainingHeld, "cash", "Checkout refund", "", { refundedOk = true })
        advanceUntilIdle()
        assertFalse(refundedOk)
        assertTrue(vm.uiState.value.actionError?.contains("30.00") == true)

        vm.refundDeposit(BigDecimal("30.00"), remainingHeld, "cash", "Checkout refund", "", { refundedOk = true })
        advanceUntilIdle()
        assertTrue(refundedOk)

        bookingsRepo.detailResult = Result.success(TestFixtures.sampleBooking(id = 101L, status = "voided"))
        vm.refresh()
        advanceUntilIdle()
        vm.reactivateBooking()
        advanceUntilIdle()
        assertEquals(listOf(101L), bookingsRepo.reactivateCalls)

        bookingsRepo.detailResult = Result.success(TestFixtures.sampleBooking(id = 101L, status = "voided").copy(groupNumber = "GRP-10"))
        vm.refresh()
        advanceUntilIdle()
        vm.reactivateBooking()
        advanceUntilIdle()
        assertEquals(listOf(101L), bookingsRepo.groupReactivateCalls)
    }
}
