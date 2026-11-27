package com.hotelapp.staff.e2e

import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.network.ApiException
import com.hotelapp.staff.core.network.AuthInterceptor
import com.hotelapp.staff.core.network.BaseUrlInterceptor
import com.hotelapp.staff.core.network.CookieStorage
import com.hotelapp.staff.core.network.EncryptedCookieJar
import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.core.network.HotelJson
import com.hotelapp.staff.core.network.NetworkFactory
import com.hotelapp.staff.core.network.RealtimeConnectionState
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.network.SecretCipher
import com.hotelapp.staff.core.network.TokenAuthenticator
import com.hotelapp.staff.core.network.TwoFactorRequiredException
import com.hotelapp.staff.data.api.AuthApi
import com.hotelapp.staff.data.api.BookingsApi
import com.hotelapp.staff.data.api.EkycApi
import com.hotelapp.staff.data.api.GuestsApi
import com.hotelapp.staff.data.api.HousekeepingApi
import com.hotelapp.staff.data.api.MaintenanceApi
import com.hotelapp.staff.data.api.NotificationsApi
import com.hotelapp.staff.data.api.OperationsApi
import com.hotelapp.staff.data.api.PaymentsApi
import com.hotelapp.staff.data.api.RoomsApi
import com.hotelapp.staff.data.api.SearchApi
import com.hotelapp.staff.data.api.SettingsApi
import com.hotelapp.staff.data.dto.CheckInRequestDto
import com.hotelapp.staff.data.dto.CreateBookingRequestDto
import com.hotelapp.staff.data.dto.CreateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.dto.CreateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.EkycReviewActionRequestDto
import com.hotelapp.staff.data.dto.RecordPaymentRequestDto
import com.hotelapp.staff.data.dto.UpdateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.dto.UpdateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.repository.DefaultAuthRepository
import com.hotelapp.staff.data.repository.DefaultBookingsRepository
import com.hotelapp.staff.data.repository.DefaultEkycRepository
import com.hotelapp.staff.data.repository.DefaultGuestsRepository
import com.hotelapp.staff.data.repository.DefaultHousekeepingRepository
import com.hotelapp.staff.data.repository.DefaultMaintenanceRepository
import com.hotelapp.staff.data.repository.DefaultNotificationsRepository
import com.hotelapp.staff.data.repository.DefaultOperationsRepository
import com.hotelapp.staff.data.repository.DefaultPaymentsRepository
import com.hotelapp.staff.data.repository.DefaultRoomsRepository
import com.hotelapp.staff.data.repository.DefaultSearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

private class E2eMemoryCookieStorage : CookieStorage {
    var bytes: ByteArray? = null
    override fun read(): ByteArray? = bytes
    override fun write(data: ByteArray) { bytes = data }
    override fun clear() { bytes = null }
}

private class E2eXorCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor 0x3C).toByte() }.toByteArray()
    override fun decrypt(cipherText: ByteArray): ByteArray = cipherText.map { (it.toInt() xor 0x3C).toByte() }.toByteArray()
}

private val stackIpCounter = AtomicInteger(10)

