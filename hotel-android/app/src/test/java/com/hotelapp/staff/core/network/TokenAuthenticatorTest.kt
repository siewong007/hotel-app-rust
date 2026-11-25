package com.hotelapp.staff.core.network

import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.data.dto.RefreshTokenResponseDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionManager = SessionManager()
        val session = StaffSession(
            user = UserSummaryDto(id = 1, username = "desk"),
            roles = listOf("reception"),
            permissions = listOf("bookings:read"),
        )
        sessionManager.setAuthenticated("expired_tok", session)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `401 triggers refresh once and retries original request with new token`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token":"fresh_tok"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val refreshClient = OkHttpClient.Builder().build()
        val authenticator = TokenAuthenticator(sessionManager) {
            val req = Request.Builder()
                .url(server.url("/api/auth/refresh"))
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            refreshClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@TokenAuthenticator null
                val raw = resp.body?.string() ?: return@TokenAuthenticator null
                HotelJson.decodeFromString<RefreshTokenResponseDto>(raw).accessToken
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(accessTokenProvider = { sessionManager.getAccessToken() }))
            .authenticator(authenticator)
            .build()

        val resp = client.newCall(
            Request.Builder().url(server.url("/api/bookings")).build(),
        ).execute()

        assertEquals(200, resp.code)
        assertEquals("fresh_tok", sessionManager.getAccessToken())

        val req1 = server.takeRequest()
        assertEquals("/api/bookings", req1.path)
        assertEquals("Bearer expired_tok", req1.getHeader("Authorization"))

        val req2 = server.takeRequest()
        assertEquals("/api/auth/refresh", req2.path)

        val req3 = server.takeRequest()
        assertEquals("/api/bookings", req3.path)
        assertEquals("Bearer fresh_tok", req3.getHeader("Authorization"))
    }

    @Test
    fun `failed refresh clears session and does not loop`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Invalid refresh token"}"""))

        val refreshClient = OkHttpClient.Builder().build()
        val authenticator = TokenAuthenticator(sessionManager) {
            val req = Request.Builder()
                .url(server.url("/api/auth/refresh"))
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            refreshClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@TokenAuthenticator null
                val raw = resp.body?.string() ?: return@TokenAuthenticator null
                HotelJson.decodeFromString<RefreshTokenResponseDto>(raw).accessToken
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(accessTokenProvider = { sessionManager.getAccessToken() }))
            .authenticator(authenticator)
            .build()

        val resp = client.newCall(
            Request.Builder().url(server.url("/api/rooms")).build(),
        ).execute()

        assertEquals(401, resp.code)
        assertEquals(2, server.requestCount)
        assertTrue(sessionManager.authState.value is AuthState.Unauthenticated)
    }
}
