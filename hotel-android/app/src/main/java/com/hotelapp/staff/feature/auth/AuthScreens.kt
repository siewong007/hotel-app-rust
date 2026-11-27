package com.hotelapp.staff.feature.auth

import androidx.activity.ComponentActivity
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.auth.CredentialCancelledException
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class LoginSubmitMethod {
    PASSWORD,
    TOTP,
    PASSKEY,
    SAVED_LOGIN,
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val totpCode: String = "",
    val isSubmitting: Boolean = false,
    val submittingMethod: LoginSubmitMethod? = null,
    val errorMessage: String? = null,
    val requiresTwoFactor: Boolean = false,
    val mfaToken: String? = null,
    val twoFactorError: String? = null,
    val twoFactorMethods: List<String> = emptyList(),
)

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var authJob: Job? = null

    fun updateUsername(value: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun updateTotpCode(value: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(totpCode = value, twoFactorError = null) }
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun resetForServerChange() {
        authJob?.cancel()
        authJob = null
        _uiState.value = LoginUiState()
    }

    fun dismissTwoFactor() {
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(
                requiresTwoFactor = false,
                mfaToken = null,
                totpCode = "",
                twoFactorError = null,
                twoFactorMethods = emptyList(),
            )
        }
    }

    fun submitSavedPassword(
        fetchSaved: suspend () -> Result<com.hotelapp.staff.core.auth.SavedPasswordLogin>,
        onPasswordLoginSuccess: ((String, String) -> Unit)? = null,
    ) {
        if (_uiState.value.isSubmitting) return
        authJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    submittingMethod = LoginSubmitMethod.SAVED_LOGIN,
                    errorMessage = null,
                )
            }
            val saved = fetchSaved().getOrElse { err ->
                val message = if (err is CredentialCancelledException) {
                    null
                } else {
                    err.message?.ifBlank { null } ?: "No saved staff credentials found on this device."
                }
                _uiState.update {
                    it.copy(isSubmitting = false, submittingMethod = null, errorMessage = message)
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    username = saved.username,
                    password = saved.password,
                    isSubmitting = false,
                    submittingMethod = null,
                    errorMessage = null,
                )
            }
            submitLogin(onPasswordLoginSuccess = onPasswordLoginSuccess)
        }
    }

    fun submitLogin(
        onPasswordLoginSuccess: ((String, String) -> Unit)? = null,
        isBackupCode: Boolean = false,
    ) {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting) return
        if (snapshot.username.isBlank() || snapshot.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter both username/email and password.") }
            return
        }
        val supportsTotp = snapshot.twoFactorMethods.isEmpty() ||
            snapshot.twoFactorMethods.any { it.equals("totp", ignoreCase = true) }
        val supportsBackup = snapshot.twoFactorMethods.any { it.equals("backup_code", ignoreCase = true) }
        if (snapshot.requiresTwoFactor && !supportsTotp && !supportsBackup) {
            _uiState.update { it.copy(twoFactorError = "Use your passkey to complete two-factor verification.") }
            return
        }
        val trimmedCode = snapshot.totpCode.trim()
        if (snapshot.requiresTwoFactor) {
            if (trimmedCode.isBlank()) {
                _uiState.update {
                    it.copy(twoFactorError = "Enter your 6-digit authenticator code or backup code.")
                }
                return
            }
            if (!isBackupCode && supportsTotp && (trimmedCode.length != 6 || !trimmedCode.all(Char::isDigit))) {
                _uiState.update {
                    it.copy(twoFactorError = "Enter all 6 digits from your authenticator app.")
                }
                return
            }
        }

        val method = if (snapshot.requiresTwoFactor) LoginSubmitMethod.TOTP else LoginSubmitMethod.PASSWORD
        authJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    submittingMethod = method,
                    errorMessage = null,
                    twoFactorError = null,
                )
            }
            val result = authRepository.login(
                username = snapshot.username,
                password = snapshot.password,
                totpCode = if (snapshot.requiresTwoFactor) trimmedCode else null,
            )
            result
                .onSuccess {
                    onPasswordLoginSuccess?.invoke(snapshot.username, snapshot.password)
                    _uiState.value = LoginUiState()
                }
                .onFailure { err ->
                    when (err) {
                        is TwoFactorRequiredException -> {
                            _uiState.update { cur ->
                                cur.copy(
                                    isSubmitting = false,
                                    submittingMethod = null,
                                    requiresTwoFactor = true,
                                    mfaToken = err.mfaToken,
                                    twoFactorMethods = err.availableMethods,
                                    twoFactorError = if (snapshot.requiresTwoFactor) {
                                        err.message
                                    } else {
                                        null
                                    },
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
                                    submittingMethod = null,
                                    requiresTwoFactor = true,
                                    twoFactorError = hint,
                                )
                            }
                        }
                        is TwoFactorExpiredException -> {
                            _uiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    submittingMethod = null,
                                    requiresTwoFactor = false,
                                    mfaToken = null,
                                    totpCode = "",
                                    twoFactorMethods = emptyList(),
                                    errorMessage = err.message,
                                )
                            }
                        }
                        else -> {
                            _uiState.update {
                                val msg = err.message ?: "Sign-in failed."
                                if (snapshot.requiresTwoFactor) {
                                    it.copy(isSubmitting = false, submittingMethod = null, twoFactorError = msg)
                                } else {
                                    it.copy(isSubmitting = false, submittingMethod = null, errorMessage = msg)
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
            username: String,
            challengeStandardB64: String,
            allowCredentials: List<com.hotelapp.staff.data.dto.PasskeyAllowCredentialDto>,
            rpId: String,
        ) -> Result<com.hotelapp.staff.data.dto.PasskeyLoginFinishRequestDto>,
    ) {
        if (_uiState.value.isSubmitting) return
        val username = _uiState.value.username.trim()
        if (username.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter your staff username before using a passkey.") }
            return
        }
        authJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isSubmitting = true, submittingMethod = LoginSubmitMethod.PASSKEY, errorMessage = null)
            }
            val startRes = authRepository.passkeyLoginStart(username)
            val start = startRes.getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submittingMethod = null,
                        errorMessage = err.message ?: "Passkey sign-in failed.",
                    )
                }
                return@launch
            }
            val assertion = onChallengeReady(username, start.challenge, start.allowCredentials, rpId).getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submittingMethod = null,
                        errorMessage = if (err is CredentialCancelledException) null else err.message,
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
                    _uiState.update { it.copy(isSubmitting = false, submittingMethod = null, errorMessage = friendly) }
                }
        }
    }

    fun verifySecondFactorWithPasskey(
        rpId: String,
        onPasswordLoginSuccess: ((String, String) -> Unit)? = null,
        onChallengeReady: suspend (
            mfaToken: String,
            challengeStandardB64: String,
            allowCredentials: List<com.hotelapp.staff.data.dto.PasskeyAllowCredentialDto>,
            rpId: String,
        ) -> Result<com.hotelapp.staff.data.dto.PasskeySecondFactorFinishRequestDto>,
    ) {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting) return
        val mfaToken = snapshot.mfaToken?.takeIf { it.isNotBlank() }
        if (mfaToken == null) {
            _uiState.update { it.copy(twoFactorError = "Missing MFA session token. Enter your password again.") }
            return
        }
        authJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isSubmitting = true, submittingMethod = LoginSubmitMethod.PASSKEY, twoFactorError = null)
            }
            val start = authRepository.passkeySecondFactorStart(mfaToken).getOrElse { err ->
                if (err is TwoFactorExpiredException) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submittingMethod = null,
                            requiresTwoFactor = false,
                            mfaToken = null,
                            totpCode = "",
                            twoFactorMethods = emptyList(),
                            errorMessage = err.message,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submittingMethod = null,
                            twoFactorError = err.message ?: "Passkey verification failed.",
                        )
                    }
                }
                return@launch
            }
            val assertion = onChallengeReady(mfaToken, start.challenge, start.allowCredentials, rpId).getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submittingMethod = null,
                        twoFactorError = if (err is CredentialCancelledException) null else err.message,
                    )
                }
                return@launch
            }
            authRepository.passkeySecondFactorFinish(assertion)
                .onSuccess {
                    onPasswordLoginSuccess?.invoke(snapshot.username, snapshot.password)
                    _uiState.value = LoginUiState()
                }
                .onFailure { err ->
                    if (err is TwoFactorExpiredException) {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submittingMethod = null,
                                requiresTwoFactor = false,
                                mfaToken = null,
                                totpCode = "",
                                twoFactorMethods = emptyList(),
                                errorMessage = err.message,
                            )
                        }
                    } else {
                        val rawMsg = err.message.orEmpty()
                        val friendly = if (rawMsg.contains("origin is not allowed", ignoreCase = true)) {
                            "Android passkey verification requires Digital Asset Links (assetlinks.json) and Android APK origin allowlisting on the backend server."
                        } else {
                            rawMsg.ifBlank { "Passkey verification failed." }
                        }
                        _uiState.update { it.copy(isSubmitting = false, submittingMethod = null, twoFactorError = friendly) }
                    }
                }
        }
    }
}