private class E2eStack(
    private val baseUrl: String,
    val cookieStorage: E2eMemoryCookieStorage = E2eMemoryCookieStorage(),
    forwardedIp: String = "10.0.1.${stackIpCounter.incrementAndGet()}",
) {
    val sessionManager = SessionManager()
    val cookieJar = EncryptedCookieJar(cookieStorage, E2eXorCipher())

    private val forwardedIpInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("X-Forwarded-For", forwardedIp)
            .build()
        chain.proceed(req)
    }

    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(forwardedIpInterceptor)
        .addInterceptor(BaseUrlInterceptor { baseUrl })
        .addInterceptor(AuthInterceptor(accessTokenProvider = { null }))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mainClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(forwardedIpInterceptor)
        .addInterceptor(BaseUrlInterceptor { baseUrl })
        .addInterceptor(
            AuthInterceptor(
                accessTokenProvider = { sessionManager.getAccessToken() },
                baseUrlProvider = { baseUrl },
                tokenForOriginProvider = { origin -> sessionManager.getAccessTokenForOrigin(origin) },
                requestAuthContextForOriginProvider = { origin -> sessionManager.getRequestAuthContextForOrigin(origin) },
            ),
        )
        .authenticator(
            TokenAuthenticator(sessionManager) {
                NetworkFactory.refreshAccessTokenSync(refreshClient)
            },
        )
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val wsClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(forwardedIpInterceptor)
        .addInterceptor(BaseUrlInterceptor { baseUrl })
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    val socketClient = RealtimeSocketClient(
        client = wsClient,
        baseUrlProvider = { baseUrl },
        sessionManager = sessionManager,
        onUnauthorized = { NetworkFactory.refreshAccessTokenSync(refreshClient) },
    )

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(NetworkFactory.PLACEHOLDER_BASE_URL)
        .client(mainClient)
        .addConverterFactory(HotelJson.asConverterFactory("application/json".toMediaType()))
        .build()

    val settingsApi: SettingsApi = retrofit.create(SettingsApi::class.java)
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val authRepo = DefaultAuthRepository(
        authApi = authApi,
        serverRepository = null,
        sessionManager = sessionManager,
        cookieJar = cookieJar,
        realtimeSocketClient = socketClient,
        currentServerUrlProvider = { baseUrl },
    )
    val bookingsRepo = DefaultBookingsRepository(retrofit.create(BookingsApi::class.java))
    val roomsRepo = DefaultRoomsRepository(retrofit.create(RoomsApi::class.java))
    val housekeepingRepo = DefaultHousekeepingRepository(retrofit.create(HousekeepingApi::class.java))
    val maintenanceRepo = DefaultMaintenanceRepository(retrofit.create(MaintenanceApi::class.java))
    val guestsRepo = DefaultGuestsRepository(retrofit.create(GuestsApi::class.java))
    val notificationsRepo = DefaultNotificationsRepository(retrofit.create(NotificationsApi::class.java))
    val searchRepo = DefaultSearchRepository(retrofit.create(SearchApi::class.java))
    val paymentsRepo = DefaultPaymentsRepository(retrofit.create(PaymentsApi::class.java))
    val ekycRepo = DefaultEkycRepository(retrofit.create(EkycApi::class.java))
    val operationsRepo = DefaultOperationsRepository(retrofit.create(OperationsApi::class.java))
}

class LiveBackendE2eTest {

    private fun baseUrl(): String {
        val url = System.getenv("HOTEL_E2E_BASE_URL")?.trim()?.trimEnd('/').orEmpty()
        assumeTrue("HOTEL_E2E_BASE_URL not set; skipping live backend E2E test", url.isNotEmpty())
        return url
    }

