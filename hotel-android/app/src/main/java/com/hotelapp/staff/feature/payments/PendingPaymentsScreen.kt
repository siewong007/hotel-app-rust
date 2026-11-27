package com.hotelapp.staff.feature.payments

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import com.hotelapp.staff.core.design.InlineAlertBanner
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val currency = config.currency
    val canApprove = session.hasPermission("payments:approve")
    val canReadReceipts = session.hasPermission("payments:read")
    val canReadBookings = session.hasPermission("bookings:read")
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    var approveTarget by remember { mutableStateOf<PendingPaymentEntryDto?>(null) }
    var rejectTarget by remember { mutableStateOf<PendingPaymentEntryDto?>(null) }
    var requestReceiptTarget by remember { mutableStateOf<PendingPaymentEntryDto?>(null) }

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
                    title = {
                        Text(
                            text = if (state.isLoading) "Payment Approvals" else "Payment Approvals (${state.total})",
                            color = colors.textPrimary,
                        )
                    },
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyStateView(
                                title = "No pending payments",
                                subtitle = "All bank transfers and payment claims have been reviewed.",
                                icon = Icons.Outlined.Payments,
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (state.error != null) {
                                item(key = "pending_error_banner") {
                                    InlineAlertBanner(message = state.error!!, tone = colors.danger)
                                }
                            }
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
                                    when {
                                        entry.receiptFileAvailable -> {
                                            KeyValueRow("Receipt", "Proof uploaded", valueColor = colors.success.fg)
                                            if (canReadReceipts) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                HotelOutlinedButton(
                                                    text = "View Receipt",
                                                    icon = Icons.Outlined.ReceiptLong,
                                                    onClick = { viewModel.openReceipt(entry.id) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                        entry.receiptUploaded -> {
                                            KeyValueRow("Receipt", "Uploaded (file unavailable)", valueColor = colors.warning.fg)
                                        }
                                        entry.receiptRequested -> {
                                            KeyValueRow("Receipt", "Requested — awaiting guest upload", valueColor = colors.warning.fg)
                                        }
                                    }

                                    if (canApprove && entry.status.lowercase() == "pending") {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        val isPaypal = entry.paymentMethod.equals("paypal", ignoreCase = true)
                                        val isBankTransfer = entry.paymentMethod.equals("bank_transfer", ignoreCase = true)
                                        if (isPaypal) {
                                            InlineAlertBanner(
                                                message = "PayPal — awaiting gateway capture (cannot be approved manually).",
                                                tone = colors.info,
                                            )
                                        }
                                        if (isBankTransfer && !entry.receiptFileAvailable) {
                                            HotelOutlinedButton(
                                                text = if (entry.receiptRequested) "Re-request Receipt" else "Request Receipt",
                                                icon = Icons.Outlined.ReceiptLong,
                                                enabled = !state.isMutating,
                                                onClick = {
                                                    viewModel.clearActionError()
                                                    requestReceiptTarget = entry
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        val showReject = entry.bookingStatus.lowercase() in setOf("pending", "pending_payment", "pending_confirmation")
                                        if (showReject || !isPaypal) {
                                            if (isPaypal) Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                if (showReject) {
                                                    HotelOutlinedButton(
                                                        text = "Reject",
                                                        isDestructive = true,
                                                        enabled = !state.isMutating,
                                                        onClick = {
                                                            viewModel.clearActionError()
                                                            rejectTarget = entry
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                                if (!isPaypal) {
                                                    HotelPrimaryButton(
                                                        text = "Approve",
                                                        icon = Icons.Outlined.CheckCircleOutline,
                                                        enabled = !state.isMutating,
                                                        containerColor = colors.success.fg,
                                                        contentColor = colors.primaryContrastText,
                                                        onClick = {
                                                            viewModel.clearActionError()
                                                            approveTarget = entry
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (state.hasMore && state.items.size < state.total) {
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

    if (state.receiptPaymentId != null) {
        var pdfLaunchError by remember(state.receiptPaymentId) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = viewModel::dismissReceipt,
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceOverlay,
            title = { Text("Payment Receipt #${state.receiptPaymentId}", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        state.isLoadingReceipt -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = colors.primaryMain)
                        }
                        state.receiptError != null -> InlineAlertBanner(
                            message = state.receiptError!!,
                            tone = colors.danger,
                        )
                        state.receiptFile != null -> {
                            val file = state.receiptFile!!
                            val mime = file.contentType.substringBefore(';').trim().lowercase()
                            if (mime.startsWith("image/")) {
                                val bitmap = remember(file) {
                                    BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size)?.asImageBitmap()
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Uploaded payment receipt",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 380.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    InlineAlertBanner(
                                        message = "Unable to decode receipt image.",
                                        tone = colors.danger,
                                    )
                                }
                            } else if (mime == "application/pdf") {
                                Text(
                                    text = "PDF receipt ready (${file.bytes.size / 1024} KB).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                                if (pdfLaunchError != null) {
                                    InlineAlertBanner(message = pdfLaunchError!!, tone = colors.danger)
                                }
                                HotelPrimaryButton(
                                    text = "Open PDF Receipt",
                                    icon = Icons.Outlined.ReceiptLong,
                                    onClick = {
                                        pdfLaunchError = null
                                        val payId = state.receiptPaymentId ?: return@HotelPrimaryButton
                                        scope.launch {
                                            runCatching {
                                                val uri = withContext(Dispatchers.IO) {
                                                    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
                                                    val staleBeforeMs = System.currentTimeMillis() - 10 * 60 * 1000L
                                                    dir.listFiles()?.forEach { child ->
                                                        if (child.lastModified() < staleBeforeMs) {
                                                            child.delete()
                                                        }
                                                    }
                                                    val outFile = File(dir, "receipt_${payId}_${UUID.randomUUID()}.pdf")
                                                    outFile.writeBytes(file.bytes)
                                                    outFile.setReadable(false, false)
                                                    outFile.setReadable(true, true)
                                                    FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        outFile,
                                                    )
                                                }
                                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                val chooser = Intent.createChooser(viewIntent, "Open Payment Receipt")
                                                context.startActivity(chooser)
                                            }.onFailure { err ->
                                                pdfLaunchError = err.message ?: "No PDF viewer app available on device."
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                InlineAlertBanner(
                                    message = "Unsupported receipt format ($mime).",
                                    tone = colors.warning,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissReceipt) {
                    Text("Close", color = colors.primaryOnSurface)
                }
            },
        )
    }

    approveTarget?.let { item ->
        ConfirmActionDialog(
            title = "Approve Payment",
            message = "Approve ${MoneyFormatter.format(item.amount, currency)} for ${item.guestName ?: item.bookingNumber ?: "booking"}?",
            confirmLabel = "Approve",
            isSubmitting = state.isMutating,
            errorMessage = state.actionError,
            onConfirm = {
                viewModel.approve(item.id) { approveTarget = null }
            },
            onDismiss = {
                viewModel.clearActionError()
                approveTarget = null
            },
        )
    }

    rejectTarget?.let { item ->
        var reason by remember(item.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                if (!state.isMutating) {
                    viewModel.clearActionError()
                    rejectTarget = null
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceOverlay,
            title = { Text("Reject Payment Claim", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.actionError.isNullOrBlank()) {
                        InlineAlertBanner(message = state.actionError!!, tone = colors.danger)
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        enabled = !state.isMutating,
                        label = { Text("Reason (required)") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = hotelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                HotelPrimaryButton(
                    text = if (state.isMutating) "Rejecting..." else "Reject Payment",
                    isLoading = state.isMutating,
                    enabled = !state.isMutating && reason.isNotBlank(),
                    containerColor = colors.danger.fg,
                    contentColor = colors.primaryContrastText,
                    onClick = {
                        viewModel.reject(item.id, reason) { rejectTarget = null }
                    },
                )
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isMutating,
                    onClick = {
                        viewModel.clearActionError()
                        rejectTarget = null
                    },
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }

    requestReceiptTarget?.let { item ->
        var message by remember(item.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                if (!state.isMutating) {
                    viewModel.clearActionError()
                    requestReceiptTarget = null
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceOverlay,
            title = { Text("Request Transfer Receipt", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Send a receipt upload request for ${item.guestName ?: item.bookingNumber ?: "booking"}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    if (!state.actionError.isNullOrBlank()) {
                        InlineAlertBanner(message = state.actionError!!, tone = colors.danger)
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        enabled = !state.isMutating,
                        label = { Text("Note to Guest (optional)") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = hotelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                HotelPrimaryButton(
                    text = if (state.isMutating) "Sending..." else "Send Request",
                    isLoading = state.isMutating,
                    enabled = !state.isMutating,
                    onClick = {
                        viewModel.requestReceipt(item.id, message) { requestReceiptTarget = null }
                    },
                )
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isMutating,
                    onClick = {
                        viewModel.clearActionError()
                        requestReceiptTarget = null
                    },
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }
}
