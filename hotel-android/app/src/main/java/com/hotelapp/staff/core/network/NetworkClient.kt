package com.hotelapp.staff.core.network

import com.hotelapp.staff.BuildConfig
import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.data.dto.RefreshTokenResponseDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val HotelJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = false
}

sealed class ApiException(
    override val message: String,
    val statusCode: Int? = null,
    val code: String? = null,
    val rawBody: String? = null,
) : Exception(message)

class TwoFactorRequiredException(
    message: String,
    val mfaToken: String,
    val availableMethods: List<String>,
) : ApiException(message, statusCode = 401, code = "two_factor_required")

class TwoFactorInvalidException(
    message: String,
    val attemptsRemaining: Int?,
) : ApiException(message, statusCode = 401, code = "two_factor_invalid")

class TwoFactorExpiredException(
    message: String,
) : ApiException(message, statusCode = 401, code = "two_factor_expired")

class TwoFactorEnrollmentRequiredException(
    message: String,
) : ApiException(message, statusCode = 401, code = "two_factor_enrollment_required")

class RateLimitedException(
    message: String,
    val retryAfterSeconds: Long?,
) : ApiException(message, statusCode = 429, code = "rate_limited")

class TurnstileRequiredException(
    message: String,
) : ApiException(message, statusCode = 400, code = "turnstile_required")

class GenericApiException(
    message: String,
    statusCode: Int? = null,
    code: String? = null,
    rawBody: String? = null,
) : ApiException(message, statusCode = statusCode, code = code, rawBody = rawBody)

object ApiErrorParser {
    fun parse(
        statusCode: Int,
        body: String?,
        retryAfterHeader: String? = null,
    ): ApiException {
        val obj = body?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { HotelJson.decodeFromString<JsonObject>(raw) }.getOrNull()
        }
        val code = obj?.get("code")?.jsonPrimitive?.content
        val errorMessage = obj?.get("error")?.jsonPrimitive?.content
            ?: obj?.get("message")?.jsonPrimitive?.content

        if (statusCode == 429) {
            val retryAfter = retryAfterHeader?.trim()?.toLongOrNull()
            val msg = errorMessage ?: if (retryAfter != null) {
                "Too many requests. Please try again in $retryAfter seconds."
            } else {
                "Too many requests. Please wait a moment and try again."
            }
            return RateLimitedException(msg, retryAfter)
        }

        when (code) {
            "two_factor_required" -> {
                val mfaToken = obj["mfa_token"]?.jsonPrimitive?.content.orEmpty()
                val methods = obj["available_methods"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
                    ?: listOf("totp")
                return TwoFactorRequiredException(
                    message = errorMessage ?: "Two-factor authentication required.",
                    mfaToken = mfaToken,
                    availableMethods = methods,
                )
            }
            "two_factor_invalid" -> {
                val remaining = obj["attempts_remaining"]?.jsonPrimitive?.intOrNull
                return TwoFactorInvalidException(
                    message = errorMessage ?: "Invalid two-factor authentication code.",
                    attemptsRemaining = remaining,
                )
            }
            "two_factor_expired" -> {
                return TwoFactorExpiredException(
                    message = errorMessage ?: "Your sign-in took too long. Enter your password again.",
                )
            }
            "two_factor_enrollment_required" -> {
                return TwoFactorEnrollmentRequiredException(
                    message = errorMessage
                        ?: "Your role requires two-factor authentication before signing in.",
                )
            }
        }

        if (statusCode == 400 && errorMessage != null &&
            errorMessage.contains("verification challenge", ignoreCase = true)
        ) {
            return TurnstileRequiredException(
                "This server requires a Cloudflare Turnstile web verification challenge on login. Use the web portal or disable Turnstile for mobile API testing.",
            )
        }

        val fallback = errorMessage ?: when (statusCode) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "You don't have permission to perform this action."
            404 -> "The requested item could not be found."
            409 -> "This record changed on the server. Refresh and try again."
            in 500..599 -> "Server error ($statusCode). Please try again shortly."
            else -> "Request failed (HTTP $statusCode)."
        }

        return GenericApiException(
            message = fallback,
            statusCode = statusCode,
            code = code,
            rawBody = body,
        )
    }

    fun fromThrowable(throwable: Throwable): Throwable {
        if (throwable is CancellationException) throw throwable
        if (throwable is ApiException) return throwable
        return when (throwable) {
            is HttpException -> {
                val resp = throwable.response()
                val body = runCatching { resp?.errorBody()?.string() }.getOrNull()
                val retryAfter = resp?.headers()?.get("Retry-After")
                parse(throwable.code(), body, retryAfter)
            }
            is IOException -> GenericApiException(
                "Could not reach the server. Check your network connection and server URL.",
            )
            is SerializationException -> GenericApiException(
                "Unexpected response format from the server.",
            )
            else -> GenericApiException(throwable.message ?: "Unexpected error occurred.")
        }
    }
}

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        Result.failure(ApiErrorParser.fromThrowable(t))
    }

