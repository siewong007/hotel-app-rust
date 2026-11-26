package com.hotelapp.staff.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AuthInterceptorAndProbeIsolationTest {

    private lateinit var activeServer: MockWebServer
    private lateinit var otherServer: MockWebServer

    @Before
    fun setUp() {
        activeServer = MockWebServer().apply { start() }
        otherServer = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        activeServer.shutdown()
        otherServer.shutdown()
    }

    @Test
    fun `auth interceptor attaches bearer token only when request matches configured origin`() {
        val configuredOrigin = activeServer.url("/").toString().trimEnd('/')
        val capturedAuthHeaders = mutableListOf<Pair<String, String?>>()

        val recordingSink = Interceptor { chain ->
            val req = chain.request()
            capturedAuthHeaders.add(req.url.toString() to req.header("Authorization"))
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(
                    accessTokenProvider = { "staff-jwt-123" },
                    baseUrlProvider = { configuredOrigin },
                ),
            )
            .addInterceptor(recordingSink)
            .build()

        // Same scheme + host + port attaches Authorization
        client.newCall(Request.Builder().url("$configuredOrigin/api/bookings").build()).execute().close()
        // Same host but different port is rejected as different origin
        val differentPortUrl = otherServer.url("/api/bookings").toString()
        client.newCall(Request.Builder().url(differentPortUrl).build()).execute().close()
        // Different host is rejected
        client.newCall(Request.Builder().url("https://attacker.invalid/api/bookings").build()).execute().close()
        // Same host+port but wrong scheme is rejected
        val parsed = configuredOrigin.toHttpUrl()
        val wrongSchemeUrl = "https://${parsed.host}:${parsed.port}/api/bookings"
        client.newCall(Request.Builder().url(wrongSchemeUrl).build()).execute().close()

        assertEquals(4, capturedAuthHeaders.size)
        assertEquals("Bearer staff-jwt-123", capturedAuthHeaders[0].second)
        assertNull(capturedAuthHeaders[1].second)
        assertNull(capturedAuthHeaders[2].second)
        assertNull(capturedAuthHeaders[3].second)
    }

    @Test
    fun `probe client disables cookies redirects and auth headers`() = runBlocking {
        otherServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok"}"""),
        )
        otherServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", activeServer.url("/steal-token").toString()),
        )

        val probeClient = NetworkFactory.createProbeClient()
        assertFalse(probeClient.followRedirects)
        assertFalse(probeClient.followSslRedirects)

        // Pre-seed cookies would never be sent by CookieJar.NO_COOKIES
        val probeUrl = otherServer.url("/health")
        assertEquals(emptyList<Cookie>(), probeClient.cookieJar.loadForRequest(probeUrl))

        val settingsApi = Retrofit.Builder()
            .baseUrl(NetworkFactory.PLACEHOLDER_BASE_URL)
            .client(probeClient)
            .addConverterFactory(HotelJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(com.hotelapp.staff.data.api.SettingsApi::class.java)

        val health = settingsApi.healthAt(probeUrl.toString())
        assertEquals("ok", health.status)

        val req1 = otherServer.takeRequest()
        assertNull(req1.getHeader("Authorization"))
        assertNull(req1.getHeader("Cookie"))

        // Second request receives 302 and is NOT automatically followed to activeServer
        probeClient.newCall(Request.Builder().url(probeUrl).build()).execute().use { resp ->
            assertEquals(302, resp.code)
        }
        assertEquals(0, activeServer.requestCount)
    }
}
