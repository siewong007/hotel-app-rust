package com.hotelapp.staff.testutil

import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.AssignableStaffMemberDto
import com.hotelapp.staff.data.dto.BookingBoardSummaryDto
import com.hotelapp.staff.data.dto.BookingDto
import com.hotelapp.staff.data.dto.BookingGroupDetailDto
import com.hotelapp.staff.data.dto.BookingTimelineEntryDto
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.dto.CheckInAdvisoryDto
import com.hotelapp.staff.data.dto.CheckInRequestDto
import com.hotelapp.staff.data.dto.CreateBookingRequestDto
import com.hotelapp.staff.data.dto.CreateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.dto.CreateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.EkycAdminListResponseDto
import com.hotelapp.staff.data.dto.EkycApplicationDetailDto
import com.hotelapp.staff.data.dto.EkycDashboardMetricsDto
import com.hotelapp.staff.data.dto.EkycReasonCodeDto
import com.hotelapp.staff.data.dto.EkycReviewActionRequestDto
import com.hotelapp.staff.data.dto.EkycSensitiveRevealResponseDto
import com.hotelapp.staff.data.dto.ForfeitDepositRequestDto
import com.hotelapp.staff.data.dto.GlobalSearchResponseDto
import com.hotelapp.staff.data.dto.GroupPayRequestDto
import com.hotelapp.staff.data.dto.HousekeepingBoardResponseDto
import com.hotelapp.staff.data.dto.HousekeepingTaskDto
import com.hotelapp.staff.data.dto.HousekeepingTaskListResponseDto
import com.hotelapp.staff.data.dto.MaintenanceTicketDto
import com.hotelapp.staff.data.dto.MaintenanceTicketListResponseDto
import com.hotelapp.staff.data.dto.NewReservationsResponseDto
import com.hotelapp.staff.data.dto.PaginatedResponseDto
import com.hotelapp.staff.data.dto.PasskeyAllowCredentialDto
import com.hotelapp.staff.data.dto.PasskeyLoginFinishRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartResponseDto
import com.hotelapp.staff.data.dto.PasskeySecondFactorFinishRequestDto
import com.hotelapp.staff.data.dto.PaymentActionResponseDto
import com.hotelapp.staff.data.dto.PaymentReceiptFile
import com.hotelapp.staff.data.dto.PendingPaymentPageDto
import com.hotelapp.staff.data.dto.RecordPaymentRequestDto
import com.hotelapp.staff.data.dto.RefundDepositRequestDto
import com.hotelapp.staff.data.dto.RoomDetailedStatusDto
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto
import com.hotelapp.staff.data.dto.StaffNotificationsResponseDto
import com.hotelapp.staff.data.dto.UpdateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.dto.UpdateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.UserSessionInfoDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import com.hotelapp.staff.data.repository.AuthRepository
import com.hotelapp.staff.data.repository.BookingsRepository
import com.hotelapp.staff.data.repository.EkycRepository
import com.hotelapp.staff.data.repository.HousekeepingRepository
import com.hotelapp.staff.data.repository.MaintenanceRepository
import com.hotelapp.staff.data.repository.NotificationsRepository
import com.hotelapp.staff.data.repository.PaymentsRepository
import com.hotelapp.staff.data.repository.RoomsRepository
import com.hotelapp.staff.data.repository.SearchRepository
import okhttp3.OkHttpClient
import java.math.BigDecimal

object TestFixtures {
    fun idleSocketClient(): RealtimeSocketClient = RealtimeSocketClient(
        client = OkHttpClient(),
        baseUrlProvider = { "http://127.0.0.1:3030" },
        sessionManager = SessionManager(),
    )

    fun adminSession(): StaffSession = StaffSession(
        user = UserSummaryDto(
            id = 1L,
            username = "admin",
            fullName = "Admin Staff",
            isSuperAdmin = true,
        ),
        roles = listOf("admin"),
        permissions = listOf(
            "bookings:read", "bookings:create", "bookings:update",
            "rooms:read", "rooms:update",
            "housekeeping:read", "housekeeping:create", "housekeeping:update",
            "maintenance:read", "maintenance:write",
            "guests:read",
            "payments:read", "payments:create", "payments:approve",
            "ekyc:read", "ekyc:review", "ekyc:approve", "ekyc:reject", "ekyc:request_resubmission", "ekyc:escalate",
        ),
    )

