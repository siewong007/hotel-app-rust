package com.hotelapp.staff.feature

import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.data.dto.EkycDashboardMetricsDto
import com.hotelapp.staff.data.dto.PendingPaymentPageDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import com.hotelapp.staff.feature.dashboard.DashboardViewModel
import com.hotelapp.staff.testutil.FakeBookingsRepository
import com.hotelapp.staff.testutil.FakeEkycRepository
import com.hotelapp.staff.testutil.FakePaymentsRepository
import com.hotelapp.staff.testutil.FakeRoomsRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `zero-permission staff settles immediately without loading error`() = runTest {
        val emptySession = StaffSession(
            user = UserSummaryDto(id = 10L, username = "voucher_noperm"),
            roles = listOf("staff"),
            permissions = listOf("vouchers:read"),
        )
        val vm = DashboardViewModel(
            session = emptySession,
            bookingsRepository = FakeBookingsRepository(),
            roomsRepository = FakeRoomsRepository(),
            realtimeSocketClient = TestFixtures.idleSocketClient(),
            paymentsRepository = FakePaymentsRepository(),
            ekycRepository = FakeEkycRepository(),
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isRefreshing)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `ekyc_reviewer loads eKYC metrics cleanly and surfaces errors if failing`() = runTest {
        val ekycRepo = FakeEkycRepository()
        val reviewerSession = StaffSession(
            user = UserSummaryDto(id = 11L, username = "ekyc_reviewer"),
            roles = listOf("ekyc_reviewer"),
            permissions = listOf("ekyc:read", "ekyc:review", "ekyc:approve"),
        )
        val failingEkycRepo = object : com.hotelapp.staff.data.repository.EkycRepository by ekycRepo {
            override suspend fun dashboard(): Result<EkycDashboardMetricsDto> =
                Result.failure(GenericApiException("Server unreachable"))
        }

        val vm = DashboardViewModel(
            session = reviewerSession,
            bookingsRepository = FakeBookingsRepository(),
            roomsRepository = FakeRoomsRepository(),
            realtimeSocketClient = TestFixtures.idleSocketClient(),
            paymentsRepository = FakePaymentsRepository(),
            ekycRepository = failingEkycRepo,
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Server unreachable", vm.uiState.value.error)
    }

    @Test
    fun `finance auditor with payments_read loads pending payments count without bookings permission`() = runTest {
        val payRepo = FakePaymentsRepository().apply {
            pendingResult = Result.success(PendingPaymentPageDto(items = emptyList(), total = 7L))
        }
        val financeSession = StaffSession(
            user = UserSummaryDto(id = 12L, username = "finance_mei"),
            roles = listOf("auditor"),
            permissions = listOf("payments:read", "payments:create", "ekyc:read", "ledgers:read"),
        )

        val vm = DashboardViewModel(
            session = financeSession,
            bookingsRepository = FakeBookingsRepository(),
            roomsRepository = FakeRoomsRepository(),
            realtimeSocketClient = TestFixtures.idleSocketClient(),
            paymentsRepository = payRepo,
            ekycRepository = FakeEkycRepository(),
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
        assertEquals(7L, vm.uiState.value.pendingPaymentsCount)
        assertEquals(0L, vm.uiState.value.pendingEkycCount)
    }
}