class BaseUrlInterceptor(
    private val baseUrlProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != NetworkFactory.PLACEHOLDER_HOST) {
            return chain.proceed(original)
        }
        val target = baseUrlProvider().toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val basePrefix = target.encodedPath.trimEnd('/')
        val reqPath = original.url.encodedPath
        val mergedPath = if (basePrefix.isEmpty()) reqPath else "$basePrefix$reqPath"

        val newUrl = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .encodedPath(mergedPath)
            .build()

        val updated = original.newBuilder().url(newUrl).build()
        return chain.proceed(updated)
    }
}

class AuthInterceptor(
    private val accessTokenProvider: () -> String?,
    private val timezoneProvider: () -> String? = { sanitizeTimezone(TimeZone.getDefault().id) },
    private val languageProvider: () -> String = { Locale.getDefault().toLanguageTag() },
    private val baseUrlProvider: () -> String = { "" },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }
        if (original.header("Accept-Language") == null) {
            val lang = languageProvider()
            if (lang.isNotBlank()) builder.header("Accept-Language", lang)
        }
        if (original.header("X-Client-Timezone") == null) {
            timezoneProvider()?.let { tz ->
                builder.header("X-Client-Timezone", tz)
            }
        }

        val path = original.url.encodedPath
        val skipAuthHeader = path == "/health" ||
            path.endsWith("/auth/login") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/register") ||
            path.contains("/auth/passkey/login/")
        val configuredBase = baseUrlProvider().trim()
        val matchesConfiguredOrigin = if (configuredBase.isEmpty()) {
            true
        } else {
            val allowed = configuredBase.toHttpUrlOrNull()
            allowed != null &&
                original.url.scheme == allowed.scheme &&
                original.url.host.equals(allowed.host, ignoreCase = true) &&
                original.url.port == allowed.port
        }

        if (!skipAuthHeader && matchesConfiguredOrigin && original.header("Authorization") == null) {
            accessTokenProvider()?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }

    companion object {
        fun sanitizeTimezone(raw: String?): String? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty() || value.length > 64) return null
            val segments = value.split('/')
            if (segments.size > 3) return null
            val ok = segments.all { seg ->
                seg.isNotEmpty() && seg.all { ch ->
                    ch.isLetterOrDigit() && ch.code < 128 || ch == '_' || ch == '-' || ch == '+'
                }
            }
            return if (ok) value else null
        }
    }
}

