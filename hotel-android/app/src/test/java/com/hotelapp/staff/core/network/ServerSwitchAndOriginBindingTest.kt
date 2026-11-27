package com.hotelapp.staff.core.network

import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.config.ServerConfigGateway
import com.hotelapp.staff.data.api.SettingsApi
import com.hotelapp.staff.data.dto.HealthResponseDto
import com.hotelapp.staff.data.dto.PublicSettingDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import com.hotelapp.staff.data.repository.DefaultServerRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class ServerSwitchAndOriginBindingTest {

    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer

    @Before
    fun setUp() {
        serverA = MockWebServer().apply { start() }
        serverB = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        serverA.shutdown()
        serverB.shutdown()
    }

    private fun testSession() = StaffSession(
        user = UserSummaryDto(id = 10L, username = "desk"),
        roles = listOf("reception"),
        permissions = listOf("bookings:read"),
    )

    @Test
    fun `session manager binds bearer tokens strictly to originating scheme host and port`() {
        val originA = serverA.url("/").toString().trimEnd('/')
        val originB = serverB.url("/").toString().trimEnd('/')
        val sessionManager = SessionManager()
        sessionManager.setAuthenticated("token-for-a", testSession(), origin = originA)

        assertEquals("token-for-a", sessionManager.getAccessTokenForOrigin(originA))
        assertNull(sessionManager.getAccessTokenForOrigin(originB))
        assertNull(sessionManager.getAccessTokenForOrigin("https://evil.example"))

        var currentBase = originA
        val interceptor = AuthInterceptor(
            accessTokenProvider = { sessionManager.getAccessToken() },
            baseUrlProvider = { currentBase },
            tokenForOriginProvider = { origin -> sessionManager.getAccessTokenForOrigin(origin) },
        )
        serverB.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        // Even if currentBase flips to B before SessionManager is cleared, B must never receive A's token.
        currentBase = originB
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        client.newCall(Request.Builder().url("$originB/api/bookings").build()).execute().close()
        val recordedB = serverB.takeRequest()
        assertNull(recordedB.getHeader("Authorization"))
    }

    @Test
    fun `single flight token refresher drops refreshed token if active server changed mid-flight`() {
        val originA = serverA.url("/").toString().trimEnd('/')
        val originB = serverB.url("/").toString().trimEnd('/')
        val sessionManager = SessionManager()
        sessionManager.setAuthenticated("old-a", testSession(), origin = originA)

        var activeUrl = originA
        val refreshClient = OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor { activeUrl })
            .addInterceptor { chain ->
                // Simulate user switching servers while the in-flight refresh HTTP call executes.
                val resp = chain.proceed(chain.request())
                activeUrl = originB
                resp
            }
            .build()
        serverA.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token":"stale-a"}"""))

        val refresher = SingleFlightTokenRefresher(
            sessionManager = sessionManager,
            refreshClient = refreshClient,
            baseUrlProvider = { activeUrl },
        )
        val outcome = refresher.refreshIfStale("old-a")
        assertTrue(outcome is TokenRefreshOutcome.AuthRejected)
        assertNull(sessionManager.getAccessTokenForOrigin(originB))
    }

    @Test
    fun `server switch stops websocket and clears auth state before persisting new server url`() = runBlocking {
        val originA = serverA.url("/").toString().trimEnd('/')
        val originB = serverB.url("/").toString().trimEnd('/')
        val events = mutableListOf<String>()
        val sessionManager = SessionManager().apply {
            setAuthenticated("tok-a", testSession(), origin = originA)
        }
        val configGateway = object : ServerConfigGateway {
            var currentUrl: String = originA
            override fun currentBaseUrl(): String = currentUrl
            override suspend fun setServerUrl(rawUrl: String): Result<String> {
                events.add("setServerUrl:$rawUrl")
                assertTrue(
                    "SessionManager must be unauthenticated before persisting the new URL",
                    sessionManager.authState.value is AuthState.Unauthenticated,
                )
                assertNull(sessionManager.getAccessToken())
                currentUrl = rawUrl
                return Result.success(rawUrl)
            }
            override suspend fun updateBranding(
                hotelName: String?,
                currency: String?,
                timezone: String?,
                resetMissing: Boolean,
            ) {
                events.add("updateBranding:$hotelName:$resetMissing")
            }
        }
        val wsClient = OkHttpClient.Builder().build()
        val realtime = RealtimeSocketClient(wsClient, { configGateway.currentBaseUrl() }, sessionManager)
        val probeApi = object : SettingsApi {
            override suspend fun health(): HealthResponseDto = HealthResponseDto(status = "ok")
            override suspend fun publicSettings(): List<PublicSettingDto> = emptyList()
            override suspend fun settings(category: String?): List<PublicSettingDto> = emptyList()
            override suspend fun publicSettingsAt(url: String): List<PublicSettingDto> =
                listOf(PublicSettingDto(key = "hotel_name", value = "Server B"))
            override suspend fun healthAt(url: String): HealthResponseDto = HealthResponseDto(status = "ok")
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(NetworkFactory.PLACEHOLDER_BASE_URL)
            .addConverterFactory(HotelJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val dummySettings = retrofit.create(SettingsApi::class.java)
        val repo = DefaultServerRepository(
            settingsApi = dummySettings,
            serverConfigStore = configGateway,
            sessionManager = sessionManager,
            realtimeSocketClient = realtime,
            onServerChanged = {
                assertTrue(
                    "onServerChanged cleanup must run before setServerUrl updates the stored URL",
                    configGateway.currentUrl == originA,
                )
                events.add("onServerChanged")
            },
            probeSettingsApi = probeApi,
        )

        val result = repo.probeAndOptionallySave(originB, saveOnSuccess = true)
        assertTrue(result.isSuccess)
        assertEquals(
            listOf("onServerChanged", "setServerUrl:$originB", "updateBranding:Server B:true"),
            events,
        )
        assertTrue(sessionManager.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun `unorigin-bound token fails closed and base url interceptor rejects invalid url`() {
        val sessionManager = SessionManager()
        sessionManager.setAuthenticated("unscoped-tok", testSession(), origin = null)
        assertNull(sessionManager.getAccessTokenForOrigin("https://example.com"))

        val baseInterceptor = BaseUrlInterceptor { "not-a-valid-url" }
        val chainClient = OkHttpClient.Builder()
            .addInterceptor(baseInterceptor)
            .build()
        var threwIo = false
        try {
            chainClient.newCall(Request.Builder().url(NetworkFactory.PLACEHOLDER_BASE_URL + "api/bookings").build()).execute()
        } catch (_: java.io.IOException) {
            threwIo = true
        }
        assertTrue("BaseUrlInterceptor must fail closed with IOException on invalid base URL", threwIo)
    }

    @Test
    fun `cookie epoch interceptor blocks stale refresh response after clearAll`() {
        val storage = object : CookieStorage {
            var data: ByteArray? = null
            override fun read(): ByteArray? = data
            override fun write(data: ByteArray) { this.data = data }
            override fun clear() { this.data = null }
        }
        val cipher = object : SecretCipher {
            override fun encrypt(plain: ByteArray): ByteArray = plain
            override fun decrypt(cipherText: ByteArray): ByteArray = cipherText
        }
        val jar = EncryptedCookieJar(storage, cipher)
        serverA.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "refresh_token=stale_rotated; Path=/api/auth; Max-Age=3600; HttpOnly")
                .setBody("""{"access_token":"tok"}"""),
        )
        val client = OkHttpClient.Builder()
            .cookieJar(jar)
            .addInterceptor(CookieEpochInterceptor(jar))
            .addNetworkInterceptor { chain ->
                val resp = chain.proceed(chain.request())
                // Simulate concurrent logout clearing cookies before response is committed to BridgeInterceptor's jar.
                jar.clearAll()
                resp
            }
            .build()
        client.newCall(Request.Builder().url(serverA.url("/api/auth/refresh")).build()).execute().close()
        assertTrue("Stale Set-Cookie from in-flight call must not resurrect session after clearAll", !jar.hasStoredCookies())
    }
}