    @Test
    fun `exercise every Android PMS feature against live seeded backend`() = runBlocking {
        val url = baseUrl()
        val stack = E2eStack(url)

        // 1. Health & Public Settings probe
        val health = stack.settingsApi.healthAt("$url/health")
        assertEquals("ok", health.status)
        val publicSettings = stack.settingsApi.publicSettingsAt("$url/api/settings/public")
        assertTrue(publicSettings.isNotEmpty())
        assertTrue(publicSettings.any { it.key == "currency" })

        // 2. Auth negative checks (wrong password, nonexistent user, inactive, locked, unverified, guest_portal)
        val wrongPw = stack.authRepo.login("staff_razak", "WrongPassword!")
        assertTrue(wrongPw.isFailure)
        assertTrue(wrongPw.exceptionOrNull() is ApiException)

        val unknownUser = stack.authRepo.login("nonexistent_user_xyz", "HotelStaging2026!")
        assertTrue(unknownUser.isFailure)

        for (blockedUser in listOf("inactive_former", "newhire_unverified")) {
            val res = stack.authRepo.login(blockedUser, "HotelStaging2026!")
            assertTrue("Expected $blockedUser login to fail", res.isFailure)
            assertTrue("Expected ApiException for $blockedUser", res.exceptionOrNull() is ApiException)
        }

        // Guest portal user must be rejected by Staff App login guard
        val guestStack = E2eStack(url)
        val guestLogin = guestStack.authRepo.login("guest_portal", "HotelStaging2026!")
        assertTrue("Expected guest_portal login to be rejected by Staff App", guestLogin.isFailure)
        val guestErr = guestLogin.exceptionOrNull() as? GenericApiException
        assertEquals("guest_account_not_allowed", guestErr?.code)
        assertTrue(guestStack.sessionManager.authState.value is AuthState.Unauthenticated)

        // 3. 2FA challenge + recovery code flow (twofa_stg)
        val twoFaStack = E2eStack(url)
        val challenge = twoFaStack.authRepo.login("twofa_stg", "HotelStaging2026!")
        assertTrue(challenge.isFailure)
        assertTrue(
            "Expected TwoFactorRequiredException for twofa_stg, got ${challenge.exceptionOrNull()}",
            challenge.exceptionOrNull() is TwoFactorRequiredException,
        )
        val twoFaSession = twoFaStack.authRepo.login("twofa_stg", "HotelStaging2026!", totpCode = "stg-rc-0001").getOrThrow()
        assertEquals("twofa_stg", twoFaSession.user.username)
        twoFaStack.authRepo.logout().getOrThrow()

        // 4. Super Admin login + persistent cookie restore + TokenAuthenticator 401 refresh + sessions
        val superSession = stack.authRepo.login("super_stg", "HotelStaging2026!").getOrThrow()
        assertTrue(superSession.hasPermission("bookings:read"))
        assertTrue(superSession.hasPermission("ekyc:read"))
        assertTrue(superSession.hasPermission("night_audit:read"))

        // Simulate cold restart with same encrypted cookie storage
        val restartedStack = E2eStack(url, cookieStorage = stack.cookieStorage)
        val restored = restartedStack.authRepo.restoreSession()
        assertTrue("Session should restore from EncryptedCookieJar", restored)
        assertTrue(restartedStack.sessionManager.authState.value is AuthState.Authenticated)

        // Force 401 by corrupting in-memory token -> TokenAuthenticator must refresh & succeed
        restartedStack.sessionManager.updateAccessToken("corrupted.jwt.token")
        val summaryAfter401 = restartedStack.bookingsRepo.summary().getOrThrow()
        assertNotNull(summaryAfter401)
        assertTrue(restartedStack.sessionManager.getAccessToken() != "corrupted.jwt.token")

        // Active sessions list & revoke second session
        val secondStack = E2eStack(url)
        secondStack.authRepo.login("super_stg", "HotelStaging2026!").getOrThrow()
        val sessions = restartedStack.authRepo.listSessions().getOrThrow()
        assertTrue("Expected >= 2 active sessions", sessions.size >= 2)
        val otherSession = sessions.first { !it.isCurrent }
        restartedStack.authRepo.revokeSession(otherSession.id).getOrThrow()

        // 5. Dashboard, Operations (Night Audit + Guest Relations), Notifications & Pending Payments
        val newRes = restartedStack.bookingsRepo.newReservations().getOrThrow()
        assertNotNull(newRes.visibleFrom)
        val occSummary = restartedStack.roomsRepo.occupancySummary().getOrThrow()
        assertTrue(occSummary.totalRooms > 0)
        val nightAudit = restartedStack.operationsRepo.nightAuditPreview().getOrThrow()
        assertTrue(nightAudit.auditDate.isNotBlank())
        val guestRel = restartedStack.operationsRepo.guestRelationsOverview().getOrThrow()
        assertTrue(guestRel.arrivals.count >= 0)

        val notifs = restartedStack.notificationsRepo.list().getOrThrow()
        if (notifs.items.isNotEmpty()) {
            restartedStack.notificationsRepo.markRead(notifs.items.first().id).getOrThrow()
        }
        restartedStack.notificationsRepo.markAllRead().getOrThrow()

        val pendingPayments = restartedStack.paymentsRepo.pendingPayments(page = 1, perPage = 25).getOrThrow()
        assertTrue(pendingPayments.total >= 0)

        // 6. Global Search across scopes
        restartedStack.searchRepo.search(query = "STG", types = null, limit = 10).getOrThrow()
        for (scope in listOf("bookings", "guests", "rooms", "ledgers")) {
            restartedStack.searchRepo.search(query = "10", types = scope, limit = 5).getOrThrow()
        }

        // 7. Guests directory & 360 profile
        val guests = restartedStack.guestsRepo.list(page = 1).getOrThrow()
        assertTrue(guests.data.isNotEmpty())
        val firstGuest = guests.data.first()
        restartedStack.guestsRepo.list(search = firstGuest.displayName().take(4), page = 1).getOrThrow()
        val guestProfile = restartedStack.guestsRepo.profile(firstGuest.id).getOrThrow()
        assertEquals(firstGuest.id, guestProfile.guest.id)

        // 8. Bookings list views, Walk-In create, Folio payments, Invoice Preview & Generate, Check-In, Void
        for (view in listOf("all", "arriving", "in_house", "departing", "upcoming", "balance")) {
            restartedStack.bookingsRepo.list(page = 1, view = view).getOrThrow()
        }
        val allBookings = restartedStack.bookingsRepo.list(page = 1, pageSize = 50, view = "all").getOrThrow()
        assertTrue(allBookings.data.isNotEmpty())
        val firstBooking = allBookings.data.first()
        assertEquals(firstBooking.id, restartedStack.bookingsRepo.detail(firstBooking.id).getOrThrow().id)
        restartedStack.bookingsRepo.timeline(firstBooking.id).getOrThrow()
        restartedStack.bookingsRepo.checkinAdvisory(firstBooking.id).getOrThrow()

        val rooms = restartedStack.roomsRepo.roomsWithOccupancy().getOrThrow()
        assertTrue(rooms.isNotEmpty())
        val cleanRoom = rooms.firstOrNull { !it.isOccupied && it.status == "available" }
        assertNotNull("Expected an available unoccupied room in seeded data", cleanRoom)

        val checkInToday = LocalDate.now().plusDays(14).toString()
        val checkOutFuture = LocalDate.now().plusDays(16).toString()
        val createdBooking = restartedStack.bookingsRepo.createBooking(
            CreateBookingRequestDto(
                guestId = firstGuest.id,
                roomId = cleanRoom!!.id,
                checkInDate = checkInToday,
                checkOutDate = checkOutFuture,
                paymentMethod = "cash",
                source = "walk_in",
                bookingRemarks = "E2E live verification walk-in",
            ),
        ).getOrThrow()

        // Folio, payments & invoices on the newly created booking
        restartedStack.paymentsRepo.bookingPayments(createdBooking.id).getOrThrow()
        restartedStack.paymentsRepo.workflowSummary(createdBooking.id).getOrThrow()
        restartedStack.paymentsRepo.recordPayment(
            RecordPaymentRequestDto(
                bookingId = createdBooking.id,
                amount = 10.0,
                paymentMethod = "cash",
                paymentType = "booking",
                transactionReference = "E2E-${UUID.randomUUID().toString().take(8)}",
                notes = "E2E partial desk payment",
                idempotencyKey = UUID.randomUUID().toString(),
            ),
        ).getOrThrow()
        val preview = restartedStack.paymentsRepo.invoicePreview(createdBooking.id).getOrThrow()
        assertEquals(createdBooking.id, preview.invoice.bookingId)
        val generatedInvoice = restartedStack.paymentsRepo.generateInvoice(createdBooking.id).getOrThrow()
        assertTrue(generatedInvoice.invoiceNumber.isNotBlank())

        // Exercise check-in on confirmed today booking if available, otherwise verify domain error cleanly
        val confirmedBooking = allBookings.data.firstOrNull { it.status == "confirmed" }
        if (confirmedBooking != null) {
            val roomDetail = restartedStack.roomsRepo.roomDetail(confirmedBooking.roomId).getOrThrow()
            if (roomDetail.status in setOf("dirty", "reserved_dirty")) {
                restartedStack.roomsRepo.updateStatus(confirmedBooking.roomId, "available", "Prep for E2E check-in").getOrThrow()
            } else if (roomDetail.status == "cleaning") {
                restartedStack.roomsRepo.endCleaning(confirmedBooking.roomId).getOrThrow()
            }
            val checkInResult = restartedStack.bookingsRepo.checkIn(confirmedBooking.id, CheckInRequestDto())
            assertTrue("Expected checkIn success or clean ApiException, got ${checkInResult.exceptionOrNull()}", checkInResult.isSuccess || checkInResult.exceptionOrNull() is ApiException)
        }

        val inHouseList = restartedStack.bookingsRepo.list(page = 1, pageSize = 25, view = "in_house").getOrThrow()
        inHouseList.data.firstOrNull()?.let { stay ->
            val coRes = restartedStack.bookingsRepo.checkOut(stay.id)
            assertTrue("checkOut returned unexpected exception: ${coRes.exceptionOrNull()}", coRes.isSuccess || coRes.exceptionOrNull() is ApiException)
        }

        // Void the future walk-in booking we created
        val voidRes = restartedStack.bookingsRepo.voidBooking(createdBooking.id, "E2E cleanup")
        assertTrue("Expected voidBooking success or ApiException, got ${voidRes.exceptionOrNull()}", voidRes.isSuccess || voidRes.exceptionOrNull() is ApiException)

        // 9. Live WebSocket + Rooms lifecycle
        val testRoomId = cleanRoom.id
        restartedStack.roomsRepo.roomDetail(testRoomId).getOrThrow()
        restartedStack.socketClient.start()
        withTimeout(8_000) {
            restartedStack.socketClient.connectionState
                .filter { it == RealtimeConnectionState.CONNECTED }
                .first()
        }

        val wsRoomEvent = async {
            withTimeout(8_000) {
                restartedStack.socketClient.domainEvents.filter { it == "rooms" }.first()
            }
        }
        restartedStack.roomsRepo.updateStatus(testRoomId, "dirty", "E2E dirty").getOrThrow()
        assertEquals("rooms", wsRoomEvent.await())

        restartedStack.roomsRepo.updateStatus(testRoomId, "available", "E2E cleaned").getOrThrow()
        restartedStack.roomsRepo.updateStatus(testRoomId, "maintenance", "E2E maintenance").getOrThrow()
        restartedStack.roomsRepo.endMaintenance(testRoomId).getOrThrow()
        assertEquals("available", restartedStack.roomsRepo.roomDetail(testRoomId).getOrThrow().status)

        // 10. Housekeeping lifecycle
        val board = restartedStack.housekeepingRepo.board().getOrThrow()
        assertTrue(board.rooms.isNotEmpty())
        restartedStack.housekeepingRepo.tasks(status = null).getOrThrow()
        restartedStack.housekeepingRepo.tasks(status = "pending").getOrThrow()
        restartedStack.housekeepingRepo.tasks(unassigned = true).getOrThrow()
        val hkStaff = restartedStack.housekeepingRepo.assignableStaff("housekeeping").getOrThrow()
        assertTrue(hkStaff.isNotEmpty())

        val createdTask = restartedStack.housekeepingRepo.createTask(
            CreateHousekeepingTaskRequestDto(
                roomId = testRoomId,
                taskType = "inspection",
                priority = "high",
                notes = "E2E inspection task",
            ),
        ).getOrThrow()
        restartedStack.housekeepingRepo.updateTask(
            createdTask.id,
            UpdateHousekeepingTaskRequestDto(assignedTo = hkStaff.first().id),
        ).getOrThrow()
        restartedStack.housekeepingRepo.updateTask(
            createdTask.id,
            UpdateHousekeepingTaskRequestDto(clearAssignee = true),
        ).getOrThrow()
        restartedStack.housekeepingRepo.updateTask(
            createdTask.id,
            UpdateHousekeepingTaskRequestDto(status = "in_progress"),
        ).getOrThrow()
        assertEquals(
            "completed",
            restartedStack.housekeepingRepo.updateTask(
                createdTask.id,
                UpdateHousekeepingTaskRequestDto(status = "completed"),
            ).getOrThrow().status,
        )

        val voidTask = restartedStack.housekeepingRepo.createTask(
            CreateHousekeepingTaskRequestDto(roomId = testRoomId, taskType = "cleaning", priority = "low"),
        ).getOrThrow()
        assertEquals(
            "void",
            restartedStack.housekeepingRepo.updateTask(
                voidTask.id,
                UpdateHousekeepingTaskRequestDto(status = "void"),
            ).getOrThrow().status,
        )

        // 11. Maintenance lifecycle
        restartedStack.maintenanceRepo.list().getOrThrow()
        val maintStaff = restartedStack.housekeepingRepo.assignableStaff("maintenance").getOrThrow()
        assertTrue(maintStaff.isNotEmpty())
        val ticket = restartedStack.maintenanceRepo.create(
            CreateMaintenanceTicketRequestDto(
                roomId = testRoomId,
                title = "E2E HVAC check",
                category = "hvac",
                priority = "high",
                description = "Automated E2E verification",
            ),
        ).getOrThrow()
        restartedStack.maintenanceRepo.update(
            ticket.id,
            UpdateMaintenanceTicketRequestDto(assignedTo = maintStaff.first().id, status = "in_progress"),
        ).getOrThrow()
        restartedStack.maintenanceRepo.update(ticket.id, UpdateMaintenanceTicketRequestDto(status = "on_hold")).getOrThrow()
        restartedStack.maintenanceRepo.update(ticket.id, UpdateMaintenanceTicketRequestDto(status = "in_progress")).getOrThrow()
        restartedStack.maintenanceRepo.update(
            ticket.id,
            UpdateMaintenanceTicketRequestDto(status = "resolved", resolutionNotes = "Verified filter"),
        ).getOrThrow()
        assertEquals(
            "closed",
            restartedStack.maintenanceRepo.update(ticket.id, UpdateMaintenanceTicketRequestDto(status = "closed")).getOrThrow().status,
        )

        // 12. eKYC queue, filters, detail, reason codes & action verification
        val ekycDash = restartedStack.ekycRepo.dashboard().getOrThrow()
        assertTrue(ekycDash.totalSubmitted >= 0)
        for (filter in listOf("all", "pending_manual_review", "submitted", "in_review", "additional_information_required", "escalated", "approved", "rejected")) {
            restartedStack.ekycRepo.applications(status = filter).getOrThrow()
        }
        val reasonCodes = restartedStack.ekycRepo.reasonCodes().getOrThrow()

        val ekycList = restartedStack.ekycRepo.applications(status = "all").getOrThrow()
        ekycList.data.firstOrNull()?.let { firstApp ->
            val detail = restartedStack.ekycRepo.applicationDetail(firstApp.id).getOrThrow()
            assertEquals(firstApp.id, detail.summary.id)
            if (detail.summary.status.lowercase() in setOf("submitted", "pending_manual_review", "automated_review", "escalated") &&
                detail.summary.assignedReviewerId == null
            ) {
                val claimed = restartedStack.ekycRepo.applyAction(
                    detail.summary.id,
                    EkycReviewActionRequestDto(
                        action = "claim",
                        expectedVersion = detail.summary.version,
                        idempotencyKey = UUID.randomUUID().toString(),
                    ),
                ).getOrThrow()
                assertEquals("in_review", claimed.summary.status)

                val approveCode = reasonCodes.firstOrNull {
                    it.isActive && it.category.contains("approv", ignoreCase = true)
                } ?: reasonCodes.firstOrNull { it.isActive }
                if (approveCode != null) {
                    val approved = restartedStack.ekycRepo.applyAction(
                        claimed.summary.id,
                        EkycReviewActionRequestDto(
                            action = "approve",
                            expectedVersion = claimed.summary.version,
                            reasonCode = approveCode.code,
                            reason = if (approveCode.requiresDetails) "Verified ID and selfie in E2E test" else null,
                            selfCheckinEnabled = true,
                            idempotencyKey = UUID.randomUUID().toString(),
                        ),
                    ).getOrThrow()
                    assertEquals("approved", approved.summary.status)
                }
            }
        }

        // 13. Logout clears cookie jar and prevents session restore
        restartedStack.authRepo.logout().getOrThrow()
        assertTrue(restartedStack.sessionManager.authState.value is AuthState.Unauthenticated)
        delay(100)
        val afterLogoutStack = E2eStack(url, cookieStorage = restartedStack.cookieStorage)
        assertFalse(afterLogoutStack.authRepo.restoreSession())
    }

