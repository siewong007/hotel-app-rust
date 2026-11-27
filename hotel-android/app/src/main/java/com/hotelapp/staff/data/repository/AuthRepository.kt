package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.network.ApiException
import com.hotelapp.staff.core.network.EncryptedCookieJar
import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.network.runSuspendCatching
import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.AuthApi
import com.hotelapp.staff.data.api.PasskeyApi
import com.hotelapp.staff.data.dto.AuthResponseDto
import com.hotelapp.staff.data.dto.LoginRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginFinishRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartResponseDto
import com.hotelapp.staff.data.dto.PasskeySecondFactorFinishRequestDto
import com.hotelapp.staff.data.dto.PasskeySecondFactorStartRequestDto
import com.hotelapp.staff.data.dto.UserSessionInfoDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

interface AuthRepository {
    fun hasStoredSession(): Boolean = true
    suspend fun restoreSession(): Boolean
    suspend fun login(username: String, password: String, totpCode: String? = null): Result<StaffSession>
    suspend fun passkeyLoginStart(username: String): Result<PasskeyLoginStartResponseDto>
    suspend fun passkeyLoginFinish(request: PasskeyLoginFinishRequestDto): Result<StaffSession>
    suspend fun passkeySecondFactorStart(mfaToken: String): Result<PasskeyLoginStartResponseDto> =
        Result.failure(UnsupportedOperationException("Passkey 2FA is not configured."))
    suspend fun passkeySecondFactorFinish(request: PasskeySecondFactorFinishRequestDto): Result<StaffSession> =
        Result.failure(UnsupportedOperationException("Passkey 2FA is not configured."))
    suspend fun logout(): Result<Unit>
    suspend fun listSessions(): Result<List<UserSessionInfoDto>>
    suspend fun revokeSession(sessionId: String): Result<Unit>
}

