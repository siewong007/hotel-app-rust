package com.hotelapp.staff.feature

import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.data.dto.EkycAdminListResponseDto
import com.hotelapp.staff.data.dto.EkycApplicationDetailDto
import com.hotelapp.staff.data.dto.EkycApplicationSummaryDto
import com.hotelapp.staff.data.dto.EkycDashboardMetricsDto
import com.hotelapp.staff.data.dto.EkycReasonCodeDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import com.hotelapp.staff.feature.ekyc.EkycViewModel
import com.hotelapp.staff.testutil.FakeEkycRepository
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
class EkycViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `applyAction sends expected version and updates detail`() = runTest {
        val repo = FakeEkycRepository()
        val summary = EkycApplicationSummaryDto(
            id = 8L,
            applicationId = "EKYC-8",
            status = "pending_review",
            fullName = "Zainab Ali",
            riskLevel = "low",
            riskScore = 12,
            version = 3,
        )
        repo.listResult = Result.success(
            EkycAdminListResponseDto(
                data = listOf(summary),
                metrics = EkycDashboardMetricsDto(totalSubmitted = 1, pendingReview = 1),
                total = 1,
            ),
        )
        val approvedDetail = EkycApplicationDetailDto(summary = summary.copy(status = "approved", version = 4))
        repo.detailResult = Result.success(EkycApplicationDetailDto(summary = summary))
        repo.actionResult = Result.success(approvedDetail)

        val vm = EkycViewModel(TestFixtures.adminSession(), repo)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.items.size)

        vm.openDetail(8L)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.selectedDetail?.summary?.version)

        vm.applyAction(id = 8L, expectedVersion = 3, action = "approve", selfCheckinEnabled = true)
        advanceUntilIdle()

        val sent = repo.actionsTaken.single()
        assertEquals(8L, sent.first)
        assertEquals("approve", sent.second.action)
        assertEquals(3, sent.second.expectedVersion)
        assertEquals("approved", vm.uiState.value.selectedDetail?.summary?.status)
    }

    @Test
    fun `read-only ekyc viewer skips reasonCodes and supports loadMore`() = runTest {
        val repo = FakeEkycRepository()
        repo.codesResult = Result.success(
            listOf(EkycReasonCodeDto(code = "DOC_OK", label = "OK", category = "approval", isActive = true)),
        )
        repo.listResult = Result.success(
            EkycAdminListResponseDto(
                data = listOf(EkycApplicationSummaryDto(id = 1L, applicationId = "EKYC-1", status = "pending_manual_review")),
                total = 2,
                page = 1,
            ),
        )
        val readonlySession = StaffSession(
            user = UserSummaryDto(id = 9L, username = "support_viewer"),
            roles = listOf("support_readonly"),
            permissions = listOf("ekyc:read", "support:read"),
        )

        val vm = EkycViewModel(readonlySession, repo)
        advanceUntilIdle()

        assertTrue("Read-only viewer must not fetch reason codes", vm.uiState.value.reasonCodes.isEmpty())
        assertEquals(1, vm.uiState.value.items.size)

        repo.listResult = Result.success(
            EkycAdminListResponseDto(
                data = listOf(EkycApplicationSummaryDto(id = 2L, applicationId = "EKYC-2", status = "approved")),
                total = 2,
                page = 2,
            ),
        )
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.items.size)
        assertEquals(2L, vm.uiState.value.page)
    }
}