private fun resolveWebAuthnRpId(serverUrl: String): String? =
    serverUrl.trim().toHttpUrlOrNull()?.host?.takeIf { it.isNotBlank() }

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

    val saveScope = (context as? ComponentActivity)?.lifecycleScope ?: scope
    val saveCredentialsOnSuccess: (String, String) -> Unit = { user, pass ->
        if (credentialHelper != null) {
            val currentOrigin = serverConfig.serverUrl
            saveScope.launch {
                credentialHelper.savePasswordSilently(context, user, pass, currentOrigin)
            }
        }
    }

    var lastServerUrl by remember { mutableStateOf(serverConfig.serverUrl) }
    LaunchedEffect(serverConfig.serverUrl) {
        if (lastServerUrl != serverConfig.serverUrl) {
            lastServerUrl = serverConfig.serverUrl
            viewModel.resetForServerChange()
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
                    enabled = !state.isSubmitting,
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
                    enabled = !state.isSubmitting,
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
                            if (!state.isSubmitting) {
                                keyboard?.hide()
                                viewModel.submitLogin(saveCredentialsOnSuccess)
                            }
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
                val isPasswordSubmitting = state.isSubmitting && state.submittingMethod == LoginSubmitMethod.PASSWORD
                HotelPrimaryButton(
                    text = if (isPasswordSubmitting) "Signing In..." else "Sign In",
                    isLoading = isPasswordSubmitting,
                    enabled = !state.isSubmitting,
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
                                keyboard?.hide()
                                viewModel.submitSavedPassword(
                                    fetchSaved = {
                                        credentialHelper.getSavedPassword(
                                            context,
                                            serverConfig.serverUrl,
                                        )
                                    },
                                    onPasswordLoginSuccess = null,
                                )
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
                                val rpHost = resolveWebAuthnRpId(serverConfig.serverUrl)
                                if (rpHost == null) {
                                    viewModel.reportError("Configured server URL does not have a valid host for passkeys.")
                                    return@AssistChip
                                }
                                viewModel.signInWithPasskey(rpId = rpHost) { user, challenge, allowCreds, rpId ->
                                    credentialHelper.signInWithPasskey(context, user, challenge, allowCreds, rpId)
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
        val rpHost = remember(serverConfig.serverUrl) {
            resolveWebAuthnRpId(serverConfig.serverUrl)
        }
        TwoFactorBottomSheet(
            code = state.totpCode,
            error = state.twoFactorError,
            availableMethods = state.twoFactorMethods,
            isSubmitting = state.isSubmitting,
            submittingMethod = state.submittingMethod,
            onCodeChange = viewModel::updateTotpCode,
            onConfirm = { isBackupCode ->
                keyboard?.hide()
                viewModel.submitLogin(saveCredentialsOnSuccess, isBackupCode = isBackupCode)
            },
            onVerifyWithPasskey = if (credentialHelper != null && rpHost != null) {
                {
                    keyboard?.hide()
                    viewModel.verifySecondFactorWithPasskey(
                        rpId = rpHost,
                        onPasswordLoginSuccess = saveCredentialsOnSuccess,
                    ) { mfaToken, challenge, allowCreds, rpId ->
                        credentialHelper.verifySecondFactorWithPasskey(
                            activityContext = context,
                            mfaToken = mfaToken,
                            challengeStandardB64 = challenge,
                            allowCredentials = allowCreds,
                            rpId = rpId,
                        )
                    }
                }
            } else {
                null
            },
            onDismiss = viewModel::dismissTwoFactor,
        )
    }
}

private enum class TwoFactorInputMode { TOTP, BACKUP_CODE }

@Composable
private fun TwoFactorBottomSheet(
    code: String,
    error: String?,
    availableMethods: List<String>,
    isSubmitting: Boolean,
    submittingMethod: LoginSubmitMethod?,
    onCodeChange: (String) -> Unit,
    onConfirm: (isBackupCode: Boolean) -> Unit,
    onVerifyWithPasskey: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = HotelTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val supportsTotp = availableMethods.isEmpty() ||
        availableMethods.any { it.equals("totp", ignoreCase = true) }
    val supportsBackupCode = availableMethods.any { it.equals("backup_code", ignoreCase = true) }
    val supportsCode = supportsTotp || supportsBackupCode
    val supportsPasskey = availableMethods.any { it.equals("passkey", ignoreCase = true) }
    var inputMode by remember(availableMethods) {
        mutableStateOf(if (supportsTotp) TwoFactorInputMode.TOTP else TwoFactorInputMode.BACKUP_CODE)
    }

    LaunchedEffect(supportsCode, inputMode) {
        if (supportsCode) {
            keyboard?.hide()
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    HotelModalSheetContainer(
        onDismiss = onDismiss,
        dismissible = !isSubmitting,
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

            if (supportsCode) {
                if (supportsTotp && supportsBackupCode) {
                    HotelSegmentedControl(
                        options = listOf(
                            TwoFactorInputMode.TOTP to "6-Digit Code",
                            TwoFactorInputMode.BACKUP_CODE to "Backup Code",
                        ),
                        selected = inputMode,
                        enabled = !isSubmitting,
                        onSelect = {
                            inputMode = it
                            onCodeChange("")
                        },
                    )
                }

                OutlinedTextField(
                    value = code,
                    enabled = !isSubmitting,
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
                                "Backup Code"
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
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isSubmitting) {
                                onConfirm(inputMode == TwoFactorInputMode.BACKUP_CODE)
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics {
                            if (inputMode == TwoFactorInputMode.TOTP) {
                                contentType = ContentType.SmsOtpCode
                            }
                        },
                )
            }

            if (supportsPasskey && onVerifyWithPasskey != null) {
                val isPasskeySubmitting = isSubmitting && submittingMethod == LoginSubmitMethod.PASSKEY
                HotelPrimaryButton(
                    text = if (isPasskeySubmitting) "Verifying Passkey..." else "Verify with Passkey",
                    isLoading = isPasskeySubmitting,
                    enabled = !isSubmitting,
                    onClick = onVerifyWithPasskey,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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
                if (supportsCode) {
                    val isCodeSubmitting = isSubmitting && submittingMethod != LoginSubmitMethod.PASSKEY
                    val canSubmitCode = !isSubmitting && (
                        if (inputMode == TwoFactorInputMode.TOTP) {
                            code.trim().length == 6
                        } else {
                            code.trim().isNotEmpty()
                        }
                        )
                    HotelPrimaryButton(
                        text = if (isCodeSubmitting) "Verifying..." else "Verify",
                        isLoading = isCodeSubmitting,
                        enabled = canSubmitCode,
                        onClick = { onConfirm(inputMode == TwoFactorInputMode.BACKUP_CODE) },
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
        }
    }
}