    @Test
    fun `exercise every seeded role RBAC matrix against live backend`() = runBlocking {
        val url = baseUrl()

        val accounts = listOf(
            "super_stg",
            "admin_stg",
            "manager_stg",
            "frontdesk_amy",
            "hk_siti",
            "staff_razak",
            "finance_mei",
            "ekyc_reviewer",
            "support_viewer",
            "marketing_nadia",
            "voucher_audit",
            "voucher_noperm",
        )

        var knownGuestId = 0L

        for (username in accounts) {
            val stack = E2eStack(url)
            val session = stack.authRepo.login(username, "HotelStaging2026!")
                .getOrElse { throw AssertionError("Failed login for $username: ${it.message}", it) }

            if (knownGuestId == 0L && session.hasPermission("guests:read")) {
                knownGuestId = stack.guestsRepo.list(page = 1).getOrThrow().data.first().id
            }

            assertPermissionProbe(username, session, "bookings:read") { stack.bookingsRepo.summary() }
            assertPermissionProbe(username, session, "rooms:read") { stack.roomsRepo.occupancySummary() }
            assertPermissionProbe(username, session, "housekeeping:read") { stack.housekeepingRepo.board() }
            assertPermissionProbe(username, session, "maintenance:read") { stack.maintenanceRepo.list() }
            assertPermissionProbe(username, session, "guests:read") { stack.guestsRepo.profile(knownGuestId) }
            if (!session.hasPermission("guests:read")) {
                val scopedGuests = stack.guestsRepo.list(page = 1).getOrThrow()
                assertTrue(
                    "Expected $username without guests:read to receive empty guest list, got ${scopedGuests.data.size}",
                    scopedGuests.data.isEmpty(),
                )
            }
            assertPermissionProbe(username, session, "payments:read") { stack.paymentsRepo.pendingPayments(page = 1, perPage = 5) }
            assertPermissionProbe(username, session, "ekyc:read") { stack.ekycRepo.dashboard() }
            assertPermissionProbe(username, session, "ekyc:review") { stack.ekycRepo.reasonCodes() }
            assertPermissionProbe(username, session, "night_audit:read") { stack.operationsRepo.nightAuditPreview() }

            // Notifications are allowed for every authenticated staff account
            stack.notificationsRepo.list().getOrThrow()

            // Search allowed for all staff; guest_relations requires guests:read
            assertPermissionProbe(username, session, "guests:read") { stack.operationsRepo.guestRelationsOverview() }

            stack.authRepo.logout().getOrThrow()
        }
    }

    private suspend fun <T> assertPermissionProbe(
        username: String,
        session: StaffSession,
        requiredPerm: String,
        call: suspend () -> Result<T>,
    ) {
        val expectedAllowed = session.hasPermission(requiredPerm)
        val result = call()
        if (expectedAllowed) {
            assertTrue(
                "Expected $username with '$requiredPerm' to succeed, but got ${result.exceptionOrNull()}",
                result.isSuccess,
            )
        } else {
            val err = result.exceptionOrNull() as? ApiException
            assertTrue(
                "Expected $username without '$requiredPerm' to fail with 403 ApiException, got $result",
                result.isFailure && err?.statusCode == 403,
            )
        }
    }
}
