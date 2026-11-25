package com.hotelapp.staff.core.network

import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.data.dto.UserSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeNetworkAwarenessTest {

    @Test
    fun offlineNetworkSkipsSocketConnectionUntilOnline() {
        val server = MockWebServer()
        server.start()
        try {
            val sessionManager = SessionManager().apply {
                setAuthenticated(
                    token = "test-jwt",
                    session = StaffSession(
                        user = UserSummaryDto(id = 1L, username = "staff", email = "staff@example.com"),
                        roles = listOf("staff"),
                        permissions = emptyList(),
                    ),
                )
            }
            val onlineFlow = MutableStateFlow(false)
            val client = RealtimeSocketClient(
                client = OkHttpClient(),
                baseUrlProvider = { server.url("/").toString().trimEnd('/') },
                sessionManager = sessionManager,
                isOnlineProvider = { onlineFlow.value },
                networkOnlineFlow = onlineFlow,
            )

            client.start()
            Thread.sleep(120)
            assertEquals(RealtimeConnectionState.DISCONNECTED, client.connectionState.value)
            assertEquals(0, server.requestCount)
            client.stop()
        } finally {
            server.shutdown()
        }
    }
}