class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val refreshTokenCall: () -> String?,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.contains("/auth/login") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/logout") ||
            path.contains("/auth/register") ||
            path.contains("/auth/passkey/login/")
        ) {
            return null
        }

        if (responseCount(response) >= 2) {
            return null
        }

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        synchronized(lock) {
            val latestToken = sessionManager.getAccessToken()
            if (!latestToken.isNullOrBlank() && latestToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }

            val newToken = runCatching { refreshTokenCall() }.getOrNull()
            if (!newToken.isNullOrBlank()) {
                sessionManager.updateAccessToken(newToken)
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }

            sessionManager.forceUnauthenticated()
            return null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

enum class RealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

class RealtimeSocketClient(
    private val client: OkHttpClient,
    private val baseUrlProvider: () -> String,
    private val sessionManager: SessionManager,
    private val onUnauthorized: (() -> String?)? = null,
    private val isOnlineProvider: () -> Boolean = { true },
    networkOnlineFlow: StateFlow<Boolean>? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _domainEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val domainEvents: SharedFlow<String> = _domainEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private var activeSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldRun = false
    private var backoffSeconds = 2L

    init {
        if (networkOnlineFlow != null) {
            scope.launch {
                var wasOnline = networkOnlineFlow.value
                networkOnlineFlow.collect { online ->
                    if (online && !wasOnline) {
                        backoffSeconds = 2L
                        reconnectJob?.cancel()
                        reconnectJob = null
                        if (shouldRun && activeSocket == null) {
                            connectIfEligible()
                        }
                    } else if (!online && wasOnline) {
                        reconnectJob?.cancel()
                        reconnectJob = null
                    }
                    wasOnline = online
                }
            }
        }
    }

    fun start() {
        shouldRun = true
        connectIfEligible()
    }

    fun stop() {
        shouldRun = false
        reconnectJob?.cancel()
        reconnectJob = null
        activeSocket?.close(1000, "Client stopped")
        activeSocket = null
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

    fun reconnectNow() {
        activeSocket?.close(1000, "Reconnecting")
        activeSocket = null
        backoffSeconds = 2L
        if (shouldRun) connectIfEligible()
    }

    private fun connectIfEligible() {
        if (!shouldRun) return
        if (!isOnlineProvider()) return
        val token = sessionManager.getAccessToken() ?: return
        if (activeSocket != null) return

        val base = baseUrlProvider().toHttpUrlOrNull() ?: return
        val basePrefix = base.encodedPath.trimEnd('/')
        val wsUrl = base.newBuilder()
            .encodedPath("$basePrefix/api/updates/socket")
            .build()

        _connectionState.value = RealtimeConnectionState.CONNECTING
        val request = Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", "hotel-updates, $token")
            .build()

        activeSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    backoffSeconds = 2L
                    _connectionState.value = RealtimeConnectionState.CONNECTED
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val obj = runCatching { HotelJson.decodeFromString<JsonObject>(text) }.getOrNull()
                    val domain = obj?.get("domain")?.jsonPrimitive?.content
                    if (!domain.isNullOrBlank()) {
                        _domainEvents.tryEmit(domain)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (activeSocket === webSocket) activeSocket = null
                    _connectionState.value = RealtimeConnectionState.DISCONNECTED
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (activeSocket === webSocket) activeSocket = null
                    _connectionState.value = RealtimeConnectionState.DISCONNECTED
                    if (response?.code == 401) {
                        scope.launch {
                            val refreshed = runCatching { onUnauthorized?.invoke() }.getOrNull()
                            if (!refreshed.isNullOrBlank()) {
                                sessionManager.updateAccessToken(refreshed)
                                scheduleReconnect()
                            } else {
                                sessionManager.forceUnauthenticated()
                                shouldRun = false
                            }
                        }
                    } else {
                        scheduleReconnect()
                    }
                }
            },
        )
    }

    private fun scheduleReconnect() {
        if (!shouldRun || !isOnlineProvider() || sessionManager.getAccessToken() == null) return
        if (reconnectJob?.isActive == true) return
        val delayMs = backoffSeconds * 1000L
        backoffSeconds = (backoffSeconds * 2L).coerceAtMost(30L)
        reconnectJob = scope.launch {
            delay(delayMs)
            reconnectJob = null
            connectIfEligible()
        }
    }
}

object NetworkFactory {
    const val PLACEHOLDER_HOST = "hotel.local"
    const val PLACEHOLDER_BASE_URL = "https://$PLACEHOLDER_HOST/"

    fun refreshAccessTokenSync(
        refreshClient: OkHttpClient,
    ): String? {
        val req = Request.Builder()
            .url("${PLACEHOLDER_BASE_URL}api/auth/refresh")
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        return refreshClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            runCatching {
                HotelJson.decodeFromString<RefreshTokenResponseDto>(body).accessToken
            }.getOrNull()
        }
    }

    private fun createLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

    fun createProbeClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }
        return builder.build()
    }

    fun createRefreshClient(
        serverConfigStore: ServerConfigStore,
        cookieJar: EncryptedCookieJar,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(BaseUrlInterceptor { serverConfigStore.currentBaseUrl() })
            .addInterceptor(AuthInterceptor(accessTokenProvider = { null }))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }
        return builder.build()
    }

    fun createMainClient(
        serverConfigStore: ServerConfigStore,
        cookieJar: EncryptedCookieJar,
        sessionManager: SessionManager,
        refreshClient: OkHttpClient,
    ): OkHttpClient {
        val authenticator = TokenAuthenticator(sessionManager) {
            val req = Request.Builder()
                .url("${PLACEHOLDER_BASE_URL}api/auth/refresh")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            refreshClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@TokenAuthenticator null
                val body = resp.body?.string() ?: return@TokenAuthenticator null
                runCatching {
                    HotelJson.decodeFromString<RefreshTokenResponseDto>(body).accessToken
                }.getOrNull()
            }
        }

        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(BaseUrlInterceptor { serverConfigStore.currentBaseUrl() })
            .addInterceptor(
                AuthInterceptor(
                    accessTokenProvider = { sessionManager.getAccessToken() },
                    baseUrlProvider = { serverConfigStore.currentBaseUrl() },
                ),
            )
            .authenticator(authenticator)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }
        return builder.build()
    }

    fun createWebSocketClient(
        serverConfigStore: ServerConfigStore,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor { serverConfigStore.currentBaseUrl() })
            .pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }
        return builder.build()
    }
}
