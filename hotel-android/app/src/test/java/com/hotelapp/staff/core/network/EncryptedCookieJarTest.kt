package com.hotelapp.staff.core.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryCookieStorage : CookieStorage {
    var payload: ByteArray? = null
    override fun read(): ByteArray? = payload
    override fun write(data: ByteArray) { payload = data }
    override fun clear() { payload = null }
}

private class TestXorCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun decrypt(cipherText: ByteArray): ByteArray = cipherText.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

class EncryptedCookieJarTest {

    @Test
    fun `persists refresh_token scoped to api auth and survives restart`() {
        val storage = InMemoryCookieStorage()
        val cipher = TestXorCipher()
        var now = 1_000_000L

        val jar1 = EncryptedCookieJar(storage, cipher) { now }
        val loginUrl = "https://hotel.example.com/api/auth/login".toHttpUrl()
        val refreshCookie = Cookie.Builder()
            .name("refresh_token")
            .value("rt_first_value")
            .hostOnlyDomain("hotel.example.com")
            .path("/api/auth")
            .expiresAt(now + 86_400_000L)
            .httpOnly()
            .secure()
            .build()

        jar1.saveFromResponse(loginUrl, listOf(refreshCookie))

        // Reload in a brand-new CookieJar instance (simulating app restart)
        val jar2 = EncryptedCookieJar(storage, cipher) { now }
        val authCookies = jar2.loadForRequest("https://hotel.example.com/api/auth/refresh".toHttpUrl())
        assertEquals(1, authCookies.size)
        assertEquals("rt_first_value", authCookies.first().value)

        // Does NOT leak to non-auth paths, other hosts, or plaintext HTTP
        assertTrue(jar2.loadForRequest("https://hotel.example.com/api/bookings".toHttpUrl()).isEmpty())
        assertTrue(jar2.loadForRequest("https://other.example.com/api/auth/refresh".toHttpUrl()).isEmpty())
        assertTrue(jar2.loadForRequest("http://hotel.example.com/api/auth/refresh".toHttpUrl()).isEmpty())
    }

    @Test
    fun `rotates cookie on refresh and deletes on Max-Age 0`() {
        val storage = InMemoryCookieStorage()
        val cipher = TestXorCipher()
        val now = 1_000_000L
        val jar = EncryptedCookieJar(storage, cipher) { now }
        val refreshUrl = "http://10.0.2.2:3030/api/auth/refresh".toHttpUrl()

        fun buildCookie(value: String, expiresAt: Long): Cookie = Cookie.Builder()
            .name("refresh_token")
            .value(value)
            .hostOnlyDomain("10.0.2.2")
            .path("/api/auth")
            .expiresAt(expiresAt)
            .httpOnly()
            .build()

        jar.saveFromResponse(refreshUrl, listOf(buildCookie("rt_1", now + 60_000L)))
        jar.saveFromResponse(refreshUrl, listOf(buildCookie("rt_2", now + 120_000L)))

        val loaded = jar.loadForRequest(refreshUrl)
        assertEquals(1, loaded.size)
        assertEquals("rt_2", loaded.first().value)

        // Logout clears via expired cookie
        jar.saveFromResponse(refreshUrl, listOf(buildCookie("", now - 1_000L)))
        assertTrue(jar.loadForRequest(refreshUrl).isEmpty())
    }
}