class DefaultAuthRepository(
    private val authApi: AuthApi,
    private val passkeyApi: PasskeyApi? = null,
    private val serverRepository: ServerRepository?,
    private val sessionManager: SessionManager,
    private val cookieJar: EncryptedCookieJar,
    private val realtimeSocketClient: RealtimeSocketClient,
    private val onLoggedOut: suspend () -> Unit = {},
    private val onAuthenticatedUser: suspend (Long) -> Unit = {},
    private val currentServerUrlProvider: () -> String = { "" },
    @Suppress("UNUSED_PARAMETER") private val shouldStartRealtime: () -> Boolean = { true },
    private val onInteractiveLogin: () -> Unit = {},
    private val cancelInFlightRequests: () -> Unit = {},
) : AuthRepository {

    private val restoreMutex = Mutex()

    override fun hasStoredSession(): Boolean {
        val baseUrl = currentServerUrlProvider().trim()
        return if (baseUrl.isNotEmpty()) {
            cookieJar.hasRefreshCookieFor(baseUrl)
        } else {
            cookieJar.hasStoredCookies()
        }
    }

    override suspend fun restoreSession(): Boolean = restoreMutex.withLock {
        if (sessionManager.authState.value is com.hotelapp.staff.core.auth.AuthState.Authenticated) {
            return@withLock true
        }
        if (!hasStoredSession()) {
            withContext(NonCancellable) {
                sessionManager.forceUnauthenticated()
                runSuspendCatching { onLoggedOut() }
            }
            return@withLock false
        }
        sessionManager.setChecking()

        val startOrigin = SessionManager.normalizeOrigin(currentServerUrlProvider().trim())
        val restored = safeApiCall {
            val origin = currentServerUrlProvider().trim().takeIf { it.isNotEmpty() }
            val refreshed = authApi.refresh()
            sessionManager.updateAccessToken(refreshed.accessToken, origin = origin)
            val access = authApi.accessSnapshot()
            val profile = authApi.profile()
            if (isGuestOnlyAccount(access.roles, profile.userType)) {
                withTimeoutOrNull(3_000L) { runSuspendCatching { authApi.logout() } }
                throw GenericApiException(
                    message = "Guest accounts cannot sign in to the Staff app. Please use the guest portal.",
                    statusCode = 403,
                    code = "guest_account_not_allowed",
                )
            }
            val currentOrigin = SessionManager.normalizeOrigin(currentServerUrlProvider().trim())
            if (currentOrigin != startOrigin) {
                return@safeApiCall false
            }
            val user = UserSummaryDto(
                id = profile.id,
                username = profile.username,
                email = profile.email,
                userType = profile.userType,
                fullName = profile.fullName,
                phone = profile.phone,
                isActive = true,
                roles = access.roles,
                permissions = access.permissions,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt,
                lastLoginAt = profile.lastLoginAt,
                isVerified = profile.isVerified,
                isSuperAdmin = "super_admin" in access.roles,
            )
            val session = StaffSession(
                user = user,
                roles = access.roles,
                permissions = access.permissions,
                routePolicies = access.routePolicies,
            )
            onAuthenticatedUser(profile.id)
            sessionManager.setAuthenticated(refreshed.accessToken, session, origin = origin)
            withTimeoutOrNull(2_000L) { runSuspendCatching { serverRepository?.refreshBranding() } }
            true
        }

        if (restored.isFailure) {
            val statusCode = (restored.exceptionOrNull() as? ApiException)?.statusCode
            val isDeterministicAuthRejection = statusCode == 401 || statusCode == 403
            withContext(NonCancellable) {
                if (isDeterministicAuthRejection) {
                    cookieJar.clearAll()
                    sessionManager.forceUnauthenticated()
                    runSuspendCatching { onLoggedOut() }
                } else {
                    sessionManager.forceUnauthenticated()
                }
            }
            return@withLock false
        }
        if (restored.getOrNull() != true) {
            withContext(NonCancellable) {
                sessionManager.forceUnauthenticated()
            }
            return@withLock false
        }
        return@withLock true
    }

    override suspend fun login(
        username: String,
        password: String,
        totpCode: String?,
    ): Result<StaffSession> = safeApiCall {
        val response = authApi.login(
            LoginRequestDto(
                username = username.trim(),
                password = password,
                totpCode = totpCode?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        activateAuthenticatedSession(response)
    }

    override suspend fun passkeyLoginStart(username: String): Result<PasskeyLoginStartResponseDto> =
        safeApiCall {
            val api = passkeyApi ?: error("Passkey API is not configured.")
            api.loginStart(PasskeyLoginStartRequestDto(username = username.trim()))
        }

    override suspend fun passkeyLoginFinish(request: PasskeyLoginFinishRequestDto): Result<StaffSession> =
        safeApiCall {
            val api = passkeyApi ?: error("Passkey API is not configured.")
            val response = api.loginFinish(request)
            activateAuthenticatedSession(response)
        }

    override suspend fun passkeySecondFactorStart(mfaToken: String): Result<PasskeyLoginStartResponseDto> =
        safeApiCall {
            val api = passkeyApi ?: error("Passkey API is not configured.")
            api.secondFactorStart(PasskeySecondFactorStartRequestDto(mfaToken = mfaToken))
        }

    override suspend fun passkeySecondFactorFinish(
        request: PasskeySecondFactorFinishRequestDto,
    ): Result<StaffSession> = safeApiCall {
        val api = passkeyApi ?: error("Passkey API is not configured.")
        val response = api.secondFactorFinish(request)
        activateAuthenticatedSession(response)
    }

    override suspend fun logout(): Result<Unit> {
        realtimeSocketClient.stop()
        return try {
            safeApiCall {
                withTimeoutOrNull(4_000L) { authApi.logout() }
                    ?: throw GenericApiException("Logout request timed out.")
            }.map { }
        } finally {
            withContext(NonCancellable) {
                realtimeSocketClient.stop()
                cancelInFlightRequests()
                cookieJar.clearAll()
                runSuspendCatching { onLoggedOut() }
                sessionManager.forceUnauthenticated()
            }
        }
    }

    override suspend fun listSessions(): Result<List<UserSessionInfoDto>> = safeApiCall {
        authApi.sessions()
    }

    override suspend fun revokeSession(sessionId: String): Result<Unit> = safeApiCall {
        authApi.revokeSession(sessionId)
    }

    private suspend fun activateAuthenticatedSession(response: AuthResponseDto): StaffSession {
        val origin = currentServerUrlProvider().trim().takeIf { it.isNotEmpty() }
        val resolvedRoles = response.roles.ifEmpty { response.user.roles }
        if (isGuestOnlyAccount(resolvedRoles, response.user.userType)) {
            sessionManager.updateAccessToken(response.accessToken, origin = origin)
            withTimeoutOrNull(3_000L) { runSuspendCatching { authApi.logout() } }
            withContext(NonCancellable) {
                cancelInFlightRequests()
                cookieJar.clearAll()
                sessionManager.forceUnauthenticated()
            }
            throw GenericApiException(
                message = "Guest accounts cannot sign in to the Staff app. Please use the guest portal.",
                statusCode = 403,
                code = "guest_account_not_allowed",
            )
        }
        val session = StaffSession(
            user = response.user,
            roles = resolvedRoles,
            permissions = response.permissions.ifEmpty { response.user.permissions },
            routePolicies = response.routePolicies,
            twoFactorEnrollmentRequired = response.twoFactorEnrollmentRequired,
            twoFactorEnrollmentDeadline = response.twoFactorEnrollmentDeadline,
            recoveryCodesRemaining = response.recoveryCodesRemaining,
        )
        onAuthenticatedUser(response.user.id)
        onInteractiveLogin()
        sessionManager.setAuthenticated(response.accessToken, session, origin = origin)
        withTimeoutOrNull(2_000L) { runSuspendCatching { serverRepository?.refreshBranding() } }
        return session
    }

    companion object {
        internal fun isGuestOnlyAccount(roles: List<String>, userType: String?): Boolean {
            val hasNonGuestRole = roles.any { !it.equals("guest", ignoreCase = true) }
            val hasGuestRole = roles.any { it.equals("guest", ignoreCase = true) }
            if (userType.equals("guest", ignoreCase = true)) {
                return !hasNonGuestRole
            }
            return hasGuestRole && !hasNonGuestRole
        }
    }
}
