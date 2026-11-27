package com.hotelapp.staff.core.auth

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.hotelapp.staff.core.design.HotelLogoMark
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme

object BiometricLockManager {
    private const val ALLOWED_AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Hotel Staff",
        subtitle: String = "Verify your identity to resume operations",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onError("Unlock prompt unavailable right now. Tap Unlock to try again.")
            return
        }
        runCatching {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errString.toString())
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
            prompt.authenticate(info)
        }.onFailure { err ->
            onError(err.message ?: "Unable to open biometric prompt.")
        }
    }
}

@Composable
fun BiometricLockOverlay(
    hotelName: String,
    errorMessage: String?,
    onUnlockClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    isSigningOut: Boolean = false,
) {
    val colors = HotelTheme.colors
    BackHandler(enabled = true) {}
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceApp),
    ) {
        // Consume stray background touches in Final pass so underlying layered content
        // never receives taps without blocking child Button clicks or vertical scroll.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HotelLogoMark(size = 56.dp)
            Spacer(modifier = Modifier.height(4.dp))
            Icon(
                imageVector = Icons.Outlined.Fingerprint,
                contentDescription = null,
                tint = colors.primaryOnSurface,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "$hotelName Locked",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = errorMessage ?: "Authenticate with biometrics or device PIN to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (errorMessage != null) colors.danger.fg else colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HotelPrimaryButton(
                text = "Unlock",
                enabled = !isSigningOut,
                onClick = onUnlockClick,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onSignOut,
                enabled = !isSigningOut,
            ) {
                Text(
                    text = if (isSigningOut) "Signing Out..." else "Sign Out Instead",
                    color = colors.textSecondary,
                )
            }
        }
        }
    }
}
