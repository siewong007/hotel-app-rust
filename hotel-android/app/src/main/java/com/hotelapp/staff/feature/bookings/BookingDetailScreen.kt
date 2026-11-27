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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.ScaffoldDefaults
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
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
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
    val canDelete = session.hasPermission("bookings:delete")
    val canReadPayments = session.hasPermission("payments:read")
    val canCreatePayments = session.hasPermission("payments:create")
    val canRefundPayments = session.hasPermission("payments:refund")
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isEmbeddedPane = onBack == null

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCheckInSheet by remember { mutableStateOf(false) }
    var showCheckOutConfirm by remember { mutableStateOf(false) }
    var showReactivateConfirm by remember { mutableStateOf(false) }
    var showRecordPaymentSheet by remember { mutableStateOf(false) }
    var showReasonDialog by remember { mutableStateOf<String?>(null) }
    var depositDialogMode by remember { mutableStateOf<String?>(null) }

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

    OnResumeRefreshEffect { viewModel.refresh(silent = true) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.surfaceApp,
        contentWindowInsets = if (isEmbeddedPane) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { HotelSnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = if (isEmbeddedPane) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
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
                                        val billable = b.billableTotal(state.paymentSummary)
                                        appendLine("Total: ${MoneyFormatter.format(billable, currency)}")
                                        val bal = state.paymentSummary?.balanceDue ?: b.balanceDue ?: BigDecimal.ZERO
                                        append("Balance Due: ${MoneyFormatter.format(bal, currency)}")
                                    }
                                    if (!sharePlainText(context, "Booking ${b.bookingNumber}", summaryText)) {
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar("No compatible app found to share summary.")
                                        }
                                    }
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
            if (booking != null && (canUpdate || canDelete)) {
                val paidTotal = state.paymentSummary?.totalPaid ?: booking.totalPaid ?: BigDecimal.ZERO
                BookingActionBottomBar(
                    status = booking.status,
                    totalPaid = paidTotal,
                    canUpdate = canUpdate,
                    canDelete = canDelete,
                    isBusy = state.isSubmittingAction,
                    applyNavigationBarsPadding = !isEmbeddedPane,
                    onCheckIn = {
                        viewModel.clearFeedback()
                        showCheckInSheet = true
                    },
                    onCheckOut = {
                        viewModel.clearFeedback()
                        showCheckOutConfirm = true
                    },
                    onRelease = {
                        viewModel.clearFeedback()
                        showReasonDialog = "release"
                    },
                    onVoid = {
                        viewModel.clearFeedback()
                        showReasonDialog = "void"
                    },
                    onReactivate = {
                        viewModel.clearFeedback()
                        showReactivateConfirm = true
                    },
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
                        if (!state.refreshError.isNullOrBlank()) {
                            item {
                                InlineAlertBanner(
                                    message = state.refreshError!!,
                                    tone = colors.warning,
                                    onDismiss = { viewModel.clearRefreshError() },
                                )
                            }
                        }
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
                            val totalPaid = state.paymentSummary?.totalPaid ?: b.totalPaid ?: BigDecimal.ZERO
                            val billableTotal = b.billableTotal(state.paymentSummary)
                            val balance = state.paymentSummary?.balanceDue ?: b.balanceDue ?: BigDecimal.ZERO
                            val paidRatio = if (billableTotal > BigDecimal.ZERO) {
                                (totalPaid.toFloat() / billableTotal.toFloat()).coerceIn(0f, 1f)
                            } else {
                                if (balance <= BigDecimal.ZERO) 1f else 0f
                            }
                            val summary = state.paymentSummary
                            val depCollected = summary?.depositCollected
                                ?: if (b.depositPaid == true) (b.depositAmount ?: BigDecimal.ZERO) else BigDecimal.ZERO
                            val depRefunded = summary?.depositRefunded ?: BigDecimal.ZERO
                            val depForfeited = summary?.depositForfeited ?: BigDecimal.ZERO
                            val heldDeposit = if (summary != null) {
                                (summary.depositCollected - summary.depositRefunded - summary.depositForfeited)
                                    .coerceAtLeast(BigDecimal.ZERO)
                            } else if (b.depositPaid == true && b.depositRefunded != true) {
                                (b.depositAmount ?: BigDecimal.ZERO).coerceAtLeast(BigDecimal.ZERO)
                            } else {
                                BigDecimal.ZERO
                            }
                            val activeDeposit = (b.depositAmount ?: BigDecimal.ZERO)
                                .takeIf { it > BigDecimal.ZERO }
                                ?: depCollected.takeIf { it > BigDecimal.ZERO }
                            val canResolveDeposit = canRefundPayments && heldDeposit > BigDecimal.ZERO

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
                                KeyValueRow("Room Charges", MoneyFormatter.format(b.totalAmount, currency))
                                b.tourismTaxAmount?.takeIf { it > BigDecimal.ZERO }?.let {
                                    KeyValueRow("Tourism Tax", MoneyFormatter.format(it, currency))
                                }
                                b.extraBedCount?.takeIf { it > 0 }?.let { beds ->
                                    val bedFee = b.extraBedCharge?.takeIf { it > BigDecimal.ZERO }
                                    val valueText = if (bedFee != null) {
                                        "$beds (${MoneyFormatter.format(bedFee, currency)})"
                                    } else {
                                        beds.toString()
                                    }
                                    KeyValueRow("Extra Beds", valueText)
                                }
                                if (billableTotal.compareTo(b.totalAmount) != 0) {
                                    KeyValueRow("Billable Total", MoneyFormatter.format(billableTotal, currency))
                                }
                                KeyValueRow("Total Paid", MoneyFormatter.format(totalPaid, currency), valueColor = colors.success.fg)
                                b.totalRefunded?.takeIf { it > BigDecimal.ZERO }?.let {
                                    KeyValueRow("Total Refunded", MoneyFormatter.format(it, currency))
                                }
                                activeDeposit?.let { depAmt ->
                                    val depLabel = when {
                                        heldDeposit > BigDecimal.ZERO && (depRefunded > BigDecimal.ZERO || depForfeited > BigDecimal.ZERO) -> "Held"
                                        heldDeposit > BigDecimal.ZERO -> "Paid"
                                        depForfeited > BigDecimal.ZERO && depRefunded <= BigDecimal.ZERO -> "Forfeited"
                                        b.depositRefunded == true || depRefunded > BigDecimal.ZERO -> "Refunded"
                                        else -> "Held / Unpaid"
                                    }
                                    val shownAmount = if (heldDeposit > BigDecimal.ZERO) heldDeposit else depAmt
                                    KeyValueRow("Deposit ($depLabel)", MoneyFormatter.format(shownAmount, currency))
                                    if (depRefunded > BigDecimal.ZERO && heldDeposit > BigDecimal.ZERO) {
                                        KeyValueRow("Deposit Refunded", MoneyFormatter.format(depRefunded, currency))
                                    }
                                    if (depForfeited > BigDecimal.ZERO && heldDeposit > BigDecimal.ZERO) {
                                        KeyValueRow("Deposit Forfeited", MoneyFormatter.format(depForfeited, currency))
                                    }
                                }
                                if (canResolveDeposit) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        HotelOutlinedButton(
                                            text = "Refund Deposit",
                                            enabled = !state.isSubmittingAction,
                                            onClick = {
                                                viewModel.clearFeedback()
                                                depositDialogMode = "refund"
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                        HotelOutlinedButton(
                                            text = "Forfeit Deposit",
                                            isDestructive = true,
                                            enabled = !state.isSubmittingAction,
                                            onClick = {
                                                viewModel.clearFeedback()
                                                depositDialogMode = "forfeit"
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
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

                        if (canReadPayments || canCreatePayments) {
                            val canPayThisBooking = canCreatePayments &&
                                b.status.lowercase() !in setOf("voided", "cancelled", "comp_void", "no_show")
                            item {
                                PaymentHistoryCard(
                                    payments = state.payments,
                                    summary = state.paymentSummary,
                                    currency = currency,
                                    canCreatePayment = canPayThisBooking,
                                    canReadPayment = canReadPayments,
                                    isLoadingInvoice = state.isLoadingInvoice,
                                    isGroupBooking = !b.groupNumber.isNullOrBlank(),
                                    onRecordPayment = {
                                        viewModel.clearFeedback()
                                        showRecordPaymentSheet = true
                                    },
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
            effectiveBalanceDue = state.paymentSummary?.balanceDue ?: state.booking!!.balanceDue,
            currency = currency,
            isSubmitting = state.isSubmittingAction,
            canCollectPayment = canCreatePayments,
            errorMessage = state.actionError,
            onDismiss = {
                viewModel.clearFeedback()
                showCheckInSheet = false
            },
            onSubmit = { first, last, phone, icNumber, amt, method ->
                viewModel.checkIn(first, last, phone, icNumber, amt, method) {
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
            isSubmitting = state.isSubmittingAction,
            errorMessage = state.actionError,
            onConfirm = {
                viewModel.checkOut {
                    showCheckOutConfirm = false
                }
            },
            onDismiss = {
                viewModel.clearFeedback()
                showCheckOutConfirm = false
            },
        )
    }

    if (showReactivateConfirm) {
        val isGroup = !state.booking?.groupNumber.isNullOrBlank()
        ConfirmActionDialog(
            title = if (isGroup) "Reactivate Group Booking" else "Reactivate Booking",
            message = if (isGroup) {
                "Reactivate all rooms in group ${state.booking?.groupNumber} and restore their reservations?"
            } else {
                "Reactivate voided booking ${state.booking?.bookingNumber} for Room ${state.booking?.roomNumber}?"
            },
            confirmLabel = "Reactivate",
            isSubmitting = state.isSubmittingAction,
            errorMessage = state.actionError,
            onConfirm = {
                viewModel.reactivateBooking {
                    showReactivateConfirm = false
                }
            },
            onDismiss = {
                viewModel.clearFeedback()
                showReactivateConfirm = false
            },
        )
    }

    showReasonDialog?.let { mode ->
        BookingReasonDialog(
            title = if (mode == "release") "Release Unpaid Hold" else "Void Booking",
            requireReason = mode == "release",
            minReasonLength = if (mode == "release") 4 else 0,
            confirmLabel = if (mode == "release") "Release Room" else "Void Booking",
            isSubmitting = state.isSubmittingAction,
            errorMessage = state.actionError,
            onConfirm = { reason ->
                if (mode == "release") {
                    viewModel.releaseHold(reason) { showReasonDialog = null }
                } else {
                    viewModel.voidBooking(reason) { showReasonDialog = null }
                }
            },
            onDismiss = {
                viewModel.clearFeedback()
                showReasonDialog = null
            },
        )
    }

    if (showRecordPaymentSheet && state.booking != null) {
        val currentBooking = state.booking!!
        RecordPaymentSheet(
            booking = currentBooking,
            defaultAmount = state.paymentSummary?.balanceDue ?: currentBooking.balanceDue,
            groupDetail = state.groupDetail,
            currency = currency,
            isSubmitting = state.isSubmittingAction,
            errorMessage = state.actionError,
            onDismiss = {
                viewModel.clearFeedback()
                showRecordPaymentSheet = false
            },
            onSubmit = { amountText, method, paymentType, reference, notes, idempotencyKey ->
                viewModel.recordPayment(
                    amountText = amountText,
                    method = method,
                    reference = reference,
                    notes = notes,
                    paymentType = paymentType,
                    idempotencyKey = idempotencyKey,
                ) {
                    showRecordPaymentSheet = false
                }
            },
        )
    }

    depositDialogMode?.let { mode ->
        val summary = state.paymentSummary
        val currentBooking = state.booking
        val heldDeposit = summary?.let {
            (it.depositCollected - it.depositRefunded - it.depositForfeited).coerceAtLeast(BigDecimal.ZERO)
        } ?: currentBooking?.depositAmount?.takeIf { currentBooking.depositPaid == true && currentBooking.depositRefunded != true }
            ?: BigDecimal.ZERO
        val isOnlineOrWebsite = currentBooking?.source.equals("online", true) ||
            currentBooking?.source.equals("website", true) ||
            currentBooking?.bookingChannelType.equals("ota", true) ||
            currentBooking?.bookingChannelType.equals("website", true)
        val depositTenders = state.payments
            .filter {
                it.paymentType.equals("deposit", true) &&
                    it.voidedAt == null &&
                    !it.paymentStatus.equals("void", true) &&
                    !it.paymentStatus.equals("voided", true) &&
                    !it.paymentStatus.equals("rejected", true)
            }
            .map { it.paymentMethod.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        val allowedRefundMethods = if (isOnlineOrWebsite && depositTenders.isNotEmpty()) depositTenders else emptyList()
        DepositActionDialog(
            isForfeit = mode == "forfeit",
            heldDeposit = heldDeposit,
            currency = currency,
            isSubmitting = state.isSubmittingAction,
            errorMessage = state.actionError,
            refundMethodOptions = allowedRefundMethods,
            onConfirm = { amount, method, reasonOrNote, reference ->
                if (mode == "forfeit") {
                    viewModel.forfeitDeposit(
                        amount = amount,
                        maxHeld = heldDeposit,
                        reason = reasonOrNote,
                        notes = "",
                    ) { depositDialogMode = null }
                } else {
                    viewModel.refundDeposit(
                        amount = amount,
                        maxHeld = heldDeposit,
                        method = method,
                        note = reasonOrNote,
                        reference = reference,
                    ) { depositDialogMode = null }
                }
            },
            onDismiss = {
                viewModel.clearFeedback()
                depositDialogMode = null
            },
        )
    }

    state.invoicePreview?.let { invoice ->
        InvoicePreviewSheet(
            invoice = invoice,
            canCreateInvoice = canCreatePayments,
            isLoading = state.isLoadingInvoice,
            errorMessage = state.invoiceError,
            onDismiss = { viewModel.dismissInvoicePreview() },
            onGenerateOfficial = { viewModel.generateOfficialInvoice() },
            onPrint = {
                InvoicePdfPrinter.printInvoice(context, config.hotelName, invoice)
                    .onFailure { err ->
                        viewModel.reportInvoiceError(err.message ?: "Unable to print invoice.")
                    }
            },
            onSharePdf = {
                scope.launch {
                    InvoicePdfPrinter.shareInvoicePdf(context, config.hotelName, invoice)
                        .onFailure { err ->
                            viewModel.reportInvoiceError(err.message ?: "Unable to share invoice PDF.")
                        }
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
    totalPaid: BigDecimal,
    canUpdate: Boolean,
    canDelete: Boolean,
    isBusy: Boolean,
    applyNavigationBarsPadding: Boolean = true,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onRelease: () -> Unit,
    onVoid: () -> Unit,
    onReactivate: () -> Unit,
) {
    val colors = HotelTheme.colors
    val normalized = status.lowercase()
    val canCheckIn = canUpdate && normalized in setOf("confirmed", "partial_complimentary", "fully_complimentary")
    val canCheckOut = canUpdate && normalized in setOf("checked_in", "auto_checked_in", "late_checkout")
    val canRelease = canUpdate && normalized == "pending_payment" && totalPaid <= BigDecimal.ZERO
    val canVoid = (canUpdate || canDelete) && normalized !in setOf("checked_out", "completed", "voided", "cancelled", "comp_void", "no_show")
    val canReactivate = canUpdate && normalized == "voided"

    if (!canCheckIn && !canCheckOut && !canRelease && !canVoid && !canReactivate) return

    Surface(color = colors.surfaceBase, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (applyNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canRelease) {
                    HotelOutlinedButton(
                        text = "Release Hold",
                        isDestructive = true,
                        enabled = !isBusy,
                        onClick = onRelease,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (canVoid) {
                    HotelOutlinedButton(
                        text = "Void",
                        isDestructive = true,
                        enabled = !isBusy,
                        onClick = onVoid,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (canReactivate) {
                    HotelPrimaryButton(
                        text = "Reactivate",
                        isLoading = isBusy,
                        onClick = onReactivate,
                        modifier = Modifier.weight(1f),
                    )
                } else if (canCheckIn) {
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
