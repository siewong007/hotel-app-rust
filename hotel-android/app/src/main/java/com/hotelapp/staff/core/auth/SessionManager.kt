package com.hotelapp.staff.core.auth

import com.hotelapp.staff.data.dto.RouteAccessPolicyDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        isSuperAdmin: Boolean,
        permission: String,
    ): Boolean {
        if (isSuperAdmin) return true
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

class SessionManager {
    @Volatile
    private var accessToken: String? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun getAccessToken(): String? = accessToken

    fun updateAccessToken(token: String) {
        accessToken = token
    }

    fun setAuthenticated(token: String, session: StaffSession) {
        accessToken = token
        _authState.value = AuthState.Authenticated(session)
    }

    fun updateSessionAccess(
        roles: List<String>,
        permissions: List<String>,
        routePolicies: List<RouteAccessPolicyDto>,
    ) {
        val current = _authState.value as? AuthState.Authenticated ?: return
        _authState.value = AuthState.Authenticated(
            current.session.copy(
                roles = roles,
                permissions = permissions,
                routePolicies = routePolicies,
            ),
        )
    }

    fun forceUnauthenticated() {
        accessToken = null
        _authState.value = AuthState.Unauthenticated
    }

    fun setChecking() {
        _authState.value = AuthState.Checking
    }
}