    fun sampleBooking(
        id: Long = 101L,
        status: String = "confirmed",
        guestName: String = "Amina Rahman",
        roomNumber: String = "204",
    ) = BookingWithDetailsDto(
        id = id,
        bookingNumber = "BK-$id",
        guestId = 10L,
        guestName = guestName,
        guestEmail = "amina@example.com",
        roomId = 20L,
        roomNumber = roomNumber,
        roomType = "Deluxe King",
        checkInDate = "2026-11-25",
        checkOutDate = "2026-11-27",
        totalAmount = BigDecimal("480.00"),
        balanceDue = BigDecimal("180.00"),
        totalPaid = BigDecimal("300.00"),
        status = status,
        paymentStatus = "partial",
    )
}

class FakeAuthRepository : AuthRepository {
    var nextLoginResult: Result<StaffSession> = Result.success(TestFixtures.adminSession())
    var nextPasskey2faStartResult: Result<PasskeyLoginStartResponseDto> = Result.success(
        PasskeyLoginStartResponseDto(
            challenge = "chal",
            allowCredentials = listOf(PasskeyAllowCredentialDto(id = "c1")),
        ),
    )
    var nextPasskey2faFinishResult: Result<StaffSession> =
        Result.failure(UnsupportedOperationException("Not configured in test"))
    var lastLoginArgs: Triple<String, String, String?>? = null
    override suspend fun restoreSession(): Boolean = false
    override suspend fun login(username: String, password: String, totpCode: String?): Result<StaffSession> {
        lastLoginArgs = Triple(username, password, totpCode)
        return nextLoginResult
    }
    override suspend fun passkeyLoginStart(username: String): Result<PasskeyLoginStartResponseDto> =
        Result.failure(UnsupportedOperationException("Not configured in test"))
    override suspend fun passkeyLoginFinish(request: PasskeyLoginFinishRequestDto): Result<StaffSession> =
        Result.failure(UnsupportedOperationException("Not configured in test"))
    override suspend fun passkeySecondFactorStart(mfaToken: String): Result<PasskeyLoginStartResponseDto> =
        nextPasskey2faStartResult
    override suspend fun passkeySecondFactorFinish(request: PasskeySecondFactorFinishRequestDto): Result<StaffSession> =
        nextPasskey2faFinishResult
    override suspend fun logout(): Result<Unit> = Result.success(Unit)
    override suspend fun listSessions(): Result<List<UserSessionInfoDto>> = Result.success(emptyList())
    override suspend fun revokeSession(sessionId: String): Result<Unit> = Result.success(Unit)
}

class FakeBookingsRepository : BookingsRepository {
    var listHandler: (Long, String?, String?) -> Result<PaginatedResponseDto<BookingWithDetailsDto>> = { page, _, _ ->
        Result.success(PaginatedResponseDto(data = listOf(TestFixtures.sampleBooking(id = page)), total = 2, page = page, pageSize = 25))
    }
    var detailResult: Result<BookingWithDetailsDto> = Result.success(TestFixtures.sampleBooking())
    var timelineResult: Result<List<BookingTimelineEntryDto>> = Result.success(emptyList())
    var advisoryResult: Result<CheckInAdvisoryDto> = Result.success(CheckInAdvisoryDto())
    var groupDetailResult: Result<BookingGroupDetailDto?> = Result.success(null)
    var checkInResult: Result<Unit> = Result.success(Unit)
    var checkOutResult: Result<Unit> = Result.success(Unit)
    var voidResult: Result<Unit> = Result.success(Unit)
    var releaseResult: Result<Unit> = Result.success(Unit)
    var reactivateResult: Result<Unit> = Result.success(Unit)
    var groupReactivateResult: Result<Unit> = Result.success(Unit)
    var lastCheckInRequest: CheckInRequestDto? = null
    var lastVoidReason: String? = null
    val reactivateCalls = mutableListOf<Long>()
    val groupReactivateCalls = mutableListOf<Long>()

