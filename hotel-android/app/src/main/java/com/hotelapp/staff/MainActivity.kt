package com.hotelapp.staff

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.hotelapp.staff.core.print.InvoicePdfPrinter
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
        InvoicePdfPrinter.clearCachedInvoices(applicationContext)
        captureDeepLink(intent)
        val container = (application as HotelStaffApp).container
        splashScreen.setKeepOnScreenCondition {
            container.sessionManager.authState.value is AuthState.Checking
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val serverConfig by container.serverConfigStore.config.collectAsStateWithLifecycle()
            val authState by container.sessionManager.authState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            var isAppLocked by rememberSaveable { mutableStateOf(false) }
            var lockError by remember { mutableStateOf<String?>(null) }
            var lastBackgroundedAtMs by rememberSaveable { mutableLongStateOf(0L) }
            var hasTriggeredColdLock by rememberSaveable { mutableStateOf(false) }
            var previousAuthWasChecking by remember { mutableStateOf(authState is AuthState.Checking) }

            fun promptUnlock() {
                if (!BiometricLockManager.canAuthenticate(this@MainActivity)) {
                    isAppLocked = false
                    return
                }
                lockError = null
                BiometricLockManager.authenticate(
                    activity = this@MainActivity,
                    title = "Unlock ${serverConfig.hotelName}",
                    onSuccess = {
                        isAppLocked = false
                        lockError = null
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

            LaunchedEffect(authState) {
                when (authState) {
                    AuthState.Checking -> {
                        previousAuthWasChecking = true
                    }
                    is AuthState.Authenticated -> {
                        if (previousAuthWasChecking &&
                            serverConfig.biometricLockEnabled &&
                            !hasTriggeredColdLock &&
                            BiometricLockManager.canAuthenticate(this@MainActivity)
                        ) {
                            hasTriggeredColdLock = true
                            isAppLocked = true
                            promptUnlock()
                        }
                        previousAuthWasChecking = false
                    }
                    AuthState.Unauthenticated -> {
                        isAppLocked = false
                        hasTriggeredColdLock = false
                        lastBackgroundedAtMs = 0L
                        previousAuthWasChecking = false
                    }
                }
            }

            LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                lastBackgroundedAtMs = System.currentTimeMillis()
            }

            LifecycleEventEffect(Lifecycle.Event.ON_START) {
                if (authState is AuthState.Authenticated &&
                    serverConfig.biometricLockEnabled &&
                    lastBackgroundedAtMs > 0L &&
                    System.currentTimeMillis() - lastBackgroundedAtMs >= LOCK_IDLE_THRESHOLD_MS &&
                    BiometricLockManager.canAuthenticate(this@MainActivity)
                ) {
                    isAppLocked = true
                    promptUnlock()
                }
            }

            LifecycleStartEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    container.realtimeSocketClient.start()
                }
                onStopOrDispose {
                    container.realtimeSocketClient.stop()
                }
            }

            LaunchedEffect(isAppLocked, authState, serverConfig.biometricLockEnabled) {
                val shouldSecureWindow = authState is AuthState.Authenticated &&
                    (isAppLocked || serverConfig.biometricLockEnabled)
                if (shouldSecureWindow) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            HotelTheme(themePreference = serverConfig.themePreference) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isAppLocked && authState is AuthState.Authenticated) {
                        BiometricLockOverlay(
                            hotelName = serverConfig.hotelName,
                            errorMessage = lockError,
                            onUnlockClick = { promptUnlock() },
                            onSignOut = {
                                isAppLocked = false
                                scope.launch { container.authRepository.logout() }
                            },
                        )
                    } else {
                        HotelAppRoot(
                            container = container,
                            windowWidthSizeClass = windowSizeClass.widthSizeClass,
                            pendingDeepLink = pendingDeepLink,
                            onConsumeDeepLink = { _pendingDeepLink.value = null },
                            deferDeepLinks = isAppLocked,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: Intent?) {
        val data = intent?.dataString ?: intent?.getStringExtra(EXTRA_DEEP_LINK)
        if (!data.isNullOrBlank()) {
            _pendingDeepLink.value = data
            intent?.data = null
            intent?.removeExtra(EXTRA_DEEP_LINK)
        }
    }

    companion object {
        const val EXTRA_DEEP_LINK = "extra_hotel_deep_link"
        private const val LOCK_IDLE_THRESHOLD_MS = 30_000L
    }
}
