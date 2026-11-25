package com.hotelapp.staff.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PasskeyEncodingTest {

    @Test
    fun `round-trips binary payloads with standard and url-safe base64`() {
        val originalStd = "v/+9AQIDBAUGBwgJCgsMDQ=="
        val url = PasskeyEncoding.standardToBase64Url(originalStd)
        assertFalse(url.contains('+'))
        assertFalse(url.contains('/'))
        assertFalse(url.contains('='))

        val restoredStd = PasskeyEncoding.base64UrlToStandard(url)
        assertEquals(originalStd, restoredStd)
    }

    @Test
    fun `handles unpadded single and double remainder lengths`() {
        val oneByteStd = "/w=="
        val url = PasskeyEncoding.standardToBase64Url(oneByteStd)
        assertEquals("_w", url)
        assertEquals(oneByteStd, PasskeyEncoding.base64UrlToStandard(url))
    }
}
