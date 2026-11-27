package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.config.ServerConfigGateway
import com.hotelapp.staff.core.config.ServerUrlNormalizer
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.network.runSuspendCatching
import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.SettingsApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class ServerProbeResult(
    val normalizedUrl: String,
    val hotelName: String?,
    val currency: String?,
    val timezone: String?,
)

interface ServerRepository {
    suspend fun probeAndOptionallySave(
        rawUrl: String,
        saveOnSuccess: Boolean,
    ): Result<ServerProbeResult>

    suspend fun refreshBranding(): Result<Unit>
}

class DefaultServerRepository(
    private val settingsApi: SettingsApi,
    private val serverConfigStore: ServerConfigGateway,
    private val sessionManager: SessionManager,
    private val realtimeSocketClient: RealtimeSocketClient,
    private val onServerChanged: suspend () -> Unit = {},
    private val probeSettingsApi: SettingsApi = settingsApi,
    private val onBeforeServerChangeLogout: suspend () -> Unit = {},
) : ServerRepository {
    override suspend fun probeAndOptionallySave(
        rawUrl: String,
        saveOnSuccess: Boolean,
    ): Result<ServerProbeResult> {
        val previous = serverConfigStore.currentBaseUrl()
        val normalized = ServerUrlNormalizer.normalize(rawUrl).getOrElse { return Result.failure(it) }

        val probeResult = safeApiCall {
            val health = probeSettingsApi.healthAt("$normalized/health")
            if (health.status != "ok") {
                error(health.message ?: "Server health check failed")
            }
            val public = runSuspendCatching { probeSettingsApi.publicSettingsAt("$normalized/api/settings/public") }
                .getOrDefault(emptyList())
            val map = public.associate { it.key to it.value }
            val hotelName = map["hotel_name"]
            val currency = map["currency"]
            val timezone = map["timezone"]
            ServerProbeResult(normalized, hotelName, currency, timezone)
        }

        val probe = probeResult.getOrElse { return Result.failure(it) }
        if (saveOnSuccess) {
            val serverChanged = previous != normalized
            return withContext(NonCancellable) {
                if (serverChanged) {
                    runSuspendCatching { onBeforeServerChangeLogout() }
                    realtimeSocketClient.stop()
                    sessionManager.forceUnauthenticated()
                    onServerChanged()
                }
                serverConfigStore.setServerUrl(normalized).getOrElse { return@withContext Result.failure(it) }
                serverConfigStore.updateBranding(
                    hotelName = probe.hotelName,
                    currency = probe.currency,
                    timezone = probe.timezone,
                    resetMissing = serverChanged,
                )
                Result.success(probe)
            }
        }
        return Result.success(probe)
    }

    override suspend fun refreshBranding(): Result<Unit> = safeApiCall {
        val public = settingsApi.publicSettings()
        val map = public.associate { it.key to it.value }
        val session = (sessionManager.authState.value as? AuthState.Authenticated)?.session
        val resolvedTimezone = map["timezone"]?.takeIf { it.isNotBlank() }
            ?: if (session?.hasPermission("settings:read") == true) {
                runSuspendCatching { settingsApi.settings(category = "general") }
                    .getOrNull()
                    ?.firstOrNull { it.key == "timezone" }
                    ?.value
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        serverConfigStore.updateBranding(
            hotelName = map["hotel_name"],
            currency = map["currency"],
            timezone = resolvedTimezone,
        )
    }
}
