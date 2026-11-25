package com.hotelapp.staff.core.auth

import com.hotelapp.staff.core.network.CookieStorage
import com.hotelapp.staff.core.network.EncryptedCookieJar
import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.core.network.SecretCipher
import com.hotelapp.staff.data.api.AuthApi
import com.hotelapp.staff.data.dto.AccessSnapshotDto
import com.hotelapp.staff.data.dto.AuthResponseDto
import com.hotelapp.staff.data.dto.LoginRequestDto
import com.hotelapp.staff.data.dto.MessageResponseDto
import com.hotelapp.staff.data.dto.RefreshTokenResponseDto
import com.hotelapp.staff.data.dto.UserProfileDto
import com.hotelapp.staff.data.dto.UserSessionInfoDto
import com.hotelapp.staff.data.dto.UserSummaryDto
import com.hotelapp.staff.data.repository.DefaultAuthRepository
import com.hotelapp.staff.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryCookieStorage : CookieStorage {
    var data: ByteArray? = null
    override fun read(): ByteArray? = data
    override fun write(data: ByteArray) { this.data = data }
    override fun clear() { data = null }
}

private class IdentityCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain
    override fun decrypt(cipherText: ByteArray): ByteArray = cipherText
}

private class FakeAuthApi(
    var loginResponse: AuthResponseDto,
    var accessSnapshot: AccessSnapshotDto,
    var profileResponse: UserProfileDto,
) : AuthApi {
    var logoutCalls = 0
    override suspend fun login(request: LoginRequestDto): AuthResponseDto = loginResponse
    override suspend fun refresh(): RefreshTokenResponseDto = RefreshTokenResponseDto("refreshed-jwt")
    override suspend fun logout(): MessageResponseDto {
        logoutCalls++
        return MessageResponseDto("ok")
    }
    override suspend fun accessSnapshot(): AccessSnapshotDto = accessSnapshot
    override suspend fun profile(): UserProfileDto = profileResponse
    override suspend fun sessions(): List<UserSessionInfoDto> = emptyList()
    override suspend fun revokeSession(id: String): MessageResponseDto = MessageResponseDto("ok")
}

class AuthRepositoryGuestGuardTest {

    @Test
    fun `isGuestOnlyAccount detects guest role or guest userType accurately`() {
        assertTrue(DefaultAuthRepository.isGuestOnlyAccount(listOf("guest"), "guest"))
        assertTrue(DefaultAuthRepository.isGuestOnlyAccount(listOf("guest"), null))
        assertTrue(DefaultAuthRepository.isGuestOnlyAccount(emptyList(), "guest"))
        assertFalse(DefaultAuthRepository.isGuestOnlyAccount(listOf("receptionist"), "staff"))
        assertFalse(DefaultAuthRepository.isGuestOnlyAccount(listOf("guest", "staff"), "staff"))
    }

    @Test
    fun `login rejects guest-only accounts and clears auth state`() = runTest {
        val cookieStorage = InMemoryCookieStorage().apply { write("cookie".encodeToByteArray()) }
        val sessionManager = SessionManager()
        val api = FakeAuthApi(
            loginResponse = AuthResponseDto(
                accessToken = "guest-jwt",
                user = UserSummaryDto(id = 77L, username = "guest_portal", userType = "guest", roles = listOf("guest")),
                roles = listOf("guest"),
                permissions = listOf("rooms:read"),
            ),
            accessSnapshot = AccessSnapshotDto(roles = listOf("guest"), permissions = listOf("rooms:read")),
            profileResponse = UserProfileDto(id = 77L, username = "guest_portal", userType = "guest"),
        )
        val repo = DefaultAuthRepository(
            authApi = api,
            serverRepository = null,
            sessionManager = sessionManager,
            cookieJar = EncryptedCookieJar(cookieStorage, IdentityCipher()),
            realtimeSocketClient = TestFixtures.idleSocketClient(),
        )

        val result = repo.login("guest_portal", "HotelStaging2026!")

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull() as GenericApiException
        assertEquals(403, err.statusCode)
        assertEquals("guest_account_not_allowed", err.code)
        assertEquals(1, api.logoutCalls)
        assertNull(cookieStorage.data)
        assertTrue(sessionManager.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun `restoreSession rejects guest accounts and forces unauthenticated`() = runTest {
        val cookieStorage = InMemoryCookieStorage()
        val sessionManager = SessionManager()
        val api = FakeAuthApi(
            loginResponse = AuthResponseDto(
                accessToken = "unused",
                user = UserSummaryDto(id = 77L, username = "guest_portal"),
            ),
            accessSnapshot = AccessSnapshotDto(roles = listOf("guest"), permissions = listOf("rooms:read")),
            profileResponse = UserProfileDto(id = 77L, username = "guest_portal", userType = "guest"),
        )
        val repo = DefaultAuthRepository(
            authApi = api,
            serverRepository = null,
            sessionManager = sessionManager,
            cookieJar = EncryptedCookieJar(cookieStorage, IdentityCipher()),
            realtimeSocketClient = TestFixtures.idleSocketClient(),
        )

        val restored = repo.restoreSession()

        assertFalse(restored)
        assertEquals(1, api.logoutCalls)
        assertTrue(sessionManager.authState.value is AuthState.Unauthenticated)
    }
}
