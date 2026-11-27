package com.hotelapp.staff.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffCredentialScopingTest {

    @Test
    fun `scopedCredentialId normalizes server origin`() {
        val scoped = StaffCredentialHelper.scopedCredentialId(
            serverOrigin = "HTTPS://Hotel.Example.com:443/api",
            username = " alice ",
        )
        assertEquals("https://hotel.example.com|alice", scoped)
    }

    @Test
    fun `extractUsernameForOrigin accepts matching normalized origin`() {
        val id = StaffCredentialHelper.scopedCredentialId("https://hotel-a.example.com", "alice")
        val extracted = StaffCredentialHelper.extractUsernameForOrigin(id, "HTTPS://HOTEL-A.EXAMPLE.COM:443/")
        assertTrue(extracted.isSuccess)
        assertEquals("alice", extracted.getOrNull())
    }

    @Test
    fun `extractUsernameForOrigin rejects credential from a different server origin`() {
        val id = StaffCredentialHelper.scopedCredentialId("https://hotel-a.example.com", "alice")
        val extracted = StaffCredentialHelper.extractUsernameForOrigin(id, "https://hotel-b.example.com")
        assertTrue(extracted.isFailure)
    }

    @Test
    fun `extractUsernameForOrigin allows legacy unscoped credential IDs`() {
        val extracted = StaffCredentialHelper.extractUsernameForOrigin("alice", "https://hotel-b.example.com")
        assertTrue(extracted.isSuccess)
        assertEquals("alice", extracted.getOrNull())
    }

    @Test
    fun `scopedCredentialId preserves brackets on IPv6 literal origins`() {
        val id = StaffCredentialHelper.scopedCredentialId("http://[::1]:3030/api", "alice")
        assertEquals("http://[::1]:3030|alice", id)
        val extracted = StaffCredentialHelper.extractUsernameForOrigin(id, "http://[::1]:3030")
        assertTrue(extracted.isSuccess)
        assertEquals("alice", extracted.getOrNull())
    }
}