    override suspend fun summary(): Result<BookingBoardSummaryDto> = Result.success(BookingBoardSummaryDto())
    override suspend fun newReservations(): Result<NewReservationsResponseDto> = Result.success(NewReservationsResponseDto())
    override suspend fun list(page: Long, pageSize: Long, search: String?, view: String?): Result<PaginatedResponseDto<BookingWithDetailsDto>> =
        listHandler(page, search, view)
    override suspend fun createBooking(request: CreateBookingRequestDto): Result<BookingDto> = Result.success(
        BookingDto(999L, "BK-999", request.guestId, request.roomId, request.checkInDate, request.checkOutDate, "confirmed"),
    )
    override suspend fun detail(id: Long): Result<BookingWithDetailsDto> = detailResult
    override suspend fun timeline(id: Long): Result<List<BookingTimelineEntryDto>> = timelineResult
    override suspend fun checkinAdvisory(id: Long): Result<CheckInAdvisoryDto> = advisoryResult
    override suspend fun checkIn(id: Long, request: CheckInRequestDto): Result<Unit> {
        lastCheckInRequest = request
        return checkInResult
    }
    override suspend fun checkOut(id: Long): Result<Unit> = checkOutResult
    override suspend fun voidBooking(id: Long, reason: String?): Result<Unit> {
        lastVoidReason = reason
        return voidResult
    }
    override suspend fun releaseHold(id: Long, reason: String): Result<Unit> = releaseResult
    override suspend fun groupPay(id: Long, request: GroupPayRequestDto): Result<Unit> = Result.success(Unit)
    override suspend fun bookingGroup(id: Long): Result<BookingGroupDetailDto?> = groupDetailResult
    override suspend fun reactivate(id: Long): Result<Unit> {
        reactivateCalls.add(id)
        return reactivateResult
    }
    override suspend fun groupReactivate(id: Long): Result<Unit> {
        groupReactivateCalls.add(id)
        return groupReactivateResult
    }
}

class FakeRoomsRepository : RoomsRepository {
    var roomsResult: Result<List<RoomWithOccupancyDto>> = Result.success(
        listOf(
            RoomWithOccupancyDto(id = 1L, roomNumber = "101", roomType = "Standard", status = "dirty"),
            RoomWithOccupancyDto(id = 2L, roomNumber = "102", roomType = "Deluxe", status = "cleaning"),
        ),
    )
    var detailResult: Result<RoomDetailedStatusDto> = Result.success(
        RoomDetailedStatusDto(id = 1L, roomNumber = "101", roomType = "Standard", status = "dirty", available = false),
    )
    var statusCalls = mutableListOf<Triple<Long, String, String?>>()
    var endCleaningCalls = mutableListOf<Long>()
    var endCleaningResult: Result<Unit> = Result.success(Unit)
    var endMaintenanceCalls = mutableListOf<Long>()
    var endMaintenanceResult: Result<Unit> = Result.success(Unit)

    override suspend fun occupancySummary() = throw UnsupportedOperationException()
    override suspend fun roomsWithOccupancy(): Result<List<RoomWithOccupancyDto>> = roomsResult
    override suspend fun roomDetail(id: Long): Result<RoomDetailedStatusDto> = detailResult
    override suspend fun updateStatus(id: Long, status: String, notes: String?): Result<Unit> {
        statusCalls.add(Triple(id, status, notes))
        return Result.success(Unit)
    }
    override suspend fun endCleaning(id: Long): Result<Unit> {
        endCleaningCalls.add(id)
        return endCleaningResult
    }
    override suspend fun endMaintenance(id: Long): Result<Unit> {
        endMaintenanceCalls.add(id)
        return endMaintenanceResult
    }
}

class FakeHousekeepingRepository : HousekeepingRepository {
    var boardResult: Result<HousekeepingBoardResponseDto> = Result.success(HousekeepingBoardResponseDto())
    var tasksResult: Result<HousekeepingTaskListResponseDto> = Result.success(HousekeepingTaskListResponseDto())
    var staffResult: Result<List<AssignableStaffMemberDto>> = Result.success(emptyList())
    val updatedTasks = mutableListOf<Pair<Long, UpdateHousekeepingTaskRequestDto>>()
    val createdTasks = mutableListOf<CreateHousekeepingTaskRequestDto>()

    private fun sampleTask(id: Long, status: String = "in_progress") = HousekeepingTaskDto(
        id = id,
        roomId = 1L,
        roomNumber = "101",
        roomType = "Standard",
        taskType = "cleaning",
        priority = "normal",
        status = status,
        taskDate = "2026-11-25",
        createdAt = "2026-11-25T09:00:00Z",
        updatedAt = "2026-11-25T09:00:00Z",
    )

