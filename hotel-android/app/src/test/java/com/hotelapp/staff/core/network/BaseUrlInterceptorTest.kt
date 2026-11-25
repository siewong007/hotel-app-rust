package com.hotelapp.staff.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BaseUrlInterceptorTest {

    private lateinit var activeServer: MockWebServer
    private lateinit var probeServer: MockWebServer

    @Before
    fun setUp() {
        activeServer = MockWebServer().apply { start() }
        probeServer = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        activeServer.shutdown()
        probeServer.shutdown()
    }

    @Test
    fun `rewrites placeholder host with base path and passes through explicit URLs`() {
        activeServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        probeServer.enqueue(MockResponse().setResponseCode(200).setBody("probe"))

        val baseUrl = activeServer.url("/subpath").toString().trimEnd('/')
        val client = OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor { baseUrl })
            .build()

        client.newCall(
            Request.Builder().url("${NetworkFactory.PLACEHOLDER_BASE_URL}api/bookings").build(),
        ).execute().use { resp ->
            assertEquals(200, resp.code)
        }
        assertEquals("/subpath/api/bookings", activeServer.takeRequest().path)

        val explicitUrl = probeServer.url("/health").toString()
        client.newCall(
            Request.Builder().url(explicitUrl).build(),
        ).execute().use { resp ->
            assertEquals(200, resp.code)
        }
        assertEquals("/health", probeServer.takeRequest().path)
    }
}
