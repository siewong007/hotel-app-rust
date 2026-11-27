package com.hotelapp.staff.feature.guests

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.DetailScreenSkeleton
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.HotelAvatar
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSnackbarHost
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.HotelTooltipIconButton
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.KeyValueRow
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.copyToClipboard
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuestDetailScreen(
    viewModel: GuestDetailViewModel,
    serverConfigStore: ServerConfigStore,
    onBack: (() -> Unit)?,
    onOpenBookingDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    canReadBookings: Boolean = true,
) {
    val colors = HotelTheme.colors
    val context = LocalContext.current
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val currency = config.currency
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showToast(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun copyAndNotify(label: String, value: String) {
        if (copyToClipboard(context, label, value)) {
            haptics.click()
            showToast("Copied $label")
        }
    }

    OnResumeRefreshEffect { viewModel.refresh(silent = true) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.surfaceApp,
        contentWindowInsets = if (onBack == null) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { HotelSnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = state.profile?.guest?.displayName() ?: "Guest Profile",
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                            }
                        }
                    },
                    actions = {
                        HotelTooltipIconButton(
                            tooltip = "Refresh",
                            onClick = { viewModel.refresh() },
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = colors.textPrimary)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surfaceBase,
                        scrolledContainerColor = colors.surfaceRaised,
                    ),
                )
                HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            }
        },
    ) { padding ->
        when {
            state.isLoading -> DetailScreenSkeleton(modifier = Modifier.padding(padding))
            state.error != null -> ErrorStateView(
                message = state.error!!,
                onRetry = { viewModel.refresh() },
                modifier = Modifier.padding(padding),
            )
            state.profile != null -> {
                val p = state.profile!!
                val g = p.guest
                HotelPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.refreshError?.let { refreshErr ->
                            item {
                                InlineAlertBanner(
                                    message = refreshErr,
                                    tone = colors.danger,
                                )
                            }
                        }

                        if (g.isBlacklisted == true) {
                            item {
                                InlineAlertBanner(
                                    message = "Blacklisted guest" + (g.blacklistReason?.let { ": $it" } ?: ""),
                                    tone = colors.danger,
                                )
                            }
                        }

                        p.summary.activeBookingId?.let { activeId ->
                            item {
                                HotelCard(
                                    onClick = if (canReadBookings) { { onOpenBookingDetail(activeId) } } else null,
                                    containerColor = colors.surfaceSelected,
                                    borderColor = colors.primarySubtleBorder,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Hotel,
                                            contentDescription = null,
                                            tint = colors.primaryOnSurface,
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Currently In-House",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.primaryOnSurface,
                                            )
                                            Text(
                                                text = "Active Stay ${p.summary.activeBookingNumber ?: "#$activeId"}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.textPrimary,
                                            )
                                        }
                                        if (canReadBookings) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChevronRight,
                                                contentDescription = "Open active booking",
                                                tint = colors.primaryOnSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            HotelCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    HotelAvatar(name = g.displayName(), size = 50.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = g.displayName(),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (g.nickName.isNotBlank() && g.nickName != g.displayName()) {
                                            Text(
                                                text = "\"${g.nickName}\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary,
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    StatusPill(
                                        label = "eKYC: ${StatusFormatter.humanize(p.ekycSummary.status)}",
                                        tone = StatusFormatter.ekycTone(p.ekycSummary.status, colors),
                                    )
                                    if (!g.vipStatus.isNullOrBlank() && !g.vipStatus.equals("none", ignoreCase = true)) {
                                        val vipLabel = if (g.vipStatus.equals("vip", ignoreCase = true)) {
                                            "VIP"
                                        } else {
                                            "VIP ${StatusFormatter.humanize(g.vipStatus)}"
                                        }
                                        StatusPill(vipLabel, colors.warning)
                                    }
                                    g.tags?.forEach { tag ->
                                        if (tag.isNotBlank()) {
                                            StatusPill(tag, colors.neutral)
                                        }
                                    }
                                }

                                if (!g.phone.isNullOrBlank() || !g.email.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        g.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                                            ContactChip(
                                                icon = Icons.Outlined.Call,
                                                label = "Call",
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)),
                                                        )
                                                    }.onFailure {
                                                        haptics.reject()
                                                        showToast("No phone dialer app available on this device.")
                                                    }
                                                },
                                            )
                                            ContactChip(
                                                icon = Icons.Outlined.ContentCopy,
                                                label = "Copy Phone",
                                                onClick = { copyAndNotify("Phone", phone) },
                                            )
                                        }
                                        g.email?.takeIf { it.isNotBlank() }?.let { email ->
                                            ContactChip(
                                                icon = Icons.Outlined.Email,
                                                label = "Email",
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null)),
                                                        )
                                                    }.onFailure {
                                                        haptics.reject()
                                                        showToast("No email app available on this device.")
                                                    }
                                                },
                                            )
                                            ContactChip(
                                                icon = Icons.Outlined.ContentCopy,
                                                label = "Copy Email",
                                                onClick = { copyAndNotify("Email", email) },
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = colors.borderSubtle)
                                Spacer(modifier = Modifier.height(8.dp))
                                g.phone?.takeIf { it.isNotBlank() }?.let { KeyValueRow("Phone", it) }
                                g.email?.takeIf { it.isNotBlank() }?.let { KeyValueRow("Email", it) }
                                g.nationality?.takeIf { it.isNotBlank() }?.let { KeyValueRow("Nationality", it) }
                                val location = listOfNotNull(
                                    g.city?.takeIf { it.isNotBlank() },
                                    g.country?.takeIf { it.isNotBlank() },
                                ).joinToString(", ")
                                if (location.isNotEmpty()) {
                                    KeyValueRow("Location", location)
                                }
                                g.companyName?.takeIf { it.isNotBlank() }?.let { KeyValueRow("Company", it) }
                                KeyValueRow("Guest Type", StatusFormatter.humanize(g.guestType))
                                g.tourismType?.takeIf { it.isNotBlank() }?.let { KeyValueRow("Tourism Type", StatusFormatter.humanize(it)) }
                            }
                        }

                        p.sensitive?.let { sens ->
                            item {
                                val dob = sens.dateOfBirth?.takeIf { it.isNotBlank() }
                                val idType = sens.idType?.takeIf { it.isNotBlank() }
                                val idNumber = sens.idNumber?.takeIf { it.isNotBlank() }
                                val idExpiry = sens.idExpiry?.takeIf { it.isNotBlank() }
                                val idCountry = sens.idCountry?.takeIf { it.isNotBlank() }
                                val hasAnySensitive = listOfNotNull(dob, idType, idNumber, idExpiry, idCountry).isNotEmpty()
                                HotelCard {
                                    Text(
                                        text = "Sensitive Identification",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (!hasAnySensitive) {
                                        Text(
                                            text = "No sensitive identification recorded.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    } else {
                                        dob?.let { KeyValueRow("Date of Birth", DateFormatter.formatDate(it)) }
                                        idType?.let { KeyValueRow("ID Type", StatusFormatter.humanize(it)) }
                                        idNumber?.let { KeyValueRow("ID Number", it) }
                                        idExpiry?.let { KeyValueRow("ID Expiry", DateFormatter.formatDate(it)) }
                                        idCountry?.let { KeyValueRow("Issuing Country", it) }
                                    }
                                }
                            }
                        }

                        if (!g.specialRequests.isNullOrBlank() || !g.notes.isNullOrBlank()) {
                            item {
                                HotelCard {
                                    Text("Guest Preferences & Notes", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    g.specialRequests?.takeIf { it.isNotBlank() }?.let {
                                        Text("Special Requests", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                                    }
                                    g.notes?.takeIf { it.isNotBlank() }?.let {
                                        if (!g.specialRequests.isNullOrBlank()) Spacer(modifier = Modifier.height(8.dp))
                                        Text("Staff Notes", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                }
                            }
                        }

                        item {
                            HotelCard {
                                Text("Stay Summary", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    SummaryStatTile(
                                        label = "Bookings",
                                        value = "${p.summary.totalBookings} (${p.summary.completedStays} done)",
                                        modifier = Modifier.weight(1f),
                                    )
                                    SummaryStatTile(
                                        label = "Total Nights",
                                        value = p.summary.totalNights.toString(),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    SummaryStatTile(
                                        label = "Room Revenue",
                                        value = MoneyFormatter.format(p.summary.totalRoomRevenue, currency),
                                        modifier = Modifier.weight(1f),
                                    )
                                    SummaryStatTile(
                                        label = "Outstanding",
                                        value = MoneyFormatter.format(p.summary.outstandingBalance, currency),
                                        valueColor = if (p.summary.outstandingBalance > BigDecimal.ZERO) {
                                            colors.danger.fg
                                        } else {
                                            colors.success.fg
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Stay History (${p.reservations.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                        }

                        items(p.reservations, key = { it.id }) { stay ->
                            val stayTone = StatusFormatter.bookingTone(stay.status, colors)
                            HotelCard(
                                onClick = if (canReadBookings) { { onOpenBookingDetail(stay.id) } } else null,
                                accentColor = stayTone.fg,
                                modifier = Modifier.animateItem(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stay.bookingNumber ?: "#${stay.id}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.primaryOnSurface,
                                    )
                                    StatusPill(
                                        label = StatusFormatter.humanize(stay.status),
                                        tone = stayTone,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Room ${stay.roomNumber} (${stay.roomType}) · ${stay.nights} night(s)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = DateFormatter.formatDateRange(stay.checkInDate, stay.checkOutDate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = MoneyFormatter.format(stay.totalAmount, currency),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.textPrimary,
                                        )
                                        if (stay.balanceDue > BigDecimal.ZERO) {
                                            Text(
                                                text = "Due ${MoneyFormatter.format(stay.balanceDue, currency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.danger.fg,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = HotelTheme.colors
    AssistChip(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = colors.surfaceRaised,
            labelColor = colors.textPrimary,
            leadingIconContentColor = colors.primaryOnSurface,
        ),
        border = BorderStroke(1.dp, colors.borderBase),
    )
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HotelTheme.colors.textPrimary,
) {
    val colors = HotelTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceSunken)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(11.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
