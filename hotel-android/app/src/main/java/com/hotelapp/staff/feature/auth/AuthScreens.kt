package com.hotelapp.staff.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.auth.StaffCredentialHelper
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelLogoLoader
import com.hotelapp.staff.core.design.HotelLogoMark
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelSegmentedControl
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.network.TwoFactorExpiredException
import com.hotelapp.staff.core.network.TwoFactorInvalidException
import com.hotelapp.staff.core.network.TwoFactorRequiredException
import com.hotelapp.staff.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val totpCode: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val requiresTwoFactor: Boolean = false,
    val twoFactorError: String? = null,
    val twoFactorMethods: List<String> = emptyList(),
)

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun updateTotpCode(value: String) {
        _uiState.update { it.copy(totpCode = value, twoFactorError = null) }
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun dismissTwoFactor() {
        _uiState.update {
            it.copy(
                requiresTwoFactor = false,
                totpCode = "",
                twoFactorError = null,
            )
        }
    }

    fun submitLogin(onPasswordLoginSuccess: ((String, String) -> Unit)? = null) {
        val snapshot = _uiState.value
        if (snapshot.username.isBlank() || snapshot.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter both username/email and password.") }
            return
        }
        if (snapshot.requiresTwoFactor && snapshot.totpCode.isBlank()) {
            _uiState.update { it.copy(twoFactorError = "Enter your 6-digit authenticator code or recovery code.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, twoFactorError = null) }
            val result = authRepository.login(
                username = snapshot.username,
                password = snapshot.password,
                totpCode = if (snapshot.requiresTwoFactor) snapshot.totpCode else null,
            )
            result
                .onSuccess {
                    onPasswordLoginSuccess?.invoke(snapshot.username, snapshot.password)
                    _uiState.value = LoginUiState()
                }
                .onFailure { err ->
                    when (err) {
                        is TwoFactorRequiredException -> {
                            _uiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    requiresTwoFactor = true,
                                    twoFactorMethods = err.availableMethods,
                                    twoFactorError = null,
                                )
                            }
                        }
                        is TwoFactorInvalidException -> {
                            val hint = err.attemptsRemaining?.let { n ->
                                "Invalid code ($n attempt${if (n == 1) "" else "s"} remaining)."
                            } ?: err.message
                            _uiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    requiresTwoFactor = true,
                                    twoFactorError = hint,
                                )
                            }
                        }
                        is TwoFactorExpiredException -> {
                            _uiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    requiresTwoFactor = false,
                                    totpCode = "",
                                    errorMessage = err.message,
                                )
                            }
                        }
                        else -> {
                            _uiState.update {
                                val msg = err.message ?: "Sign-in failed."
                                if (snapshot.requiresTwoFactor) {
                                    it.copy(isSubmitting = false, twoFactorError = msg)
                                } else {
                                    it.copy(isSubmitting = false, errorMessage = msg)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun signInWithPasskey(
        rpId: String,
        onChallengeReady: suspend (
            challengeStandardB64: String,
            allowCredentials: List<com.hotelapp.staff.data.dto.PasskeyAllowCredentialDto>,
            rpId: String,
        ) -> Result<com.hotelapp.staff.data.dto.PasskeyLoginFinishRequestDto>,
    ) {
        val username = _uiState.value.username.trim()
        if (username.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter your username or email before using a passkey.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val startRes = authRepository.passkeyLoginStart(username)
            val start = startRes.getOrElse { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message ?: "Passkey sign-in failed.") }
                return@launch
            }
            val assertion = onChallengeReady(start.challenge, start.allowCredentials, rpId).getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = err.message?.takeIf { m -> !m.contains("cancel", ignoreCase = true) },
                    )
                }
                return@launch
            }
            authRepository.passkeyLoginFinish(assertion)
                .onSuccess {
                    _uiState.value = LoginUiState()
                }
                .onFailure { err ->
                    val rawMsg = err.message.orEmpty()
                    val friendly = if (rawMsg.contains("origin is not allowed", ignoreCase = true)) {
                        "Android passkey sign-in requires Digital Asset Links (assetlinks.json) and Android APK origin allowlisting on the backend server."
                    } else {
                        rawMsg.ifBlank { "Passkey verification failed." }
                    }
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = friendly) }
                }
        }
    }
}

