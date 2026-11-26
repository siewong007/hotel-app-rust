package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.config.ServerUrlNormalizer
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.SettingsApi

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
    private val serverConfigStore: ServerConfigStore,
    private val sessionManager: SessionManager,
    private val realtimeSocketClient: RealtimeSocketClient,
    private val onServerChanged: suspend () -> Unit = {},
    private val probeSettingsApi: SettingsApi = settingsApi,
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
            val public = runCatching { probeSettingsApi.publicSettingsAt("$normalized/api/settings/public") }
                .getOrDefault(emptyList())
            val map = public.associate { it.key to it.value }
            val hotelName = map["hotel_name"]
            val currency = map["currency"]
            val timezone = map["timezone"]
            ServerProbeResult(normalized, hotelName, currency, timezone)
        }

        val probe = probeResult.getOrElse { return Result.failure(it) }
        if (saveOnSuccess) {
            serverConfigStore.setServerUrl(normalized).getOrElse { return Result.failure(it) }
            serverConfigStore.updateBranding(probe.hotelName, probe.currency, probe.timezone)
            if (previous != normalized) {
                realtimeSocketClient.stop()
                sessionManager.forceUnauthenticated()
                onServerChanged()
            }
        }
        return Result.success(probe)
    }

    override suspend fun refreshBranding(): Result<Unit> = safeApiCall {
        val public = settingsApi.publicSettings()
        val map = public.associate { it.key to it.value }
        serverConfigStore.updateBranding(
            hotelName = map["hotel_name"],
            currency = map["currency"],
            timezone = map["timezone"],
        )
    }
}
