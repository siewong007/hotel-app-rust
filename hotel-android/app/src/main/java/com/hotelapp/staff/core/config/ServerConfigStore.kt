package com.hotelapp.staff.core.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hotelapp.staff.BuildConfig
import com.hotelapp.staff.core.design.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "hotel_staff_config")

data class ServerConfig(
    val serverUrl: String = BuildConfig.DEFAULT_SERVER_URL,
    val recentUrls: List<String> = emptyList(),
    val themePreference: ThemePreference = ThemePreference.DARK,
    val hotelName: String = "Hotel PMS",
    val currency: String = "MYR",
    val hotelTimezone: String = "Asia/Kuala_Lumpur",
    val recentSearches: List<String> = emptyList(),
    val biometricLockEnabled: Boolean = false,
    val osNotificationsEnabled: Boolean = false,
)

object ServerUrlNormalizer {
    fun normalize(
        raw: String,
        allowCleartext: Boolean = BuildConfig.ALLOW_CLEARTEXT,
    ): Result<String> {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Server URL is required."))
        }
        val withScheme = if ("://" in trimmed) trimmed else {
            if (allowCleartext) "http://$trimmed" else "https://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a valid http:// or https:// server URL."))

        if (!parsed.isHttps && !allowCleartext) {
            return Result.failure(IllegalArgumentException("Release builds require an https:// server URL."))
        }

        val defaultPort = if (parsed.isHttps) 443 else 80
        val portSuffix = if (parsed.port != defaultPort) ":${parsed.port}" else ""
        val pathPart = parsed.encodedPath.trimEnd('/')
        return Result.success("${parsed.scheme}://${parsed.host}$portSuffix$pathPart")
    }
}

class ServerConfigStore(context: Context) {
    private val dataStore = context.applicationContext.configDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    @Volatile
    private var cachedBaseUrl: String = BuildConfig.DEFAULT_SERVER_URL

    init {
        scope.launch {
            val prefs = dataStore.data.first()
            applyPreferences(prefs)
        }
    }

    suspend fun loadInitial(): ServerConfig {
        val prefs = dataStore.data.first()
        return applyPreferences(prefs)
    }

    fun currentBaseUrl(): String = cachedBaseUrl

    suspend fun setServerUrl(rawUrl: String): Result<String> {
        val normalized = ServerUrlNormalizer.normalize(rawUrl)
        val url = normalized.getOrElse { return Result.failure(it) }
        cachedBaseUrl = url
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url
            val existing = decodeLines(prefs[KEY_RECENT_URLS])
            val updated = (listOf(url) + existing.filterNot { it == url }).take(5)
            prefs[KEY_RECENT_URLS] = updated.joinToString("\n")
        }
        val latest = dataStore.data.first()
        applyPreferences(latest)
        return Result.success(url)
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = preference.name
        }
        _config.value = _config.value.copy(themePreference = preference)
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_LOCK] = enabled
        }
        _config.value = _config.value.copy(biometricLockEnabled = enabled)
    }

    suspend fun setOsNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_OS_NOTIFICATIONS] = enabled
        }
        _config.value = _config.value.copy(osNotificationsEnabled = enabled)
    }

    suspend fun addRecentSearch(query: String) {
        val cleaned = query.trim().replace("\n", " ")
        if (cleaned.length < 2) return
        dataStore.edit { prefs ->
            val existing = decodeLines(prefs[KEY_RECENT_SEARCHES])
            val updated = (listOf(cleaned) + existing.filterNot { it.equals(cleaned, ignoreCase = true) }).take(8)
            prefs[KEY_RECENT_SEARCHES] = updated.joinToString("\n")
        }
        val latest = dataStore.data.first()
        applyPreferences(latest)
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_RECENT_SEARCHES)
        }
        _config.value = _config.value.copy(recentSearches = emptyList())
    }

    suspend fun updateBranding(
        hotelName: String?,
        currency: String?,
        timezone: String?,
    ) {
        dataStore.edit { prefs ->
            if (!hotelName.isNullOrBlank()) prefs[KEY_HOTEL_NAME] = hotelName.trim()
            if (!currency.isNullOrBlank()) prefs[KEY_CURRENCY] = currency.trim().uppercase()
            if (!timezone.isNullOrBlank()) prefs[KEY_TIMEZONE] = timezone.trim()
        }
        _config.value = _config.value.copy(
            hotelName = hotelName?.takeIf { it.isNotBlank() } ?: _config.value.hotelName,
            currency = currency?.takeIf { it.isNotBlank() }?.uppercase() ?: _config.value.currency,
            hotelTimezone = timezone?.takeIf { it.isNotBlank() } ?: _config.value.hotelTimezone,
        )
    }

    private fun applyPreferences(prefs: Preferences): ServerConfig {
        val rawUrl = prefs[KEY_SERVER_URL] ?: BuildConfig.DEFAULT_SERVER_URL
        val url = ServerUrlNormalizer.normalize(rawUrl).getOrDefault(BuildConfig.DEFAULT_SERVER_URL)
        cachedBaseUrl = url
        val theme = prefs[KEY_THEME]?.let { name ->
            runCatching { ThemePreference.valueOf(name) }.getOrNull()
        } ?: ThemePreference.DARK
        val loaded = ServerConfig(
            serverUrl = url,
            recentUrls = decodeLines(prefs[KEY_RECENT_URLS]),
            themePreference = theme,
            hotelName = prefs[KEY_HOTEL_NAME] ?: "Hotel PMS",
            currency = prefs[KEY_CURRENCY] ?: "MYR",
            hotelTimezone = prefs[KEY_TIMEZONE] ?: "Asia/Kuala_Lumpur",
            recentSearches = decodeLines(prefs[KEY_RECENT_SEARCHES]),
            biometricLockEnabled = prefs[KEY_BIOMETRIC_LOCK] ?: false,
            osNotificationsEnabled = prefs[KEY_OS_NOTIFICATIONS] ?: false,
        )
        _config.value = loaded
        return loaded
    }

    private fun decodeLines(raw: String?): List<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_RECENT_URLS = stringPreferencesKey("recent_urls")
        private val KEY_THEME = stringPreferencesKey("theme_preference")
        private val KEY_HOTEL_NAME = stringPreferencesKey("hotel_name")
        private val KEY_CURRENCY = stringPreferencesKey("currency")
        private val KEY_TIMEZONE = stringPreferencesKey("hotel_timezone")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        private val KEY_OS_NOTIFICATIONS = booleanPreferencesKey("os_notifications_enabled")
    }
}
