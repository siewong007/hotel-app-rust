package com.hotelapp.staff

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.auth.BiometricLockManager
import com.hotelapp.staff.core.auth.BiometricLockOverlay
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.navigation.HotelAppRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val _pendingDeepLink = MutableStateFlow<String?>(null)
    private val pendingDeepLink = _pendingDeepLink.asStateFlow()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        savedInstanceState?.getString(STATE_PENDING_DEEP_LINK)?.let { savedLink ->
            if (_pendingDeepLink.value.isNullOrBlank()) {
                _pendingDeepLink.value = savedLink
            }
        }
        captureDeepLink(intent)
        val container = (application as HotelStaffApp).container
        if (!container.serverConfigStore.isLoaded.value ||
            container.isAppLocked.value ||
            container.serverConfigStore.config.value.biometricLockEnabled
        ) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        splashScreen.setKeepOnScreenCondition {
            !container.serverConfigStore.isLoaded.value ||
                container.sessionManager.authState.value is AuthState.Checking
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isConfigLoaded by container.serverConfigStore.isLoaded.collectAsStateWithLifecycle()
            val serverConfig by container.serverConfigStore.config.collectAsStateWithLifecycle()
            val authState by container.sessionManager.authState.collectAsStateWithLifecycle()
            val isAppLocked by container.isAppLocked.collectAsStateWithLifecycle()
            val hasUnlockedInProcess by container.hasUnlockedInProcess.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            var isSigningOutFromLock by rememberSaveable { mutableStateOf(false) }
            var lockError by remember { mutableStateOf<String?>(null) }

            val needsColdLock = isConfigLoaded &&
                authState is AuthState.Authenticated &&
                serverConfig.biometricLockEnabled &&
                !hasUnlockedInProcess
            val effectiveLocked = isAppLocked || needsColdLock

            fun promptUnlock() {
                if (isSigningOutFromLock) return
                if (!BiometricLockManager.canAuthenticate(this@MainActivity)) {
                    container.setAppLocked(true)
                    lockError = "Device screen lock or biometrics are unavailable. Re-enable device lock in Android Settings or sign out."
                    return
                }
                lockError = null
                BiometricLockManager.authenticate(
                    activity = this@MainActivity,
                    title = "Unlock ${serverConfig.hotelName}",
                    onSuccess = {
                        container.markUnlockedInProcess()
                        container.setAppLocked(false)
                        container.lastBackgroundedElapsedMs = 0L
                        lockError = null
                        if (container.sessionManager.authState.value is AuthState.Authenticated) {
                            container.realtimeSocketClient.start()
                        }
                    },
                    onError = { err ->
                        lockError = err
                    },
                )
            }

            LaunchedEffect(Unit) {
                container.serverConfigStore.loadInitial()
                container.authRepository.restoreSession()
            }

            LaunchedEffect(isConfigLoaded, authState, serverConfig.biometricLockEnabled, hasUnlockedInProcess) {
                if (!isConfigLoaded) return@LaunchedEffect
                when (authState) {
                    AuthState.Checking -> Unit
                    is AuthState.Authenticated -> {
                        isSigningOutFromLock = false
                        if (serverConfig.biometricLockEnabled && !hasUnlockedInProcess) {
                            container.setAppLocked(true)
                            if (!container.hasTriggeredColdLock) {
                                container.hasTriggeredColdLock = true
                                promptUnlock()
                            }
                        } else if (!serverConfig.biometricLockEnabled && !hasUnlockedInProcess) {
                            container.markUnlockedInProcess()
                        }
                    }
                    AuthState.Unauthenticated -> {
                        container.resetLockState()
                        _pendingDeepLink.value = null
                        isSigningOutFromLock = false
                        lockError = null
                    }
                }
            }

            LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                container.lastBackgroundedElapsedMs = SystemClock.elapsedRealtime()
            }

            LifecycleEventEffect(Lifecycle.Event.ON_START) {
                if (isConfigLoaded &&
                    authState is AuthState.Authenticated &&
                    !isSigningOutFromLock &&
                    serverConfig.biometricLockEnabled &&
                    hasUnlockedInProcess &&
                    container.lastBackgroundedElapsedMs > 0L &&
                    SystemClock.elapsedRealtime() - container.lastBackgroundedElapsedMs >= LOCK_IDLE_THRESHOLD_MS
                ) {
                    container.setAppLocked(true)
                    promptUnlock()
                }
            }

            LifecycleStartEffect(isConfigLoaded, authState, effectiveLocked) {
                if (isConfigLoaded && authState is AuthState.Authenticated && !effectiveLocked) {
                    container.realtimeSocketClient.start()
                } else {
                    container.realtimeSocketClient.stop()
                }
                onStopOrDispose {
                    container.realtimeSocketClient.stop()
                }
            }

            LaunchedEffect(isConfigLoaded, effectiveLocked, authState, serverConfig.biometricLockEnabled) {
                val shouldSecureWindow = !isConfigLoaded ||
                    effectiveLocked ||
                    authState !is AuthState.Unauthenticated
                if (shouldSecureWindow) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val signOutFromLock: () -> Unit = {
                if (!isSigningOutFromLock) {
                    isSigningOutFromLock = true
                    lockError = null
                    scope.launch {
                        if (!BiometricLockManager.canAuthenticate(this@MainActivity)) {
                            container.serverConfigStore.setBiometricLockEnabled(false)
                        }
                        container.authRepository.logout()
                    }
                }
            }

            HotelTheme(themePreference = serverConfig.themePreference) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        !isConfigLoaded || authState is AuthState.Checking -> {
                            Surface(modifier = Modifier.fillMaxSize()) {}
                        }
                        needsColdLock -> {
                            BiometricLockOverlay(
                                hotelName = serverConfig.hotelName,
                                errorMessage = lockError,
                                isSigningOut = isSigningOutFromLock,
                                onUnlockClick = { promptUnlock() },
                                onSignOut = signOutFromLock,
                            )
                        }
                        else -> {
                            HotelAppRoot(
                                container = container,
                                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                                pendingDeepLink = pendingDeepLink,
                                onConsumeDeepLink = { _pendingDeepLink.value = null },
                                deferDeepLinks = effectiveLocked,
                            )
                            if (authState is AuthState.Authenticated && effectiveLocked) {
                                BiometricLockOverlay(
                                    hotelName = serverConfig.hotelName,
                                    errorMessage = lockError,
                                    isSigningOut = isSigningOutFromLock,
                                    onUnlockClick = { promptUnlock() },
                                    onSignOut = signOutFromLock,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _pendingDeepLink.value?.let { outState.putString(STATE_PENDING_DEEP_LINK, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: Intent?) {
        val data = intent?.dataString
        if (!data.isNullOrBlank()) {
            _pendingDeepLink.value = data
            intent.data = null
        }
    }

    companion object {
        private const val STATE_PENDING_DEEP_LINK = "state_pending_deep_link"
        private const val LOCK_IDLE_THRESHOLD_MS = 30_000L
    }
}
