package com.hotelapp.staff.feature

import com.hotelapp.staff.core.network.GenericApiException
import com.hotelapp.staff.core.network.TwoFactorExpiredException
import com.hotelapp.staff.core.network.TwoFactorRequiredException
import com.hotelapp.staff.data.dto.PasskeySecondFactorFinishRequestDto
import com.hotelapp.staff.feature.auth.AuthViewModel
import com.hotelapp.staff.testutil.FakeAuthRepository
import com.hotelapp.staff.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `submitLogin validates empty fields locally`() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)

        vm.submitLogin()

        assertEquals("Enter both username/email and password.", vm.uiState.value.errorMessage)
        assertNull(repo.lastLoginArgs)
    }

    @Test
    fun `submitLogin handles 2FA challenge and retries with TOTP`() = runTest {
        val repo = FakeAuthRepository()
        repo.nextLoginResult = Result.failure(
            TwoFactorRequiredException("Enter TOTP", "mfa-123", listOf("totp", "recovery")),
        )
        val vm = AuthViewModel(repo)
        vm.updateUsername("admin")
        vm.updatePassword("secret123")
        vm.submitLogin()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.requiresTwoFactor)
        assertEquals(listOf("totp", "recovery"), vm.uiState.value.twoFactorMethods)

        repo.nextLoginResult = Result.success(com.hotelapp.staff.testutil.TestFixtures.adminSession())
        vm.updateTotpCode("654321")
        vm.submitLogin()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.requiresTwoFactor)
        assertEquals(Triple("admin", "secret123", "654321"), repo.lastLoginArgs)
    }

    @Test
    fun `submitLogin surfaces API errors cleanly`() = runTest {
        val repo = FakeAuthRepository()
        repo.nextLoginResult = Result.failure(GenericApiException("Invalid credentials", statusCode = 401))
        val vm = AuthViewModel(repo)

        vm.updateUsername("admin")
        vm.updatePassword("wrong")
        vm.submitLogin()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals("Invalid credentials", vm.uiState.value.errorMessage)
    }

    @Test
    fun `submitLogin routes generic failures during 2FA to twoFactorError`() = runTest {
        val repo = FakeAuthRepository()
        repo.nextLoginResult = Result.failure(
            TwoFactorRequiredException("Enter TOTP", "mfa-1", listOf("totp")),
        )
        val vm = AuthViewModel(repo)
        vm.updateUsername("admin")
        vm.updatePassword("secret123")
        vm.submitLogin()
        advanceUntilIdle()

        repo.nextLoginResult = Result.failure(GenericApiException("Temporary server error", statusCode = 500))
        vm.updateTotpCode("123456")
        vm.submitLogin()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals("Temporary server error", vm.uiState.value.twoFactorError)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `reportError sets errorMessage for credential helper failures`() {
        val vm = AuthViewModel(FakeAuthRepository())
        vm.reportError("No saved staff credentials found on this device.")
        assertEquals("No saved staff credentials found on this device.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `passkey second factor finish handles TwoFactorExpiredException and resets 2FA sheet`() = runTest {
        val repo = FakeAuthRepository()
        repo.nextLoginResult = Result.failure(
            TwoFactorRequiredException("Enter 2FA", "mfa-exp", listOf("totp", "passkey", "backup_code")),
        )
        repo.nextPasskey2faFinishResult = Result.failure(
            TwoFactorExpiredException("Your sign-in took too long. Enter your password again."),
        )
        val vm = AuthViewModel(repo)
        vm.updateUsername("admin")
        vm.updatePassword("secret123")
        vm.submitLogin()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.requiresTwoFactor)

        vm.verifySecondFactorWithPasskey("saliminn.my") { token, _, _, _ ->
            Result.success(
                PasskeySecondFactorFinishRequestDto(
                    mfaToken = token,
                    credentialId = "c1",
                    authenticatorData = "a1",
                    clientDataJson = "cj",
                    signature = "sig",
                    challenge = "chal",
                ),
            )
        }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.requiresTwoFactor)
        assertNull(vm.uiState.value.mfaToken)
        assertEquals("Your sign-in took too long. Enter your password again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `submitLogin enforces 6 digits for TOTP while permitting alphanumeric backup code`() = runTest {
        val repo = FakeAuthRepository()
        repo.nextLoginResult = Result.failure(
            TwoFactorRequiredException("Enter 2FA", "mfa-2", listOf("totp", "backup_code")),
        )
        val vm = AuthViewModel(repo)
        vm.updateUsername("admin")
        vm.updatePassword("secret123")
        vm.submitLogin()
        advanceUntilIdle()

        vm.updateTotpCode("123")
        vm.submitLogin(isBackupCode = false)
        assertEquals("Enter all 6 digits from your authenticator app.", vm.uiState.value.twoFactorError)

        repo.nextLoginResult = Result.success(com.hotelapp.staff.testutil.TestFixtures.adminSession())
        vm.updateTotpCode("ABCD-1234")
        vm.submitLogin(isBackupCode = true)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.requiresTwoFactor)
    }
}
