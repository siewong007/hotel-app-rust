package com.hotelapp.staff.core.network

import com.hotelapp.staff.BuildConfig
import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.auth.RequestAuthContext
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
) : ApiException(message, statusCode = 403, code = "two_factor_enrollment_required")

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

suspend inline fun <T> runSuspendCatching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        Result.failure(t)
    }

data class RequestSessionTag(
    val generation: Long,
    val origin: String,
)

class BaseUrlInterceptor(
    private val baseUrlProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != NetworkFactory.PLACEHOLDER_HOST) {
            return chain.proceed(original)
        }
        val target = baseUrlProvider().toHttpUrlOrNull()
            ?: throw IOException("Invalid configured server URL")

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
    private val tokenForOriginProvider: ((String) -> String?)? = null,
    private val requestAuthContextForOriginProvider: ((String) -> RequestAuthContext?)? = null,
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
            path.endsWith("/auth/logout") ||
            path.endsWith("/auth/register") ||
            path.contains("/auth/passkey/login/") ||
            path.contains("/auth/passkey/2fa/")
        val requestOrigin = SessionManager.originOf(original.url)
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
            val contextAuth = requestAuthContextForOriginProvider?.invoke(requestOrigin)
            if (contextAuth != null) {
                builder.header("Authorization", "Bearer ${contextAuth.token}")
                builder.tag(
                    RequestSessionTag::class.java,
                    RequestSessionTag(contextAuth.generation, contextAuth.origin),
                )
            } else {
                val token = if (tokenForOriginProvider != null) {
                    tokenForOriginProvider.invoke(requestOrigin)
                } else {
                    accessTokenProvider()
                }
                token?.let {
                    builder.header("Authorization", "Bearer $it")
                }
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

sealed interface TokenRefreshOutcome {
    data class Success(val accessToken: String) : TokenRefreshOutcome
    data object AuthRejected : TokenRefreshOutcome
    data object TransientFailure : TokenRefreshOutcome
}

class SingleFlightTokenRefresher(
    private val sessionManager: SessionManager,
    private val refreshClient: OkHttpClient,
    private val baseUrlProvider: () -> String,
) {
    private val lock = Any()

    fun refreshIfStale(
        failedToken: String?,
        expectedGeneration: Long? = null,
        expectedOrigin: String? = null,
    ): TokenRefreshOutcome {
        if (failedToken.isNullOrBlank()) return TokenRefreshOutcome.TransientFailure
        synchronized(lock) {
            val startGeneration = sessionManager.currentGeneration()
            if (expectedGeneration != null && expectedGeneration != startGeneration) {
                return TokenRefreshOutcome.TransientFailure
            }
            if (sessionManager.authState.value is AuthState.Unauthenticated) {
                return TokenRefreshOutcome.TransientFailure
            }
            val targetOrigin = SessionManager.normalizeOrigin(baseUrlProvider())
            if (expectedOrigin != null && targetOrigin != null && !expectedOrigin.equals(targetOrigin, ignoreCase = true)) {
                return TokenRefreshOutcome.TransientFailure
            }
            val latestToken = if (targetOrigin != null) {
                sessionManager.getAccessTokenForOrigin(targetOrigin)
            } else {
                sessionManager.getAccessToken()
            }
            if (!latestToken.isNullOrBlank() && latestToken != failedToken) {
                return TokenRefreshOutcome.Success(latestToken)
            }
            val outcome = NetworkFactory.refreshAccessTokenOutcome(refreshClient)
            val currentOrigin = SessionManager.normalizeOrigin(baseUrlProvider())
            val endGeneration = sessionManager.currentGeneration()
            val endState = sessionManager.authState.value
            if (endGeneration != startGeneration || endState is AuthState.Unauthenticated) {
                return TokenRefreshOutcome.TransientFailure
            }
            if (targetOrigin != null && currentOrigin != targetOrigin) {
                return if (outcome is TokenRefreshOutcome.Success) {
                    TokenRefreshOutcome.AuthRejected
                } else {
                    TokenRefreshOutcome.TransientFailure
                }
            }
            when (outcome) {
                is TokenRefreshOutcome.Success -> sessionManager.updateAccessToken(outcome.accessToken, origin = targetOrigin)
                TokenRefreshOutcome.AuthRejected -> sessionManager.forceUnauthenticated()
                TokenRefreshOutcome.TransientFailure -> Unit
            }
            return outcome
        }
    }
}

class TokenAuthenticator private constructor(
    private val sessionManager: SessionManager,
    private val configuredOriginProvider: () -> String?,
    private val refreshTokenOutcome: (failedToken: String?, expectedGeneration: Long?, expectedOrigin: String?) -> TokenRefreshOutcome,
) : Authenticator {

    private val fallbackLock = Any()

    constructor(
        sessionManager: SessionManager,
        refreshTokenCall: () -> String?,
    ) : this(
        sessionManager = sessionManager,
        configuredOriginProvider = { null },
        refreshTokenOutcome = legacyRefreshOutcome(sessionManager, refreshTokenCall),
    )

    companion object {
        fun fromOutcome(
            sessionManager: SessionManager,
            configuredOriginProvider: () -> String? = { null },
            refreshTokenOutcome: (failedToken: String?, expectedGeneration: Long?, expectedOrigin: String?) -> TokenRefreshOutcome,
        ): TokenAuthenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            configuredOriginProvider = configuredOriginProvider,
            refreshTokenOutcome = refreshTokenOutcome,
        )

        private fun legacyRefreshOutcome(
            sessionManager: SessionManager,
            refreshTokenCall: () -> String?,
        ): (String?, Long?, String?) -> TokenRefreshOutcome = { failedToken, expectedGeneration, _ ->
            if (expectedGeneration != null && expectedGeneration != sessionManager.currentGeneration()) {
                TokenRefreshOutcome.TransientFailure
            } else {
                val latest = sessionManager.getAccessToken()
                if (!latest.isNullOrBlank() && latest != failedToken) {
                    TokenRefreshOutcome.Success(latest)
                } else {
                    val token = runCatching { refreshTokenCall() }.getOrNull()
                    if (!token.isNullOrBlank()) {
                        sessionManager.updateAccessToken(token)
                        TokenRefreshOutcome.Success(token)
                    } else {
                        sessionManager.forceUnauthenticated()
                        TokenRefreshOutcome.AuthRejected
                    }
                }
            }
        }
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.contains("/auth/login") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/logout") ||
            path.contains("/auth/register") ||
            path.contains("/auth/passkey/login/") ||
            path.contains("/auth/passkey/2fa/")
        ) {
            return null
        }

        if (responseCount(response) >= 2) {
            return null
        }

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val tag = response.request.tag(RequestSessionTag::class.java)
        val requestOrigin = SessionManager.originOf(response.request.url)
        val configuredOrigin = configuredOriginProvider()?.let(SessionManager::normalizeOrigin)
        if (configuredOrigin != null && !requestOrigin.equals(configuredOrigin, ignoreCase = true)) {
            return null
        }
        if (tag != null) {
            if (tag.generation != sessionManager.currentGeneration() ||
                !tag.origin.equals(requestOrigin, ignoreCase = true)
            ) {
                return null
            }
        }

        synchronized(fallbackLock) {
            return when (
                val outcome = refreshTokenOutcome(
                    failedToken,
                    tag?.generation,
                    tag?.origin ?: configuredOrigin,
                )
            ) {
                is TokenRefreshOutcome.Success -> {
                    if (tag != null && tag.generation != sessionManager.currentGeneration()) {
                        return null
                    }
                    val postConfiguredOrigin = configuredOriginProvider()?.let(SessionManager::normalizeOrigin)
                    if (postConfiguredOrigin != null && !requestOrigin.equals(postConfiguredOrigin, ignoreCase = true)) {
                        return null
                    }
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${outcome.accessToken}")
                        .build()
                }
                TokenRefreshOutcome.AuthRejected, TokenRefreshOutcome.TransientFailure -> null
            }
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
    private val onUnauthorizedOutcome: ((failedToken: String?) -> TokenRefreshOutcome)? = null,
    private val elapsedMsProvider: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketLock = Any()

    private val _domainEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val domainEvents: SharedFlow<String> = _domainEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private var activeSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var stabilityResetJob: Job? = null
    private var shouldRun = false
    private var backoffSeconds = 2L
    private var openedAtElapsedMs = 0L
    private var consecutive401Count = 0

    init {
        if (networkOnlineFlow != null) {
            scope.launch {
                var wasOnline = networkOnlineFlow.value
                networkOnlineFlow.collect { online ->
                    if (online && !wasOnline) {
                        val shouldConnect = synchronized(socketLock) {
                            backoffSeconds = 2L
                            consecutive401Count = 0
                            reconnectJob?.cancel()
                            reconnectJob = null
                            shouldRun && activeSocket == null
                        }
                        if (shouldConnect) {
                            connectIfEligible()
                        }
                    } else if (!online && wasOnline) {
                        synchronized(socketLock) {
                            reconnectJob?.cancel()
                            reconnectJob = null
                        }
                    }
                    wasOnline = online
                }
            }
        }
    }

    fun start() {
        synchronized(socketLock) {
            shouldRun = true
            consecutive401Count = 0
        }
        connectIfEligible()
    }

    fun stop() {
        val toClose = synchronized(socketLock) {
            shouldRun = false
            reconnectJob?.cancel()
            reconnectJob = null
            stabilityResetJob?.cancel()
            stabilityResetJob = null
            openedAtElapsedMs = 0L
            consecutive401Count = 0
            val current = activeSocket
            activeSocket = null
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            current
        }
        toClose?.close(1000, "Client stopped")
    }

    fun reconnectNow() {
        val canConnect = sessionManager.authState.value is AuthState.Authenticated ||
            sessionManager.getAccessToken() != null
        val toClose = synchronized(socketLock) {
            reconnectJob?.cancel()
            reconnectJob = null
            stabilityResetJob?.cancel()
            stabilityResetJob = null
            openedAtElapsedMs = 0L
            val current = activeSocket
            activeSocket = null
            backoffSeconds = 2L
            consecutive401Count = 0
            if (canConnect) {
                shouldRun = true
            }
            current
        }
        toClose?.close(1000, "Reconnecting")
        if (canConnect) {
            connectIfEligible()
        }
    }

    private fun connectIfEligible() {
        val token: String
        val wsUrl: okhttp3.HttpUrl
        val attemptGeneration: Long
        synchronized(socketLock) {
            if (!shouldRun || !isOnlineProvider()) return
            if (activeSocket != null) return

            val base = baseUrlProvider().toHttpUrlOrNull() ?: return
            val origin = SessionManager.originOf(base)
            attemptGeneration = sessionManager.currentGeneration()
            token = sessionManager.getAccessTokenForOrigin(origin) ?: return
            val basePrefix = base.encodedPath.trimEnd('/')
            wsUrl = base.newBuilder()
                .encodedPath("$basePrefix/api/updates/socket")
                .build()
            _connectionState.value = RealtimeConnectionState.CONNECTING
        }

        val request = Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", "hotel-updates, $token")
            .build()

        val attemptToken = token
        synchronized(socketLock) {
            if (!shouldRun || activeSocket != null) return
            if (sessionManager.currentGeneration() != attemptGeneration) return
            activeSocket = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        synchronized(socketLock) {
                            if (activeSocket !== webSocket || sessionManager.currentGeneration() != attemptGeneration) return
                            openedAtElapsedMs = elapsedMsProvider()
                            _connectionState.value = RealtimeConnectionState.CONNECTED
                            stabilityResetJob?.cancel()
                            stabilityResetJob = scope.launch {
                                delay(STABILITY_RESET_MS)
                                synchronized(socketLock) {
                                    if (activeSocket === webSocket && sessionManager.currentGeneration() == attemptGeneration) {
                                        backoffSeconds = 2L
                                        consecutive401Count = 0
                                    }
                                }
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val isCurrent = synchronized(socketLock) {
                            activeSocket === webSocket && sessionManager.currentGeneration() == attemptGeneration
                        }
                        if (!isCurrent) return
                        val obj = runCatching { HotelJson.decodeFromString<JsonObject>(text) }.getOrNull()
                        val domain = obj?.get("domain")?.jsonPrimitive?.content
                        if (!domain.isNullOrBlank()) {
                            _domainEvents.tryEmit(domain)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        synchronized(socketLock) {
                            if (activeSocket !== webSocket) return
                            activeSocket = null
                            stabilityResetJob?.cancel()
                            stabilityResetJob = null
                            if (openedAtElapsedMs > 0L && elapsedMsProvider() - openedAtElapsedMs >= STABILITY_RESET_MS) {
                                backoffSeconds = 2L
                                consecutive401Count = 0
                            }
                            openedAtElapsedMs = 0L
                            _connectionState.value = RealtimeConnectionState.DISCONNECTED
                            if (sessionManager.currentGeneration() == attemptGeneration) {
                                scheduleReconnectLocked()
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        synchronized(socketLock) {
                            if (activeSocket !== webSocket) return
                            activeSocket = null
                            stabilityResetJob?.cancel()
                            stabilityResetJob = null
                            if (openedAtElapsedMs > 0L && elapsedMsProvider() - openedAtElapsedMs >= STABILITY_RESET_MS) {
                                backoffSeconds = 2L
                                consecutive401Count = 0
                            }
                            openedAtElapsedMs = 0L
                            _connectionState.value = RealtimeConnectionState.DISCONNECTED
                            if (sessionManager.currentGeneration() != attemptGeneration ||
                                sessionManager.authState.value !is AuthState.Authenticated
                            ) {
                                return
                            }
                        }
                        val statusCode = response?.code
                        when {
                            statusCode == 401 -> {
                                val tooMany401s = synchronized(socketLock) {
                                    consecutive401Count++
                                    if (consecutive401Count > MAX_CONSECUTIVE_WS_401) {
                                        shouldRun = false
                                        true
                                    } else {
                                        false
                                    }
                                }
                                if (tooMany401s) {
                                    if (sessionManager.currentGeneration() == attemptGeneration) {
                                        sessionManager.forceUnauthenticated()
                                    }
                                    return
                                }
                                scope.launch {
                                    if (sessionManager.currentGeneration() != attemptGeneration ||
                                        sessionManager.authState.value !is AuthState.Authenticated
                                    ) {
                                        return@launch
                                    }
                                    if (onUnauthorizedOutcome != null) {
                                        when (onUnauthorizedOutcome.invoke(attemptToken)) {
                                            is TokenRefreshOutcome.Success,
                                            TokenRefreshOutcome.TransientFailure,
                                            -> synchronized(socketLock) {
                                                if (sessionManager.currentGeneration() == attemptGeneration) {
                                                    scheduleReconnectLocked()
                                                }
                                            }
                                            TokenRefreshOutcome.AuthRejected -> synchronized(socketLock) {
                                                shouldRun = false
                                            }
                                        }
                                    } else {
                                        val refreshed = runCatching { onUnauthorized?.invoke() }.getOrNull()
                                        if (sessionManager.currentGeneration() != attemptGeneration) {
                                            return@launch
                                        }
                                        if (!refreshed.isNullOrBlank()) {
                                            sessionManager.updateAccessToken(refreshed)
                                            synchronized(socketLock) { scheduleReconnectLocked() }
                                        } else {
                                            sessionManager.forceUnauthenticated()
                                            synchronized(socketLock) { shouldRun = false }
                                        }
                                    }
                                }
                            }
                            statusCode != null && statusCode in 400..499 && statusCode != 408 && statusCode != 429 -> {
                                synchronized(socketLock) {
                                    shouldRun = false
                                }
                            }
                            else -> {
                                synchronized(socketLock) {
                                    if (sessionManager.currentGeneration() == attemptGeneration) {
                                        scheduleReconnectLocked()
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    private fun scheduleReconnectLocked() {
        if (!shouldRun || !isOnlineProvider() || sessionManager.getAccessToken() == null) return
        if (reconnectJob?.isActive == true) return
        val delayMs = backoffSeconds * 1000L
        backoffSeconds = (backoffSeconds * 2L).coerceAtMost(30L)
        reconnectJob = scope.launch {
            delay(delayMs)
            synchronized(socketLock) {
                reconnectJob = null
            }
            connectIfEligible()
        }
    }

    companion object {
        private const val STABILITY_RESET_MS = 15_000L
        private const val MAX_CONSECUTIVE_WS_401 = 2
    }
}

object NetworkFactory {
    const val PLACEHOLDER_HOST = "hotel.local"
    const val PLACEHOLDER_BASE_URL = "https://$PLACEHOLDER_HOST/"

    fun refreshAccessTokenOutcome(refreshClient: OkHttpClient): TokenRefreshOutcome {
        val req = Request.Builder()
            .url("${PLACEHOLDER_BASE_URL}api/auth/refresh")
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            refreshClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@use TokenRefreshOutcome.TransientFailure
                    val token = runCatching {
                        HotelJson.decodeFromString<RefreshTokenResponseDto>(body).accessToken
                    }.getOrNull()
                    if (!token.isNullOrBlank()) {
                        TokenRefreshOutcome.Success(token)
                    } else {
                        TokenRefreshOutcome.TransientFailure
                    }
                } else if (resp.code == 401 || resp.code == 403) {
                    TokenRefreshOutcome.AuthRejected
                } else {
                    TokenRefreshOutcome.TransientFailure
                }
            }
        } catch (_: IOException) {
            TokenRefreshOutcome.TransientFailure
        }
    }

    fun refreshAccessTokenSync(
        refreshClient: OkHttpClient,
    ): String? = when (val outcome = refreshAccessTokenOutcome(refreshClient)) {
        is TokenRefreshOutcome.Success -> outcome.accessToken
        TokenRefreshOutcome.AuthRejected, TokenRefreshOutcome.TransientFailure -> null
    }

    fun createTokenRefresher(
        sessionManager: SessionManager,
        refreshClient: OkHttpClient,
        baseUrlProvider: () -> String,
    ): SingleFlightTokenRefresher = SingleFlightTokenRefresher(sessionManager, refreshClient, baseUrlProvider)

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
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(CookieEpochInterceptor(cookieJar))
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
        tokenRefresher: SingleFlightTokenRefresher = createTokenRefresher(
            sessionManager = sessionManager,
            refreshClient = refreshClient,
            baseUrlProvider = { serverConfigStore.currentBaseUrl() },
        ),
    ): OkHttpClient {
        val authenticator = TokenAuthenticator.fromOutcome(
            sessionManager = sessionManager,
            configuredOriginProvider = { serverConfigStore.currentBaseUrl() },
            refreshTokenOutcome = tokenRefresher::refreshIfStale,
        )

        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(CookieEpochInterceptor(cookieJar))
            .addInterceptor(BaseUrlInterceptor { serverConfigStore.currentBaseUrl() })
            .addInterceptor(
                AuthInterceptor(
                    accessTokenProvider = { sessionManager.getAccessToken() },
                    baseUrlProvider = { serverConfigStore.currentBaseUrl() },
                    tokenForOriginProvider = { origin -> sessionManager.getAccessTokenForOrigin(origin) },
                    requestAuthContextForOriginProvider = { origin -> sessionManager.getRequestAuthContextForOrigin(origin) },
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
            .followRedirects(false)
            .followSslRedirects(false)
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
