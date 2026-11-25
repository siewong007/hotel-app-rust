package com.hotelapp.staff.feature.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.ConfirmActionDialog
import com.hotelapp.staff.core.design.DetailScreenSkeleton
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.HotelAvatar
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSnackbarHost
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.HotelTooltipIconButton
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.KeyValueRow
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.copyToClipboard
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.design.sharePlainText
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.print.InvoicePdfPrinter
import com.hotelapp.staff.core.ui.CollectEffects
import com.hotelapp.staff.data.dto.BookingTimelineEntryDto
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    session: StaffSession,
    viewModel: BookingDetailViewModel,
    serverConfigStore: ServerConfigStore,
    onBack: (() -> Unit)?,
    onOpenGuest: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HotelTheme.colors
    val context = LocalContext.current
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val currency = config.currency
    val canUpdate = session.hasPermission("bookings:update")
    val canReadPayments = session.hasPermission("payments:read")
    val canCreatePayments = session.hasPermission("payments:create")
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCheckInSheet by remember { mutableStateOf(false) }
    var showCheckOutConfirm by remember { mutableStateOf(false) }
    var showRecordPaymentSheet by remember { mutableStateOf(false) }
    var showReasonDialog by remember { mutableStateOf<String?>(null) }

    fun copyAndNotify(label: String, value: String) {
        if (copyToClipboard(context, label, value)) {
            haptics.click()
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("Copied $label: $value")
            }
        }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is BookingDetailEffect.ShowMessage -> {
                if (effect.isError) haptics.reject() else haptics.confirm()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(effect.message)
                viewModel.clearFeedback()
            }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.surfaceApp,
        snackbarHost = { HotelSnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = state.booking?.bookingNumber ?: "Booking Details",
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.booking?.bookingNumber?.let { num ->
                                IconButton(
                                    onClick = { copyAndNotify("Booking #", num) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy booking number",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = colors.textPrimary,
                                )
                            }
                        }
                    },
                    actions = {
                        state.booking?.let { b ->
                            HotelTooltipIconButton(
                                tooltip = "Share booking summary",
                                onClick = {
                                    haptics.click()
                                    val summaryText = buildString {
                                        appendLine("${config.hotelName} · Booking ${b.bookingNumber}")
                                        appendLine("Guest: ${b.guestName}")
                                        appendLine("Room: ${b.roomNumber} (${b.roomType})")
                                        appendLine("Stay: ${DateFormatter.formatDateRangeWithNights(b.checkInDate, b.checkOutDate)}")
                                        appendLine("Status: ${StatusFormatter.humanize(b.status)}")
                                        appendLine("Total: ${MoneyFormatter.format(b.totalAmount, currency)}")
                                        val bal = b.balanceDue ?: BigDecimal.ZERO
                                        append("Balance Due: ${MoneyFormatter.format(bal, currency)}")
                                    }
                                    sharePlainText(context, "Booking ${b.bookingNumber}", summaryText)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share booking summary",
                                    tint = colors.textPrimary,
                                )
                            }
                        }
                        HotelTooltipIconButton(
                            tooltip = "Refresh",
                            onClick = { viewModel.refresh() },
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                                tint = colors.textPrimary,
                            )
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
        bottomBar = {
            val booking = state.booking
            if (booking != null && canUpdate) {
                BookingActionBottomBar(
                    status = booking.status,
                    isBusy = state.isSubmittingAction,
                    onCheckIn = { showCheckInSheet = true },
                    onCheckOut = { showCheckOutConfirm = true },
                    onRelease = { showReasonDialog = "release" },
                    onVoid = { showReasonDialog = "void" },
                )
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
            state.booking != null -> {
                val b = state.booking!!
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
                        if (state.advisory?.needsAttention == true && !state.advisory?.message.isNullOrBlank()) {
                            item {
                                InlineAlertBanner(
                                    message = "Company Advisory: ${state.advisory!!.message}",
                                    tone = colors.warning,
                                )
                            }
                        }

                        item {
                            val bookingTone = StatusFormatter.bookingTone(b.status, colors)
                            HotelCard(accentColor = bookingTone.fg) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        HotelAvatar(name = b.guestName, size = 44.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = b.guestName,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (!b.guestEmail.isNullOrBlank()) {
                                                Text(
                                                    text = b.guestEmail,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusPill(
                                        label = StatusFormatter.humanize(b.status),
                                        tone = bookingTone,
                                    )
                                }

                                if (session.hasPermission("guests:read")) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val linkShape = RoundedCornerShape(10.dp)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(linkShape)
                                            .background(colors.surfaceRaised)
                                            .border(1.dp, colors.borderSubtle, linkShape)
                                            .clickable { onOpenGuest(b.guestId) }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PersonOutline,
                                            contentDescription = null,
                                            tint = colors.primaryOnSurface,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "View Guest 360 Profile",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.primaryOnSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.primaryOnSurface,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                val nights = DateFormatter.nightsCount(b.checkInDate, b.checkOutDate)
                                val guestSummary = buildString {
                                    append("${b.adults ?: 1}A")
                                    if ((b.children ?: 0) > 0) append(" · ${b.children}C")
                                    if (nights != null) append(" · ${nights}n")
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceSunken)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    StayWellMetric("Room", b.roomNumber)
                                    StayWellMetric("Check-In", DateFormatter.formatDate(b.checkInDate))
                                    StayWellMetric("Check-Out", DateFormatter.formatDate(b.checkOutDate))
                                    StayWellMetric("Party", guestSummary)
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                KeyValueRow("Room Type", b.roomType)
                                b.bookingChannelName?.let { KeyValueRow("Channel", it) }
                                b.otaReference?.takeIf { it.isNotBlank() }?.let { KeyValueRow("OTA Reference", it) }
                                b.companyName?.takeIf { it.isNotBlank() }?.let { KeyValueRow("Company", it) }
                                b.groupNumber?.takeIf { it.isNotBlank() }?.let { KeyValueRow("Group #", it) }
                                b.smokingPreference?.let { KeyValueRow("Smoking Pref", StatusFormatter.humanize(it)) }
                                if (b.isComplimentary == true) {
                                    KeyValueRow("Rate Type", "Complimentary", valueColor = colors.info.fg)
                                }
                                b.ekycSummary?.let { ekyc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "eKYC Verification",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                        StatusPill(
                                            label = StatusFormatter.humanize(ekyc.status),
                                            tone = StatusFormatter.ekycTone(ekyc.status, colors),
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            val totalPaid = b.totalPaid ?: BigDecimal.ZERO
                            val totalAmount = b.totalAmount
                            val balance = b.balanceDue ?: BigDecimal.ZERO
                            val paidRatio = if (totalAmount > BigDecimal.ZERO) {
                                (totalPaid.toFloat() / totalAmount.toFloat()).coerceIn(0f, 1f)
                            } else {
                                if (balance <= BigDecimal.ZERO) 1f else 0f
                            }

                            HotelCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Folio & Payment",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                    )
                                    b.paymentStatus?.let { payStatus ->
                                        StatusPill(
                                            label = StatusFormatter.humanize(payStatus),
                                            tone = StatusFormatter.paymentTone(payStatus, colors),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceSunken)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Balance Due",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.textSecondary,
                                        )
                                        Text(
                                            text = MoneyFormatter.format(balance, currency),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = if (balance > BigDecimal.ZERO) colors.danger.fg else colors.success.fg,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { paidRatio },
                                        color = if (balance > BigDecimal.ZERO) colors.primaryMain else colors.success.fg,
                                        trackColor = colors.surfaceRaised,
                                        strokeCap = StrokeCap.Round,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                KeyValueRow("Nightly Rate", MoneyFormatter.format(b.roomRate, currency))
                                KeyValueRow("Total Amount", MoneyFormatter.format(totalAmount, currency))
                                b.tourismTaxAmount?.takeIf { it > BigDecimal.ZERO }?.let {
                                    KeyValueRow("Tourism Tax", MoneyFormatter.format(it, currency))
                                }
                                b.extraBedCount?.takeIf { it > 0 }?.let {
                                    KeyValueRow("Extra Beds", it.toString())
                                }
                                KeyValueRow("Total Paid", MoneyFormatter.format(totalPaid, currency), valueColor = colors.success.fg)
                                b.totalRefunded?.takeIf { it > BigDecimal.ZERO }?.let {
                                    KeyValueRow("Total Refunded", MoneyFormatter.format(it, currency))
                                }
                                b.depositAmount?.takeIf { it > BigDecimal.ZERO }?.let {
                                    val depLabel = if (b.depositPaid == true) "Paid" else "Held / Unpaid"
                                    KeyValueRow("Deposit ($depLabel)", MoneyFormatter.format(it, currency))
                                }
                                b.paymentMethod?.takeIf { it.isNotBlank() }?.let {
                                    KeyValueRow("Payment Method", StatusFormatter.humanize(it))
                                }
                                b.folioNumber?.takeIf { it.isNotBlank() }?.let { folio ->
                                    CopyableKeyValueRow("Folio #", folio) { copyAndNotify("Folio #", folio) }
                                }
                                b.invoiceNumber?.takeIf { it.isNotBlank() }?.let { inv ->
                                    CopyableKeyValueRow("Invoice #", inv) { copyAndNotify("Invoice #", inv) }
                                }
                            }
                        }

                        if (canReadPayments) {
                            item {
                                PaymentHistoryCard(
                                    payments = state.payments,
                                    summary = state.paymentSummary,
                                    currency = currency,
                                    canCreatePayment = canCreatePayments,
                                    isLoadingInvoice = state.isLoadingInvoice,
                                    onRecordPayment = { showRecordPaymentSheet = true },
                                    onOpenInvoice = { viewModel.openInvoicePreview() },
                                )
                            }
                        }

                        if (!b.specialRequests.isNullOrBlank() || !b.remarks.isNullOrBlank()) {
                            item {
                                HotelCard {
                                    Text("Notes & Requests", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (!b.specialRequests.isNullOrBlank()) {
                                        Text(
                                            text = "Special Requests",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.textSecondary,
                                        )
                                        Text(
                                            text = b.specialRequests,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textPrimary,
                                        )
                                    }
                                    if (!b.remarks.isNullOrBlank()) {
                                        if (!b.specialRequests.isNullOrBlank()) Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Staff Remarks",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.textSecondary,
                                        )
                                        Text(
                                            text = b.remarks,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }

                        if (state.timeline.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Activity Timeline (${state.timeline.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                )
                            }
                            item {
                                HotelCard {
                                    state.timeline.forEachIndexed { idx, entry ->
                                        TimelineItemRow(
                                            entry = entry,
                                            currency = currency,
                                            isLast = idx == state.timeline.lastIndex,
                                        )
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

    if (showCheckInSheet && state.booking != null) {
        CheckInModalSheet(
            booking = state.booking!!,
            currency = currency,
            isSubmitting = state.isSubmittingAction,
            canCollectPayment = canCreatePayments,
            onDismiss = { showCheckInSheet = false },
            onSubmit = { first, last, phone, amt, method ->
                viewModel.checkIn(first, last, phone, amt, method) {
                    showCheckInSheet = false
                }
            },
        )
    }

    if (showCheckOutConfirm) {
        ConfirmActionDialog(
            title = "Check Out Guest",
            message = "Complete check-out for ${state.booking?.guestName} (Room ${state.booking?.roomNumber})?",
            confirmLabel = "Check Out",
            onConfirm = {
                showCheckOutConfirm = false
                viewModel.checkOut()
            },
            onDismiss = { showCheckOutConfirm = false },
        )
    }

    showReasonDialog?.let { mode ->
        BookingReasonDialog(
            title = if (mode == "release") "Release Unpaid Hold" else "Void Booking",
            requireReason = mode == "release",
            confirmLabel = if (mode == "release") "Release Room" else "Void Booking",
            onConfirm = { reason ->
                showReasonDialog = null
                if (mode == "release") {
                    viewModel.releaseHold(reason) {}
                } else {
                    viewModel.voidBooking(reason) {}
                }
            },
            onDismiss = { showReasonDialog = null },
        )
    }

    if (showRecordPaymentSheet && state.booking != null) {
        RecordPaymentSheet(
            defaultAmount = state.paymentSummary?.balanceDue ?: state.booking?.balanceDue,
            currency = currency,
            isSubmitting = state.isSubmittingAction,
            onDismiss = { showRecordPaymentSheet = false },
            onSubmit = { amountText, method, reference, notes ->
                viewModel.recordPayment(amountText, method, reference, notes) {
                    showRecordPaymentSheet = false
                }
            },
        )
    }

    state.invoicePreview?.let { invoice ->
        InvoicePreviewSheet(
            invoice = invoice,
            canCreateInvoice = canCreatePayments,
            isLoading = state.isLoadingInvoice,
            onDismiss = { viewModel.dismissInvoicePreview() },
            onGenerateOfficial = { viewModel.generateOfficialInvoice() },
            onPrint = {
                InvoicePdfPrinter.printInvoice(context, config.hotelName, invoice)
                    .onFailure { err ->
                        scope.launch { snackbarHostState.showSnackbar(err.message ?: "Unable to print invoice.") }
                    }
            },
            onSharePdf = {
                InvoicePdfPrinter.shareInvoicePdf(context, config.hotelName, invoice)
                    .onFailure { err ->
                        scope.launch { snackbarHostState.showSnackbar(err.message ?: "Unable to share invoice PDF.") }
                    }
            },
        )
    }
}

@Composable
private fun StayWellMetric(
    label: String,
    value: String,
) {
    val colors = HotelTheme.colors
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun CopyableKeyValueRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    val colors = HotelTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onCopy)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primaryOnSurface,
            )
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy $label",
                tint = colors.primaryOnSurface,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun TimelineItemRow(
    entry: BookingTimelineEntryDto,
    currency: String,
    isLast: Boolean,
) {
    val colors = HotelTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
        ) {
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.primaryMain),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .weight(1f)
                        .padding(top = 4.dp)
                        .background(colors.borderBase),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = DateFormatter.formatRelativeTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
            if (!entry.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (!entry.statusFrom.isNullOrBlank() && !entry.statusTo.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(5.dp))
                StatusPill(
                    label = "${StatusFormatter.humanize(entry.statusFrom)} → ${StatusFormatter.humanize(entry.statusTo)}",
                    tone = colors.info,
                )
            }
            val parsedAmount = entry.amount?.toBigDecimalOrNull()
            if (parsedAmount != null) {
                Spacer(modifier = Modifier.height(5.dp))
                StatusPill(
                    label = MoneyFormatter.format(parsedAmount, currency),
                    tone = colors.success,
                )
            }
            if (!entry.actorUsername.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "By ${entry.actorUsername}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun BookingActionBottomBar(
    status: String,
    isBusy: Boolean,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onRelease: () -> Unit,
    onVoid: () -> Unit,
) {
    val colors = HotelTheme.colors
    val canCheckIn = status == "confirmed"
    val canCheckOut = status in setOf("checked_in", "auto_checked_in", "late_checkout")
    val canRelease = status == "pending_payment"
    val canVoid = status !in setOf("checked_out", "completed", "voided", "cancelled")

    if (!canCheckIn && !canCheckOut && !canVoid) return

    Surface(color = colors.surfaceBase, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canVoid) {
                    HotelOutlinedButton(
                        text = if (canRelease) "Release Hold" else "Void",
                        isDestructive = true,
                        enabled = !isBusy,
                        onClick = if (canRelease) onRelease else onVoid,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (canCheckIn) {
                    HotelPrimaryButton(
                        text = "Check In",
                        icon = Icons.AutoMirrored.Outlined.Login,
                        isLoading = isBusy,
                        onClick = onCheckIn,
                        modifier = Modifier.weight(1.4f),
                    )
                } else if (canCheckOut) {
                    HotelPrimaryButton(
                        text = "Check Out",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        isLoading = isBusy,
                        onClick = onCheckOut,
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
        }
    }
}
