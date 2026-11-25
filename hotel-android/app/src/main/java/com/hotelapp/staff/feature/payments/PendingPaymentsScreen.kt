package com.hotelapp.staff.feature.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.ConfirmActionDialog
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSnackbarHost
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.HotelTooltipIconButton
import com.hotelapp.staff.core.design.KeyValueRow
import com.hotelapp.staff.core.design.ListCardSkeleton
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.ui.CollectEffects
import com.hotelapp.staff.data.dto.PendingPaymentEntryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingPaymentsScreen(
    session: StaffSession,
    viewModel: PendingPaymentsViewModel,
    serverConfigStore: ServerConfigStore,
    onBack: () -> Unit,
    onOpenBookingDetail: (Long) -> Unit,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val currency = config.currency
    val canApprove = session.hasPermission("payments:approve")
    val canReadBookings = session.hasPermission("bookings:read")
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    var approveTarget by remember { mutableStateOf<PendingPaymentEntryDto?>(null) }
    var rejectTarget by remember { mutableStateOf<PendingPaymentEntryDto?>(null) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PendingPaymentsEffect.ShowMessage -> {
                if (effect.isError) haptics.reject() else haptics.confirm()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    OnResumeRefreshEffect { viewModel.refresh(silent = true) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.surfaceApp,
        snackbarHost = { HotelSnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Payment Approvals (${state.total})", color = colors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when {
                state.isLoading -> ListCardSkeleton()
                state.error != null && state.items.isEmpty() -> ErrorStateView(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                )
                else -> HotelPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.items.isEmpty()) {
                        EmptyStateView(
                            title = "No pending payments",
                            subtitle = "All bank transfers and payment claims have been reviewed.",
                            icon = Icons.Outlined.Payments,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.items, key = { it.id }) { entry ->
                                val tone = StatusFormatter.paymentTone(entry.status, colors)
                                HotelCard(
                                    onClick = if (canReadBookings) { { onOpenBookingDetail(entry.bookingId) } } else null,
                                    accentColor = tone.fg,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.guestName ?: "Guest",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colors.textPrimary,
                                            )
                                            Text(
                                                text = listOfNotNull(
                                                    entry.bookingNumber,
                                                    entry.roomNumber?.let { "Room $it" },
                                                ).joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.primaryOnSurface,
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = MoneyFormatter.format(entry.amount, currency),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colors.textPrimary,
                                            )
                                            StatusPill(label = StatusFormatter.humanize(entry.status), tone = tone)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    KeyValueRow("Method", StatusFormatter.humanize(entry.paymentMethod))
                                    entry.reference?.takeIf { it.isNotBlank() }?.let { KeyValueRow("Reference", it) }
                                    KeyValueRow("Stay Dates", DateFormatter.formatDateRange(entry.checkInDate, entry.checkOutDate))
                                    KeyValueRow("Submitted", DateFormatter.formatRelativeTimestamp(entry.createdAt))
                                    if (entry.receiptUploaded) {
                                        KeyValueRow("Receipt", "Proof uploaded", valueColor = colors.success.fg)
                                    }

                                    if (canApprove && entry.status.lowercase() == "pending") {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (entry.bookingStatus.lowercase() in setOf("pending", "pending_payment", "pending_confirmation")) {
                                                HotelOutlinedButton(
                                                    text = "Reject",
                                                    isDestructive = true,
                                                    enabled = !state.isMutating,
                                                    onClick = { rejectTarget = entry },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            HotelPrimaryButton(
                                                text = "Approve",
                                                icon = Icons.Outlined.CheckCircleOutline,
                                                enabled = !state.isMutating,
                                                containerColor = colors.success.fg,
                                                contentColor = colors.primaryContrastText,
                                                onClick = { approveTarget = entry },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.items.size < state.total) {
                                item(key = "pending_load_more") {
                                    if (state.isLoadingMore) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        }
                                    } else {
                                        HotelOutlinedButton(
                                            text = "Load More (${state.items.size}/${state.total})",
                                            onClick = viewModel::loadMore,
                                            modifier = Modifier.fillMaxWidth(),
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

    approveTarget?.let { item ->
        ConfirmActionDialog(
            title = "Approve Payment",
            message = "Approve ${MoneyFormatter.format(item.amount, currency)} for ${item.guestName ?: item.bookingNumber ?: "booking"}?",
            confirmLabel = "Approve",
            onConfirm = {
                approveTarget = null
                viewModel.approve(item.id)
            },
            onDismiss = { approveTarget = null },
        )
    }

    rejectTarget?.let { item ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceOverlay,
            title = { Text("Reject Payment Claim", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                HotelPrimaryButton(
                    text = "Reject Payment",
                    enabled = reason.isNotBlank(),
                    containerColor = colors.danger.fg,
                    contentColor = colors.primaryContrastText,
                    onClick = {
                        rejectTarget = null
                        viewModel.reject(item.id, reason)
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }
}