@Composable
fun SplashScreen(hotelName: String = "Hotel PMS") {
    HotelLogoLoader(
        fullScreen = true,
        hotelName = hotelName,
        message = "Restoring session...",
    )
}

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    serverConfigStore: ServerConfigStore,
    onOpenServerConfig: () -> Unit,
    credentialHelper: StaffCredentialHelper? = null,
) {
    val colors = HotelTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberPlatformHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val serverConfig by serverConfigStore.config.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }

    val saveCredentialsOnSuccess: (String, String) -> Unit = { user, pass ->
        if (credentialHelper != null) {
            scope.launch {
                credentialHelper.savePasswordSilently(context, user, pass)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            haptics.reject()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.primaryMain.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            ) {
                HotelLogoMark(size = 76.dp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = serverConfig.hotelName,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Staff Property Management",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))
            AssistChip(
                onClick = {
                    haptics.click()
                    onOpenServerConfig()
                },
                shape = RoundedCornerShape(999.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Change server URL",
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = {
                    Text(
                        text = serverConfig.serverUrl,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = colors.surfaceBase,
                    labelColor = colors.textSecondary,
                    leadingIconContentColor = colors.primaryOnSurface,
                    trailingIconContentColor = colors.primaryOnSurface,
                ),
                border = BorderStroke(1.dp, colors.borderBase),
            )

            Spacer(modifier = Modifier.height(22.dp))
            HotelCard(
                contentPadding = PaddingValues(18.dp),
                borderColor = colors.borderBase,
            ) {
                Text(
                    text = "Staff Sign In",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sign in with your property staff credentials.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::updateUsername,
                    label = { Text("Username or Email") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            viewModel.submitLogin(saveCredentialsOnSuccess)
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                        .semantics { contentType = ContentType.Password },
                )

                AnimatedVisibility(visible = state.errorMessage != null) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        InlineAlertBanner(
                            message = state.errorMessage.orEmpty(),
                            tone = colors.danger,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HotelPrimaryButton(
                    text = if (state.isSubmitting) "Signing In..." else "Sign In",
                    isLoading = state.isSubmitting,
                    onClick = {
                        haptics.click()
                        keyboard?.hide()
                        viewModel.submitLogin(saveCredentialsOnSuccess)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (credentialHelper != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        AssistChip(
                            onClick = {
                                haptics.click()
                                scope.launch {
                                    credentialHelper.getSavedPassword(context)
                                        .onSuccess { saved ->
                                            viewModel.updateUsername(saved.username)
                                            viewModel.updatePassword(saved.password)
                                            viewModel.submitLogin()
                                        }
                                        .onFailure { err ->
                                            val msg = err.message.orEmpty()
                                            if (!msg.contains("cancel", ignoreCase = true)) {
                                                viewModel.reportError(
                                                    msg.ifBlank { "No saved staff credentials found on this device." },
                                                )
                                            }
                                        }
                                }
                            },
                            enabled = !state.isSubmitting,
                            leadingIcon = {
                                Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Saved Login", style = MaterialTheme.typography.labelMedium) },
                        )
                        AssistChip(
                            onClick = {
                                haptics.click()
                                val rpHost = runCatching { java.net.URI(serverConfig.serverUrl).host }.getOrNull()
                                    ?.takeIf { it.isNotBlank() } ?: "saliminn.my"
                                viewModel.signInWithPasskey(rpId = rpHost) { challenge, allowCreds, rpId ->
                                    credentialHelper.signInWithPasskey(context, challenge, allowCreds, rpId)
                                }
                            },
                            enabled = !state.isSubmitting,
                            leadingIcon = {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Passkey", style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
    }

    if (state.requiresTwoFactor) {
        TwoFactorBottomSheet(
            code = state.totpCode,
            error = state.twoFactorError,
            isSubmitting = state.isSubmitting,
            onCodeChange = viewModel::updateTotpCode,
            onConfirm = {
                keyboard?.hide()
                viewModel.submitLogin(saveCredentialsOnSuccess)
            },
            onDismiss = viewModel::dismissTwoFactor,
        )
    }
}

private enum class TwoFactorInputMode { TOTP, RECOVERY }

@Composable
private fun TwoFactorBottomSheet(
    code: String,
    error: String?,
    isSubmitting: Boolean,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HotelTheme.colors
    val focusRequester = remember { FocusRequester() }
    var inputMode by remember { mutableStateOf(TwoFactorInputMode.TOTP) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    HotelModalSheetContainer(
        onDismiss = { if (!isSubmitting) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primarySubtle)
                        .border(1.dp, colors.primarySubtleBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = colors.primaryOnSurface,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Two-Factor Verification",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Verify your staff identity to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            HotelSegmentedControl(
                options = listOf(
                    TwoFactorInputMode.TOTP to "6-Digit Code",
                    TwoFactorInputMode.RECOVERY to "Recovery Code",
                ),
                selected = inputMode,
                onSelect = {
                    inputMode = it
                    onCodeChange("")
                },
            )

            OutlinedTextField(
                value = code,
                onValueChange = { next ->
                    val filtered = if (inputMode == TwoFactorInputMode.TOTP) {
                        next.filter { it.isDigit() }.take(6)
                    } else {
                        next.take(32)
                    }
                    onCodeChange(filtered)
                },
                label = {
                    Text(
                        if (inputMode == TwoFactorInputMode.TOTP) {
                            "6-Digit Authenticator Code"
                        } else {
                            "Backup Recovery Code"
                        },
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                textStyle = if (inputMode == TwoFactorInputMode.TOTP) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (inputMode == TwoFactorInputMode.TOTP) {
                        KeyboardType.Number
                    } else {
                        KeyboardType.Ascii
                    },
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics {
                        if (inputMode == TwoFactorInputMode.TOTP) {
                            contentType = ContentType.SmsOtpCode
                        }
                    },
            )

            AnimatedVisibility(visible = error != null) {
                InlineAlertBanner(message = error.orEmpty(), tone = colors.danger)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
                HotelPrimaryButton(
                    text = if (isSubmitting) "Verifying..." else "Verify",
                    isLoading = isSubmitting,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.3f),
                )
            }
        }
    }
}
