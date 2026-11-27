package com.hotelapp.staff.core.auth

import com.hotelapp.staff.data.dto.RouteAccessPolicyDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class StaffSession(
    val user: UserSummaryDto,
    val roles: List<String>,
    val permissions: List<String>,
    val routePolicies: List<RouteAccessPolicyDto> = emptyList(),
    val twoFactorEnrollmentRequired: Boolean = false,
    val twoFactorEnrollmentDeadline: String? = null,
    val recoveryCodesRemaining: Int? = null,
) {
    fun hasPermission(permission: String): Boolean =
        PermissionChecker.hasPermission(
            permissions = permissions,
            isSuperAdmin = user.isSuperAdmin,
            permission = permission,
        )

    fun hasAnyPermission(vararg required: String): Boolean =
        PermissionChecker.hasAnyPermission(
            permissions = permissions,
            isSuperAdmin = user.isSuperAdmin,
            required = required,
        )
}

object PermissionChecker {
    fun hasPermission(
        permissions: Collection<String>,
        @Suppress("UNUSED_PARAMETER") isSuperAdmin: Boolean,
        permission: String,
    ): Boolean {
        // Mirror backend rbac_cache::has_permission: super_admin accounts derive
        // permissions from their role grants; users.is_super_admin does not bypass RBAC.
        if (permission in permissions) return true
        val resource = permission.substringBefore(':', missingDelimiterValue = "")
        if (resource.isNotEmpty() && "$resource:manage" in permissions) {
            return true
        }
        return false
    }

    fun hasAnyPermission(
        permissions: Collection<String>,
        isSuperAdmin: Boolean,
        vararg required: String,
    ): Boolean = required.any { hasPermission(permissions, isSuperAdmin, it) }
}

sealed interface AuthState {
    data object Checking : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val session: StaffSession) : AuthState
}

data class RequestAuthContext(
    val token: String,
    val origin: String,
    val generation: Long,
)

class SessionManager {
    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenOrigin: String? = null

    @Volatile
    private var sessionGeneration: Long = 0L

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun currentGeneration(): Long = sessionGeneration

    fun getAccessToken(): String? = accessToken

    fun getAccessTokenForOrigin(origin: String): String? {
        synchronized(this) {
            val token = accessToken ?: return null
            val boundOrigin = tokenOrigin ?: return null
            val requestedOrigin = normalizeOrigin(origin) ?: return null
            return if (boundOrigin.equals(requestedOrigin, ignoreCase = true)) token else null
        }
    }

    fun getRequestAuthContextForOrigin(origin: String): RequestAuthContext? {
        synchronized(this) {
            val token = accessToken ?: return null
            val boundOrigin = tokenOrigin ?: return null
            val requestedOrigin = normalizeOrigin(origin) ?: return null
            if (!boundOrigin.equals(requestedOrigin, ignoreCase = true)) return null
            return RequestAuthContext(
                token = token,
                origin = boundOrigin,
                generation = sessionGeneration,
            )
        }
    }

    fun updateAccessToken(token: String, origin: String? = null) {
        synchronized(this) {
            accessToken = token
            if (origin != null) {
                tokenOrigin = normalizeOrigin(origin)
            }
        }
    }

    fun setAuthenticated(token: String, session: StaffSession, origin: String? = null) {
        synchronized(this) {
            sessionGeneration++
            accessToken = token
            tokenOrigin = origin?.let(::normalizeOrigin)
            _authState.value = AuthState.Authenticated(session)
        }
    }

    fun updateSessionAccess(
        roles: List<String>,
        permissions: List<String>,
        routePolicies: List<RouteAccessPolicyDto>,
    ) {
        synchronized(this) {
            val current = _authState.value as? AuthState.Authenticated ?: return
            _authState.value = AuthState.Authenticated(
                current.session.copy(
                    roles = roles,
                    permissions = permissions,
                    routePolicies = routePolicies,
                ),
            )
        }
    }

    fun forceUnauthenticated() {
        synchronized(this) {
            sessionGeneration++
            accessToken = null
            tokenOrigin = null
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun setChecking() {
        _authState.value = AuthState.Checking
    }

    companion object {
        fun originOf(url: HttpUrl): String {
            val scheme = url.scheme.lowercase()
            val rawHost = url.host.lowercase()
            val host = if (':' in rawHost && !rawHost.startsWith('[')) "[$rawHost]" else rawHost
            val defaultPort = if (scheme == "https") 443 else 80
            val portSuffix = if (url.port != defaultPort) ":${url.port}" else ""
            return "$scheme://$host$portSuffix"
        }

        fun normalizeOrigin(urlOrOrigin: String?): String? {
            val raw = urlOrOrigin?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parsed = raw.toHttpUrlOrNull() ?: return null
            return originOf(parsed)
        }
    }
}
