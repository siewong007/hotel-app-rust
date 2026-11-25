package com.hotelapp.staff.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponseDto(
    val status: String,
    val message: String? = null,
)

@Serializable
data class PublicSettingDto(
    val key: String,
    val value: String,
)

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    @SerialName("totp_code") val totpCode: String? = null,
)

@Serializable
data class UserSummaryDto(
    val id: Long,
    val username: String,
    val email: String = "",
    @SerialName("user_type") val userType: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_super_admin") val isSuperAdmin: Boolean = false,
)

@Serializable
data class RouteAccessPolicyDto(
    @SerialName("route_id") val routeId: String,
    val path: String,
    @SerialName("nav_label") val navLabel: String? = null,
    @SerialName("nav_group") val navGroup: String? = null,
    @SerialName("required_permissions") val requiredPermissions: List<String> = emptyList(),
    @SerialName("required_roles") val requiredRoles: List<String> = emptyList(),
    @SerialName("excluded_roles") val excludedRoles: List<String> = emptyList(),
    @SerialName("nav_permissions") val navPermissions: List<String> = emptyList(),
    @SerialName("nav_roles") val navRoles: List<String> = emptyList(),
    @SerialName("nav_excluded_roles") val navExcludedRoles: List<String> = emptyList(),
    @SerialName("is_navigation") val isNavigation: Boolean = false,
)

@Serializable
data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String,
    val user: UserSummaryDto,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    @SerialName("route_policies") val routePolicies: List<RouteAccessPolicyDto> = emptyList(),
    @SerialName("is_first_login") val isFirstLogin: Boolean = false,
    @SerialName("recovery_codes_remaining") val recoveryCodesRemaining: Int? = null,
    @SerialName("profile_complete") val profileComplete: Boolean = true,
    @SerialName("missing_profile_fields") val missingProfileFields: List<String> = emptyList(),
    @SerialName("two_factor_enrollment_required") val twoFactorEnrollmentRequired: Boolean = false,
    @SerialName("two_factor_enrollment_deadline") val twoFactorEnrollmentDeadline: String? = null,
)

@Serializable
data class RefreshTokenResponseDto(
    @SerialName("access_token") val accessToken: String,
)

@Serializable
data class AccessSnapshotDto(
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    @SerialName("route_policies") val routePolicies: List<RouteAccessPolicyDto> = emptyList(),
)

@Serializable
data class UserProfileDto(
    val id: Long,
    val username: String,
    val email: String = "",
    @SerialName("email_configured") val emailConfigured: Boolean = true,
    @SerialName("is_verified") val isVerified: Boolean = true,
    @SerialName("user_type") val userType: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
    @SerialName("profile_complete") val profileComplete: Boolean = true,
    @SerialName("missing_profile_fields") val missingProfileFields: List<String> = emptyList(),
)

@Serializable
data class UserSessionInfoDto(
    val id: String,
    @SerialName("user_agent") val userAgent: String? = null,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("is_current") val isCurrent: Boolean = false,
    val location: String? = null,
    val timezone: String? = null,
)

@Serializable
data class MessageResponseDto(
    val message: String? = null,
)
