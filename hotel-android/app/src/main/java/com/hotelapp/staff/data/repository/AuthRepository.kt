package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.network.EncryptedCookieJar
import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.AuthApi
import com.hotelapp.staff.data.api.PasskeyApi
import com.hotelapp.staff.data.dto.AuthResponseDto
import com.hotelapp.staff.data.dto.LoginRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginFinishRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartResponseDto
import com.hotelapp.staff.data.dto.UserSessionInfoDto
import com.hotelapp.staff.data.dto.UserSummaryDto

interface AuthRepository {
    suspend fun restoreSession(): Boolean
    suspend fun login(username: String, password: String, totpCode: String? = null): Result<StaffSession>
    suspend fun passkeyLoginStart(username: String): Result<PasskeyLoginStartResponseDto>
    suspend fun passkeyLoginFinish(request: PasskeyLoginFinishRequestDto): Result<StaffSession>
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
) : AuthRepository {

    override suspend fun restoreSession(): Boolean {
        sessionManager.setChecking()
        runCatching { serverRepository?.refreshBranding() }

        val restored = safeApiCall {
            val refreshed = authApi.refresh()
            sessionManager.updateAccessToken(refreshed.accessToken)
            val access = authApi.accessSnapshot()
            val profile = authApi.profile()
            if (isGuestOnlyAccount(access.roles, profile.userType)) {
                runCatching { authApi.logout() }
                cookieJar.clearAll()
                sessionManager.forceUnauthenticated()
                throw GenericApiException(
                    message = "Guest accounts cannot sign in to the Staff app. Please use the guest portal.",
                    statusCode = 403,
                    code = "guest_account_not_allowed",
                )
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
            sessionManager.setAuthenticated(refreshed.accessToken, session)
            realtimeSocketClient.start()
        }

        if (restored.isFailure) {
            sessionManager.forceUnauthenticated()
            return false
        }
        return true
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

    override suspend fun logout(): Result<Unit> {
        realtimeSocketClient.stop()
        val result = safeApiCall { authApi.logout() }
        cookieJar.clearAll()
        sessionManager.forceUnauthenticated()
        runCatching { onLoggedOut() }
        return result.map { }
    }

    override suspend fun listSessions(): Result<List<UserSessionInfoDto>> = safeApiCall {
        authApi.sessions()
    }

    override suspend fun revokeSession(sessionId: String): Result<Unit> = safeApiCall {
        authApi.revokeSession(sessionId)
    }

    private suspend fun activateAuthenticatedSession(response: AuthResponseDto): StaffSession {
        val resolvedRoles = response.roles.ifEmpty { response.user.roles }
        if (isGuestOnlyAccount(resolvedRoles, response.user.userType)) {
            sessionManager.updateAccessToken(response.accessToken)
            runCatching { authApi.logout() }
            cookieJar.clearAll()
            sessionManager.forceUnauthenticated()
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
        sessionManager.setAuthenticated(response.accessToken, session)
        runCatching { serverRepository?.refreshBranding() }
        realtimeSocketClient.start()
        return session
    }

    companion object {
        internal fun isGuestOnlyAccount(roles: List<String>, userType: String?): Boolean {
            if (userType.equals("guest", ignoreCase = true)) return true
            if (roles.isEmpty()) return false
            val nonGuestRoleExists = roles.any { !it.equals("guest", ignoreCase = true) }
            val hasGuestRole = roles.any { it.equals("guest", ignoreCase = true) }
            return hasGuestRole && !nonGuestRoleExists
        }
    }
}
