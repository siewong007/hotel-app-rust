package com.hotelapp.staff.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorParserTest {

    @Test
    fun `parses two_factor_required with mfa_token and methods`() {
        val body = """
            {
              "error": "2FA required. Please provide a TOTP code or recovery code.",
              "code": "two_factor_required",
              "mfa_token": "mfa_tok_123",
              "available_methods": ["totp", "passkey"]
            }
        """.trimIndent()

        val ex = ApiErrorParser.parse(401, body)
        assertTrue(ex is TwoFactorRequiredException)
        val tfa = ex as TwoFactorRequiredException
        assertEquals("mfa_tok_123", tfa.mfaToken)
        assertEquals(listOf("totp", "passkey"), tfa.availableMethods)
    }

    @Test
    fun `parses two_factor_invalid with attempts_remaining`() {
        val body = """
            {
              "error": "Invalid 2FA code.",
              "code": "two_factor_invalid",
              "attempts_remaining": 3
            }
        """.trimIndent()

        val ex = ApiErrorParser.parse(401, body)
        assertTrue(ex is TwoFactorInvalidException)
        assertEquals(3, (ex as TwoFactorInvalidException).attemptsRemaining)
    }

    @Test
    fun `parses two_factor_expired and two_factor_enrollment_required`() {
        val expired = ApiErrorParser.parse(
            401,
            """{"error":"Your sign-in took too long.","code":"two_factor_expired"}""",
        )
        assertTrue(expired is TwoFactorExpiredException)

        val enroll = ApiErrorParser.parse(
            401,
            """{"error":"Enrollment required.","code":"two_factor_enrollment_required"}""",
        )
        assertTrue(enroll is TwoFactorEnrollmentRequiredException)
    }

    @Test
    fun `parses 429 Retry-After and Turnstile challenge error`() {
        val rateLimited = ApiErrorParser.parse(
            429,
            """{"error":"Too many login attempts."}""",
            retryAfterHeader = "45",
        )
        assertTrue(rateLimited is RateLimitedException)
        assertEquals(45L, (rateLimited as RateLimitedException).retryAfterSeconds)

        val turnstile = ApiErrorParser.parse(
            400,
            """{"error":"Please complete the verification challenge.","code":"bad_request"}""",
        )
        assertTrue(turnstile is TurnstileRequiredException)
    }

    @Test
    fun `parses generic error payload`() {
        val err = ApiErrorParser.parse(409, """{"error":"Room is already occupied"}""")
        assertEquals(409, err.statusCode)
        assertEquals("Room is already occupied", err.message)
    }
}
