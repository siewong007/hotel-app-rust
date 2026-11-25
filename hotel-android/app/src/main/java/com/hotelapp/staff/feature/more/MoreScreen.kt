package com.hotelapp.staff.feature.more

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.hotelapp.staff.core.auth.BiometricLockManager
import com.hotelapp.staff.core.notifications.StaffNotificationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.BuildConfig
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.ConfirmActionDialog
import com.hotelapp.staff.core.design.HotelAvatar
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelSegmentedControl
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.ListCardSkeleton
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.ThemePreference
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.data.dto.UserSessionInfoDto
import com.hotelapp.staff.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsUiState(
    val isLoading: Boolean = false,
    val sessions: List<UserSessionInfoDto> = emptyList(),
    val error: String? = null,
    val revokeNotice: String? = null,
)

class MoreViewModel(
    private val authRepository: AuthRepository,
    private val serverConfigStore: ServerConfigStore,
) : ViewModel() {

    private val _sessionsState = MutableStateFlow(SessionsUiState())
    val sessionsState: StateFlow<SessionsUiState> = _sessionsState.asStateFlow()

    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch {
            serverConfigStore.setThemePreference(preference)
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            serverConfigStore.setBiometricLockEnabled(enabled)
        }
    }

    fun setOsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            serverConfigStore.setOsNotificationsEnabled(enabled)
        }
    }

    fun clearRevokeNotice() {
        _sessionsState.update { it.copy(revokeNotice = null) }
    }

    fun loadSessions() {
        viewModelScope.launch {
            _sessionsState.update { it.copy(isLoading = true, error = null) }
            authRepository.listSessions()
                .onSuccess { list ->
                    _sessionsState.update { it.copy(isLoading = false, sessions = list) }
                }
                .onFailure { err ->
                    _sessionsState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun revokeSession(id: String) {
        viewModelScope.launch {
            authRepository.revokeSession(id)
                .onSuccess {
                    _sessionsState.update { it.copy(revokeNotice = "Session revoked.") }
                    loadSessions()
                }
                .onFailure { err ->
                    _sessionsState.update { it.copy(error = err.message) }
                }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    session: StaffSession,
    viewModel: MoreViewModel,
    serverConfigStore: ServerConfigStore,
    onOpenMaintenance: () -> Unit,
    onOpenGuests: () -> Unit,
    onOpenServerConfig: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    onOpenPendingPayments: () -> Unit = {},
    onOpenEkycQueue: () -> Unit = {},
    pendingSyncCount: Int = 0,
    onSyncNow: () -> Unit = {},
    staffNotificationManager: StaffNotificationManager? = null,
) {
    val colors = HotelTheme.colors
    val context = LocalContext.current
    val haptics = rememberPlatformHaptics()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    var showSessionsSheet by remember { mutableStateOf(false) }
    var revokeTargetSession by remember { mutableStateOf<UserSessionInfoDto?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canUseBiometrics = remember(context) { BiometricLockManager.canAuthenticate(context) }
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setOsNotifications(true)
            onShowMessage("System operational alerts enabled.")
        } else {
            viewModel.setOsNotifications(false)
            onShowMessage("Notification permission denied by system settings.")
        }
    }

    val canReadMaintenance = session.hasPermission("maintenance:read")
    val canReadGuests = session.hasPermission("guests:read")
    val canReadPayments = session.hasPermission("payments:read")
    val canReadEkyc = session.hasPermission("ekyc:read")
    val displayName = session.user.fullName?.takeIf { it.isNotBlank() } ?: session.user.username

    LaunchedEffect(sessionsState.revokeNotice) {
        val msg = sessionsState.revokeNotice
        if (msg != null) {
            haptics.confirm()
            onShowMessage(msg)
            viewModel.clearRevokeNotice()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HotelCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HotelAvatar(name = displayName, size = 50.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "@${session.user.username}" + if (session.user.email.isNotBlank()) " · ${session.user.email}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (session.roles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    session.roles.forEach { role ->
                        StatusPill(
                            label = StatusFormatter.humanize(role),
                            tone = colors.info,
                        )
                    }
                }
            }
        }

        if (canReadMaintenance || canReadGuests || canReadPayments || canReadEkyc) {
            HotelCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                Text(
                    text = "Operations",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
                var added = false
                if (canReadMaintenance) {
                    MoreRowItem(
                        icon = Icons.Outlined.Build,
                        title = "Maintenance Tickets",
                        subtitle = "Report & track room repairs",
                        onClick = onOpenMaintenance,
                    )
                    added = true
                }
                if (canReadGuests) {
                    if (added) HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                    MoreRowItem(
                        icon = Icons.Outlined.Group,
                        title = "Guest Directory",
                        subtitle = "Profiles, eKYC status & stay history",
                        onClick = onOpenGuests,
                    )
                    added = true
                }
                if (canReadPayments) {
                    if (added) HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                    MoreRowItem(
                        icon = Icons.Outlined.Payments,
                        title = "Pending Payment Approvals",
                        subtitle = "Verify & approve bank transfer claims",
                        onClick = onOpenPendingPayments,
                    )
                    added = true
                }
                if (canReadEkyc) {
                    if (added) HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                    MoreRowItem(
                        icon = Icons.Outlined.Badge,
                        title = "eKYC Verification Queue",
                        subtitle = "Review guest identity documents & risk flags",
                        onClick = onOpenEkycQueue,
                    )
                }
            }
        }

        HotelCard {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            HotelSegmentedControl(
                options = listOf(
                    ThemePreference.SYSTEM to "System",
                    ThemePreference.DARK to "Dark",
                    ThemePreference.LIGHT to "Light",
                ),
                selected = config.themePreference,
                onSelect = viewModel::setTheme,
            )
        }

        HotelCard(contentPadding = PaddingValues(vertical = 8.dp)) {
            Text(
                text = "Security & Device",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = colors.primaryOnSurface)
                },
                headlineContent = {
                    Text("Biometric App Lock", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                },
                supportingContent = {
                    Text(
                        text = if (canUseBiometrics) "Require fingerprint or PIN when returning after idle" else "Biometric/PIN not configured on device",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = config.biometricLockEnabled,
                        enabled = canUseBiometrics,
                        onCheckedChange = { wantEnable ->
                            if (!wantEnable) {
                                viewModel.setBiometricLock(false)
                            } else {
                                val act = context as? FragmentActivity
                                if (act != null) {
                                    BiometricLockManager.authenticate(
                                        activity = act,
                                        title = "Enable Biometric App Lock",
                                        subtitle = "Verify your identity to enable lock",
                                        onSuccess = {
                                            viewModel.setBiometricLock(true)
                                            onShowMessage("Biometric app lock enabled.")
                                        },
                                        onError = { err -> onShowMessage(err) },
                                    )
                                } else {
                                    viewModel.setBiometricLock(true)
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primaryContrastText,
                            checkedTrackColor = colors.primaryMain,
                        ),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = colors.primaryOnSurface)
                },
                headlineContent = {
                    Text("System Notifications", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                },
                supportingContent = {
                    Text(
                        text = "Android status-bar alerts for arrivals, payments & repairs",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = config.osNotificationsEnabled,
                        onCheckedChange = { wantEnable ->
                            if (!wantEnable) {
                                viewModel.setOsNotifications(false)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                staffNotificationManager?.hasNotificationPermission() != true
                            ) {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setOsNotifications(true)
                                onShowMessage("System operational alerts enabled.")
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primaryContrastText,
                            checkedTrackColor = colors.primaryMain,
                        ),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (pendingSyncCount > 0) {
                HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                ListItem(
                    leadingContent = {
                        Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = colors.warning.fg)
                    },
                    headlineContent = {
                        Text(
                            text = "Offline Sync Queue ($pendingSyncCount)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Queued housekeeping, room or repair updates awaiting network",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            haptics.click()
                            onSyncNow()
                            onShowMessage("Syncing queued updates...")
                        }) {
                            Text("Sync Now", color = colors.primaryOnSurface)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        HotelCard(contentPadding = PaddingValues(vertical = 8.dp)) {
            Text(
                text = "Account & Connection",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            MoreRowItem(
                icon = Icons.Outlined.Devices,
                title = "Active Sessions",
                subtitle = "Review & revoke signed-in devices",
                onClick = {
                    viewModel.loadSessions()
                    showSessionsSheet = true
                },
            )
            HorizontalDivider(
                color = colors.borderSubtle,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            MoreRowItem(
                icon = Icons.Outlined.Dns,
                title = "Server Connection",
                subtitle = config.serverUrl,
                onClick = onOpenServerConfig,
            )
        }

        HotelOutlinedButton(
            text = "Sign Out",
            isDestructive = true,
            icon = Icons.AutoMirrored.Outlined.Logout,
            onClick = { showLogoutConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${config.hotelName} · ${config.currency} · ${config.hotelTimezone}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Staff App v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
    }

    if (showLogoutConfirm) {
        ConfirmActionDialog(
            title = "Sign Out",
            message = "Sign out from ${config.hotelName} on this device?",
            confirmLabel = "Sign Out",
            isDestructive = true,
            onConfirm = {
                showLogoutConfirm = false
                scope.launch { viewModel.logout {} }
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }

    if (showSessionsSheet) {
        HotelModalSheetContainer(onDismiss = { showSessionsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 540.dp)
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Signed-In Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    IconButton(onClick = viewModel::loadSessions) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh sessions",
                            tint = colors.textSecondary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    sessionsState.isLoading -> ListCardSkeleton(
                        count = 3,
                        modifier = Modifier.height(260.dp),
                    )
                    sessionsState.error != null -> InlineAlertBanner(
                        message = sessionsState.error!!,
                        tone = colors.danger,
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 24.dp),
                    ) {
                        items(sessionsState.sessions, key = { it.id }) { s ->
                            HotelCard(
                                accentColor = if (s.isCurrent) colors.success.fg else null,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = s.userAgent ?: "Unknown Device",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val meta = listOfNotNull(s.location, s.ipAddress).joinToString(" · ")
                                        if (meta.isNotEmpty()) {
                                            Text(
                                                text = meta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary,
                                            )
                                        }
                                        Text(
                                            text = "Signed in ${DateFormatter.formatRelativeTimestamp(s.createdAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textMuted,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (s.isCurrent) {
                                        StatusPill(label = "Current", tone = colors.success, leadingDot = true)
                                    } else {
                                        HotelOutlinedButton(
                                            text = "Revoke",
                                            isDestructive = true,
                                            onClick = { revokeTargetSession = s },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    revokeTargetSession?.let { target ->
        ConfirmActionDialog(
            title = "Revoke Device Session",
            message = "Sign out \"${target.userAgent ?: "Unknown Device"}\" immediately?",
            confirmLabel = "Revoke",
            isDestructive = true,
            onConfirm = {
                val id = target.id
                revokeTargetSession = null
                viewModel.revokeSession(id)
            },
            onDismiss = { revokeTargetSession = null },
        )
    }
}

@Composable
private fun MoreRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = HotelTheme.colors
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primarySubtle)
                    .border(1.dp, colors.primarySubtleBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primaryOnSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