    override suspend fun board(): Result<HousekeepingBoardResponseDto> = boardResult
    override suspend fun tasks(status: String?, unassigned: Boolean?, page: Long): Result<HousekeepingTaskListResponseDto> = tasksResult
    override suspend fun createTask(request: CreateHousekeepingTaskRequestDto): Result<HousekeepingTaskDto> {
        createdTasks.add(request)
        return Result.success(sampleTask(50L, "pending"))
    }
    override suspend fun updateTask(id: Long, request: UpdateHousekeepingTaskRequestDto): Result<HousekeepingTaskDto> {
        updatedTasks.add(id to request)
        return Result.success(sampleTask(id, request.status ?: "in_progress"))
    }
    override suspend fun assignableStaff(scope: String): Result<List<AssignableStaffMemberDto>> = staffResult
}

class FakeMaintenanceRepository : MaintenanceRepository {
    var listResult: Result<MaintenanceTicketListResponseDto> = Result.success(MaintenanceTicketListResponseDto())
    val created = mutableListOf<CreateMaintenanceTicketRequestDto>()
    val createdIdempotencyKeys = mutableListOf<String?>()
    val updated = mutableListOf<Pair<Long, UpdateMaintenanceTicketRequestDto>>()

    private fun sampleTicket(id: Long, status: String = "open") = MaintenanceTicketDto(
        id = id,
        roomId = 1L,
        roomNumber = "101",
        ticketNumber = "MT-$id",
        title = "AC leak",
        category = "hvac",
        priority = "high",
        status = status,
        createdAt = "2026-11-25T08:00:00Z",
        updatedAt = "2026-11-25T08:00:00Z",
    )

    override suspend fun list(status: String?, priority: String?, page: Long): Result<MaintenanceTicketListResponseDto> = listResult
    override suspend fun create(
        request: CreateMaintenanceTicketRequestDto,
        idempotencyKey: String?,
    ): Result<MaintenanceTicketDto> {
        created.add(request)
        createdIdempotencyKeys.add(idempotencyKey)
        return Result.success(sampleTicket(77L))
    }
    override suspend fun update(id: Long, request: UpdateMaintenanceTicketRequestDto): Result<MaintenanceTicketDto> {
        updated.add(id to request)
        return Result.success(sampleTicket(id, request.status ?: "in_progress"))
    }
}

class FakeSearchRepository : SearchRepository {
    var handler: (String, String?) -> Result<GlobalSearchResponseDto> = { q, _ ->
        Result.success(GlobalSearchResponseDto(query = q, groups = emptyList()))
    }
    override suspend fun search(query: String, types: String?, limit: Int): Result<GlobalSearchResponseDto> =
        handler(query, types)
}

class FakePaymentsRepository : PaymentsRepository {
    var pendingResult: Result<PendingPaymentPageDto> = Result.success(PendingPaymentPageDto())
    var approveResult: Result<PaymentActionResponseDto> = Result.success(PaymentActionResponseDto(1L, "completed", "confirmed"))
    var rejectResult: Result<PaymentActionResponseDto> = Result.success(PaymentActionResponseDto(1L, "rejected", "cancelled"))
    var downloadReceiptResult: Result<PaymentReceiptFile> = Result.failure(UnsupportedOperationException("Not configured"))
    var requestReceiptResult: Result<Unit> = Result.success(Unit)
    val approvedIds = mutableListOf<Long>()
    val rejectedCalls = mutableListOf<Pair<Long, String>>()
    val requestedReceiptCalls = mutableListOf<Pair<Long, String?>>()

