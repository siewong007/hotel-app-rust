package com.hotelapp.staff.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlNormalizerTest {

    @Test
    fun `trims whitespace and trailing slashes`() {
        val res = ServerUrlNormalizer.normalize("  http://10.0.2.2:3030///  ", allowCleartext = true)
        assertEquals("http://10.0.2.2:3030", res.getOrThrow())
    }

    @Test
    fun `adds scheme when omitted and strips default ports`() {
        val res = ServerUrlNormalizer.normalize("saliminn.my:443/", allowCleartext = false)
        assertEquals("https://saliminn.my", res.getOrThrow())
    }

    @Test
    fun `rejects cleartext http when allowCleartext is false`() {
        val res = ServerUrlNormalizer.normalize("http://10.0.2.2:3030", allowCleartext = false)
        assertTrue(res.isFailure)
    }

    @Test
    fun `rejects blank input`() {
        assertTrue(ServerUrlNormalizer.normalize("   ", allowCleartext = true).isFailure)
    }
}