    override suspend fun bookingPayments(bookingId: Long) = Result.success(emptyList<com.hotelapp.staff.data.dto.BookingPaymentRecordDto>())
    override suspend fun workflowSummary(bookingId: Long) = Result.failure<com.hotelapp.staff.data.dto.PaymentWorkflowSummaryDto>(UnsupportedOperationException())
    override suspend fun recordPayment(request: RecordPaymentRequestDto) = Result.success(Unit)
    override suspend fun pendingPayments(page: Long, perPage: Long) = pendingResult
    override suspend fun approvePayment(paymentId: Long): Result<PaymentActionResponseDto> {
        approvedIds.add(paymentId)
        return approveResult
    }
    override suspend fun rejectPayment(paymentId: Long, reason: String): Result<PaymentActionResponseDto> {
        rejectedCalls.add(paymentId to reason)
        return rejectResult
    }
    override suspend fun downloadReceipt(paymentId: Long): Result<PaymentReceiptFile> = downloadReceiptResult
    override suspend fun requestReceipt(paymentId: Long, message: String?): Result<Unit> {
        requestedReceiptCalls.add(paymentId to message)
        return requestReceiptResult
    }
    override suspend fun invoicePreview(bookingId: Long) = Result.failure<com.hotelapp.staff.data.dto.InvoicePreviewDto>(UnsupportedOperationException())
    override suspend fun generateInvoice(bookingId: Long) = Result.failure<com.hotelapp.staff.data.dto.InvoiceDto>(UnsupportedOperationException())
    override suspend fun refundDeposit(bookingId: Long, request: RefundDepositRequestDto) = Result.success(Unit)
    override suspend fun forfeitDeposit(bookingId: Long, request: ForfeitDepositRequestDto) = Result.success(Unit)
}

class FakeEkycRepository : EkycRepository {
    var listResult: Result<EkycAdminListResponseDto> = Result.success(EkycAdminListResponseDto())
    var detailResult: Result<EkycApplicationDetailDto> = Result.failure(UnsupportedOperationException())
    var actionResult: Result<EkycApplicationDetailDto> = Result.failure(UnsupportedOperationException())
    var codesResult: Result<List<EkycReasonCodeDto>> = Result.success(emptyList())
    var documentBytesResult: Result<ByteArray> = Result.failure(UnsupportedOperationException("Not configured"))
    var revealResult: Result<EkycSensitiveRevealResponseDto> = Result.failure(UnsupportedOperationException("Not configured"))
    val actionsTaken = mutableListOf<Pair<Long, EkycReviewActionRequestDto>>()
    val revealCalls = mutableListOf<Triple<Long, String, String>>()

    override suspend fun dashboard(): Result<EkycDashboardMetricsDto> = Result.success(EkycDashboardMetricsDto())
    override suspend fun applications(status: String?, riskLevel: String?, search: String?, page: Long) = listResult
    override suspend fun applicationDetail(id: Long) = detailResult
    override suspend fun applyAction(id: Long, request: EkycReviewActionRequestDto): Result<EkycApplicationDetailDto> {
        actionsTaken.add(id to request)
        return actionResult
    }
    override suspend fun reasonCodes() = codesResult
    override suspend fun fetchDocumentBytes(id: Long, kind: String): Result<ByteArray> = documentBytesResult
    override suspend fun revealSensitive(id: Long, field: String, reason: String): Result<EkycSensitiveRevealResponseDto> {
        revealCalls.add(Triple(id, field, reason))
        return revealResult
    }
}

class FakeNotificationsRepository : NotificationsRepository {
    var listResult: Result<StaffNotificationsResponseDto> = Result.success(StaffNotificationsResponseDto())
    var markReadResult: Result<Unit> = Result.success(Unit)
    var markAllReadResult: Result<Unit> = Result.success(Unit)
    val markedReadIds = mutableListOf<Long>()
    var markAllReadCalls = 0

    override suspend fun list(): Result<StaffNotificationsResponseDto> = listResult
    override suspend fun markRead(id: Long): Result<Unit> {
        markedReadIds.add(id)
        if (markReadResult.isSuccess) {
            listResult.getOrNull()?.let { cur ->
                val updatedItems = cur.items.map { item ->
                    if (item.id == id && item.readAt == null) item.copy(readAt = "2026-06-01T12:00:00Z") else item
                }
                val nextUnread = updatedItems.count { it.readAt == null && it.actionPending }.toLong()
                listResult = Result.success(
                    cur.copy(
                        unread = nextUnread,
                        urgentUnread = 0L.coerceAtLeast(if (nextUnread == 0L) 0L else cur.urgentUnread),
                        items = updatedItems,
                    ),
                )
            }
        }
        return markReadResult
    }
    override suspend fun markAllRead(): Result<Unit> {
        markAllReadCalls += 1
        if (markAllReadResult.isSuccess) {
            listResult.getOrNull()?.let { cur ->
                listResult = Result.success(
                    cur.copy(
                        unread = 0L,
                        urgentUnread = 0L,
                        items = cur.items.map { item -> item.copy(readAt = item.readAt ?: "2026-06-01T12:00:00Z") },
                    ),
                )
            }
        }
        return markAllReadResult
    }
}
