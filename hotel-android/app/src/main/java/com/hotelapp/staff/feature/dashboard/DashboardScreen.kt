package com.hotelapp.staff.feature.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.KingBed
import androidx.compose.material.icons.outlined.NightlightRound
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.DashboardSkeleton
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.HotelAvatar
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.KpiCard
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.SectionHeader
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.rememberReducedMotion
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.BookingBoardSummaryDto
import com.hotelapp.staff.data.dto.GuestRelationsOverviewDto
import com.hotelapp.staff.data.dto.HotelOccupancySummaryDto
import com.hotelapp.staff.data.dto.NewReservationsResponseDto
import com.hotelapp.staff.data.dto.NightAuditPreviewDto
import com.hotelapp.staff.data.repository.BookingsRepository
import com.hotelapp.staff.data.repository.EkycRepository
import com.hotelapp.staff.data.repository.OperationsRepository
import com.hotelapp.staff.data.repository.PaymentsRepository
import com.hotelapp.staff.data.repository.RoomsRepository
import com.hotelapp.staff.widget.OpsWidgetSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val bookingSummary: BookingBoardSummaryDto? = null,
    val occupancySummary: HotelOccupancySummaryDto? = null,
    val newReservations: NewReservationsResponseDto? = null,
    val nightAuditPreview: NightAuditPreviewDto? = null,
    val guestRelationsOverview: GuestRelationsOverviewDto? = null,
    val pendingPaymentsCount: Long? = null,
    val pendingEkycCount: Long? = null,
    val error: String? = null,
)

class DashboardViewModel(
    private val session: StaffSession,
    private val bookingsRepository: BookingsRepository,
    private val roomsRepository: RoomsRepository,
    realtimeSocketClient: RealtimeSocketClient,
    private val operationsRepository: OperationsRepository? = null,
    private val paymentsRepository: PaymentsRepository? = null,
    private val ekycRepository: EkycRepository? = null,
    private val hotelNameProvider: () -> String = { "Hotel Staff" },
    private val onWidgetSnapshotReady: (suspend (OpsWidgetSnapshot) -> Unit)? = null,
) : ViewModel() {

    private val canReadBookings = session.hasPermission("bookings:read")
    private val canReadRooms = session.hasPermission("rooms:read")
    private val canReadNightAudit = session.hasPermission("night_audit:read")
    private val canReadGuests = session.hasPermission("guests:read")
    private val canReadPendingPayments = session.hasPermission("payments:read")
    private val canReadEkyc = session.hasPermission("ekyc:read")

    private val hasAnyDashboardFeed =
        canReadBookings || canReadRooms || canReadNightAudit || canReadGuests || canReadPendingPayments || canReadEkyc

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = hasAnyDashboardFeed))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            realtimeSocketClient.domainEvents.collect { domain ->
                if (domain in setOf("bookings", "rooms", "payments", "housekeeping", "ekyc")) {
                    refresh(silent = true)
                }
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (!hasAnyDashboardFeed) {
            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            return
        }
        viewModelScope.launch {
            val existing = _uiState.value
            val hasLoadedData = existing.bookingSummary != null ||
                existing.occupancySummary != null ||
                existing.nightAuditPreview != null ||
                existing.guestRelationsOverview != null ||
                existing.pendingPaymentsCount != null ||
                existing.pendingEkycCount != null
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = !hasLoadedData,
                    isRefreshing = hasLoadedData,
                    error = null,
                )
            }
            val bookingSummaryDef = if (canReadBookings) async { bookingsRepository.summary() } else null
            val newResDef = if (canReadBookings) async { bookingsRepository.newReservations() } else null
            val occDef = if (canReadRooms) async { roomsRepository.occupancySummary() } else null
            val nightAuditDef = if (canReadNightAudit && operationsRepository != null) async { operationsRepository.nightAuditPreview() } else null
            val guestRelDef = if (canReadGuests && operationsRepository != null) async { operationsRepository.guestRelationsOverview() } else null
            val pendingPayDef = if (canReadPendingPayments && paymentsRepository != null) async { paymentsRepository.pendingPayments(page = 1, perPage = 1) } else null
            val ekycDashDef = if (canReadEkyc && ekycRepository != null) async { ekycRepository.dashboard() } else null

            val bookingRes = bookingSummaryDef?.await()
            val newRes = newResDef?.await()
            val occRes = occDef?.await()
            val nightAuditRes = nightAuditDef?.await()
            val guestRelRes = guestRelDef?.await()
            val pendingPayRes = pendingPayDef?.await()
            val ekycDashRes = ekycDashDef?.await()

            val firstError = listOfNotNull(
                bookingRes?.exceptionOrNull(),
                occRes?.exceptionOrNull(),
                pendingPayRes?.exceptionOrNull(),
                ekycDashRes?.exceptionOrNull(),
                nightAuditRes?.exceptionOrNull(),
                guestRelRes?.exceptionOrNull(),
            ).firstOrNull()?.message

            _uiState.update { current ->
                val updatedBookings = bookingRes?.getOrNull() ?: current.bookingSummary
                val updatedOcc = occRes?.getOrNull() ?: current.occupancySummary
                val updatedNightAudit = nightAuditRes?.getOrNull() ?: current.nightAuditPreview
                val updatedGuestRel = guestRelRes?.getOrNull() ?: current.guestRelationsOverview
                val updatedPayCount = pendingPayRes?.getOrNull()?.total ?: current.pendingPaymentsCount
                val updatedEkycCount = ekycDashRes?.getOrNull()?.let { it.pendingReview + it.underManualReview } ?: current.pendingEkycCount
                val anyDataNow = updatedBookings != null ||
                    updatedOcc != null ||
                    updatedNightAudit != null ||
                    updatedGuestRel != null ||
                    updatedPayCount != null ||
                    updatedEkycCount != null

                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    bookingSummary = updatedBookings,
                    newReservations = newRes?.getOrNull() ?: current.newReservations,
                    occupancySummary = updatedOcc,
                    nightAuditPreview = updatedNightAudit,
                    guestRelationsOverview = updatedGuestRel,
                    pendingPaymentsCount = updatedPayCount,
                    pendingEkycCount = updatedEkycCount,
                    error = if (!anyDataNow) firstError else null,
                )
            }

            val updatedState = _uiState.value
            if (onWidgetSnapshotReady != null && (updatedState.occupancySummary != null || updatedState.bookingSummary != null)) {
                val occ = updatedState.occupancySummary
                val book = updatedState.bookingSummary
                val na = updatedState.nightAuditPreview
                val rateText = occ?.occupancyRate?.let { "${it.stripTrailingZeros().toPlainString()}%" } ?: "0%"
                onWidgetSnapshotReady.invoke(
                    OpsWidgetSnapshot(
                        hotelName = hotelNameProvider(),
                        occupancyPercent = rateText,
                        occupiedRooms = occ?.occupiedRooms ?: 0L,
                        totalRooms = occ?.totalRooms ?: 0L,
                        arrivingToday = book?.arriving ?: 0L,
                        dirtyRooms = na?.roomSnapshot?.dirty ?: 0,
                        openMaintenance = na?.roomSnapshot?.maintenance ?: 0,
                        updatedAtMs = System.currentTimeMillis(),
                        isAuthenticated = true,
                    ),
                )
            }
        }
    }
}

private data class QuickActionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun DashboardScreen(
    session: StaffSession,
    viewModel: DashboardViewModel,
    serverConfigStore: ServerConfigStore,
    onOpenBookingsView: (String) -> Unit,
    onOpenBookingDetail: (Long) -> Unit,
    onOpenRooms: () -> Unit,
    onOpenHousekeeping: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenGuests: () -> Unit,
    onOpenPendingPayments: () -> Unit = {},
    onOpenEkycQueue: () -> Unit = {},
) {
    val colors = HotelTheme.colors
    val reducedMotion = rememberReducedMotion()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val currency = config.currency

    val canReadBookings = session.hasPermission("bookings:read")
    val canReadRooms = session.hasPermission("rooms:read")
    val canReadHousekeeping = session.hasPermission("housekeeping:read")
    val canReadMaintenance = session.hasPermission("maintenance:read")
    val canReadGuests = session.hasPermission("guests:read")

    val displayName = session.user.fullName?.takeIf { it.isNotBlank() } ?: session.user.username
    val firstName = remember(displayName) {
        displayName.trim().substringBefore(' ').ifBlank { displayName }
    }
    val greeting = remember { DateFormatter.greetingForHour(LocalTime.now().hour) }
    val todayLabel = remember { DateFormatter.formatTodayLabel() }

    val quickActions = remember(
        canReadRooms,
        canReadHousekeeping,
        canReadMaintenance,
        canReadGuests,
    ) {
        buildList {
            if (canReadRooms) add(QuickActionItem("Room Grid", Icons.Outlined.KingBed, onOpenRooms))
            if (canReadHousekeeping) add(QuickActionItem("Housekeeping", Icons.Outlined.CleaningServices, onOpenHousekeeping))
            if (canReadMaintenance) add(QuickActionItem("Maintenance", Icons.Outlined.Build, onOpenMaintenance))
            if (canReadGuests) add(QuickActionItem("Guests", Icons.Outlined.Group, onOpenGuests))
        }
    }

    OnResumeRefreshEffect { viewModel.refresh(silent = true) }

    when {
        state.isLoading -> DashboardSkeleton()
        state.error != null -> ErrorStateView(
            message = state.error!!,
            onRetry = { viewModel.refresh() },
        )
        else -> HotelPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$greeting, $firstName",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = todayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        HotelAvatar(name = displayName, size = 44.dp)
                    }
                }

                if (session.twoFactorEnrollmentRequired) {
                    item {
                        val deadline = session.twoFactorEnrollmentDeadline?.let {
                            " by ${DateFormatter.formatDate(it)}"
                        }.orEmpty()
                        InlineAlertBanner(
                            message = "Two-factor authentication enrollment is required for your role$deadline.",
                            tone = colors.warning,
                        )
                    }
                }

                if (!canReadBookings && !canReadRooms) {
                    val hasAnyOpsWorkspace = canReadHousekeeping ||
                        canReadMaintenance ||
                        canReadGuests ||
                        session.hasPermission("payments:read") ||
                        session.hasPermission("ekyc:read")
                    item {
                        HotelCard {
                            Text(
                                text = "Your Operational Workspace",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (hasAnyOpsWorkspace) {
                                    "Jump straight into your assigned operational boards."
                                } else {
                                    "Your role uses the hotel web portal for back-office reporting and administration. Use the More tab for sessions and app preferences."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            if (canReadHousekeeping) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HotelPrimaryButton(
                                    text = "Open Housekeeping Board",
                                    icon = Icons.Outlined.CleaningServices,
                                    onClick = onOpenHousekeeping,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else if (session.hasPermission("ekyc:read")) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HotelPrimaryButton(
                                    text = "Open eKYC Review Queue",
                                    icon = Icons.Outlined.Badge,
                                    onClick = onOpenEkycQueue,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else if (session.hasPermission("payments:read")) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HotelPrimaryButton(
                                    text = "Open Payment Approvals",
                                    icon = Icons.Outlined.Payments,
                                    onClick = onOpenPendingPayments,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                state.occupancySummary?.let { occ ->
                    item {
                        HotelCard(
                            onClick = if (canReadRooms) onOpenRooms else null,
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column {
                                    Text(
                                        text = "Property Occupancy",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val rateText = occ.occupancyRate?.let { "${it.stripTrailingZeros().toPlainString()}%" } ?: "0%"
                                    Text(
                                        text = rateText,
                                        style = MaterialTheme.typography.displaySmall,
                                        color = colors.primaryOnSurface,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    StatusPill(
                                        label = "${occ.occupiedRooms}/${occ.totalRooms} Rooms",
                                        tone = colors.info,
                                    )
                                    if (canReadRooms) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            val targetFraction = if (occ.totalRooms > 0) {
                                (occ.occupiedRooms.toFloat() / occ.totalRooms.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val animatedFraction by animateFloatAsState(
                                targetValue = targetFraction,
                                animationSpec = tween(durationMillis = if (reducedMotion) 0 else 600, easing = FastOutSlowInEasing),
                                label = "occupancy_bar",
                            )
                            LinearProgressIndicator(
                                progress = { animatedFraction },
                                color = colors.primaryMain,
                                trackColor = colors.surfaceSunken,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${occ.availableRooms} available",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.success.fg,
                                )
                                Text(
                                    text = "${occ.totalGuests} guests (${occ.totalAdults}A · ${occ.totalChildren}C)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }

                state.bookingSummary?.let { summary ->
                    item {
                        SectionHeader(
                            title = "Today's Operations",
                            actionLabel = "All Bookings",
                            onActionClick = { onOpenBookingsView("all") },
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                KpiCard(
                                    title = "Arriving Today",
                                    value = summary.arriving.toString(),
                                    subtitle = "${summary.readyToCheckIn} ready to check in",
                                    tone = colors.info,
                                    icon = Icons.AutoMirrored.Outlined.Login,
                                    onClick = { onOpenBookingsView("arriving") },
                                    modifier = Modifier.weight(1f),
                                )
                                KpiCard(
                                    title = "In-House",
                                    value = summary.inHouse.toString(),
                                    subtitle = "${summary.guestsInHouse} guests",
                                    tone = colors.success,
                                    icon = Icons.Outlined.Hotel,
                                    onClick = { onOpenBookingsView("in_house") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                KpiCard(
                                    title = "Departing Today",
                                    value = summary.departing.toString(),
                                    subtitle = "Scheduled check-outs",
                                    tone = colors.warning,
                                    icon = Icons.AutoMirrored.Outlined.Logout,
                                    onClick = { onOpenBookingsView("departing") },
                                    modifier = Modifier.weight(1f),
                                )
                                KpiCard(
                                    title = "Upcoming",
                                    value = summary.upcoming.toString(),
                                    subtitle = "Future reservations",
                                    tone = colors.violet,
                                    icon = Icons.Outlined.Event,
                                    onClick = { onOpenBookingsView("upcoming") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            val dueCount = summary.normalDue + summary.companyDue
                            val dueTotal = summary.normalDueAmount.add(summary.companyDueAmount)
                            val balanceTone = if (dueCount > 0) colors.danger else colors.neutral
                            HotelCard(
                                onClick = { onOpenBookingsView("balance") },
                                accentColor = if (dueCount > 0) colors.danger.fg else null,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(balanceTone.bg)
                                            .border(1.dp, balanceTone.border, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = balanceTone.fg,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Outstanding Balance",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.textSecondary,
                                        )
                                        Text(
                                            text = MoneyFormatter.format(dueTotal, currency),
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = if (dueCount > 0) colors.danger.fg else colors.textPrimary,
                                        )
                                        Text(
                                            text = "$dueCount stays (${summary.normalDue} guest · ${summary.companyDue} company)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textMuted,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                    )
                                }
                            }
                        }
                    }
                }

                if (quickActions.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Quick Access")
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            quickActions.forEach { action ->
                                val tileShape = RoundedCornerShape(14.dp)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(tileShape)
                                        .background(colors.surfaceBase)
                                        .border(1.dp, colors.borderSubtle, tileShape)
                                        .clickable(onClick = action.onClick)
                                        .padding(vertical = 12.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.primarySubtle),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = action.icon,
                                            contentDescription = null,
                                            tint = colors.primaryOnSurface,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Text(
                                        text = action.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                val showPendingPayments = session.hasPermission("payments:read")
                val showEkyc = session.hasPermission("ekyc:read")
                if (showPendingPayments || showEkyc) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (showPendingPayments) {
                                val count = state.pendingPaymentsCount ?: 0L
                                KpiCard(
                                    title = "Pending Payments",
                                    value = count.toString(),
                                    subtitle = "Awaiting staff review",
                                    tone = if (count > 0) colors.warning else colors.neutral,
                                    icon = Icons.Outlined.Payments,
                                    onClick = onOpenPendingPayments,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (showEkyc) {
                                val count = state.pendingEkycCount ?: 0L
                                KpiCard(
                                    title = "eKYC Queue",
                                    value = count.toString(),
                                    subtitle = "Identity checks to review",
                                    tone = if (count > 0) colors.info else colors.neutral,
                                    icon = Icons.Outlined.Badge,
                                    onClick = onOpenEkycQueue,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                state.guestRelationsOverview?.let { gr ->
                    item {
                        HotelCard(onClick = onOpenGuests) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.SupportAgent, contentDescription = null, tint = colors.primaryOnSurface, modifier = Modifier.size(18.dp))
                                    Text("Guest Relations", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                }
                                StatusPill("${gr.vipArrivals.count} VIP today", if (gr.vipArrivals.count > 0) colors.warning else colors.neutral)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val parts = buildList {
                                add("${gr.followUps.count} open follow-up(s)")
                                gr.support?.let { add("${it.waitingForStaff} support waiting") }
                            }.joinToString(" · ")
                            Text(parts, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }

                state.nightAuditPreview?.let { na ->
                    item {
                        val statusLabel = when {
                            na.alreadyRun -> "Completed"
                            na.canRun -> "Ready to Run"
                            else -> "Blocked"
                        }
                        val statusTone = when {
                            na.alreadyRun -> colors.success
                            na.canRun -> colors.info
                            else -> colors.warning
                        }
                        HotelCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.NightlightRound, contentDescription = null, tint = colors.primaryOnSurface, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("Night Audit · ${DateFormatter.formatDate(na.auditDate)}", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                        Text("${na.totalUnposted} unposted · Est. ${MoneyFormatter.format(na.estimatedRevenue, currency)}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                }
                                StatusPill(statusLabel, statusTone)
                            }
                        }
                    }
                }

                state.newReservations?.let { strip ->
                    item {
                        SectionHeader(
                            title = "New Reservations (${strip.reservations.size})",
                        )
                    }
                    if (strip.reservations.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "No new arrivals queued",
                                subtitle = "Arrivals visible from ${strip.visibleFrom} hotel time will appear here.",
                                icon = Icons.Outlined.Hotel,
                            )
                        }
                    } else {
                        items(strip.reservations, key = { it.id }) { item ->
                            val tone = StatusFormatter.bookingTone(item.status, colors)
                            HotelCard(
                                onClick = { onOpenBookingDetail(item.id) },
                                accentColor = tone.fg,
                                modifier = Modifier.animateItem(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = "Room ${item.roomNumber}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.primaryOnSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = item.bookingNumber ?: "#${item.id}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textMuted,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = item.guestName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = DateFormatter.formatDateRangeWithNights(item.checkInDate, item.checkOutDate),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusPill(
                                        label = StatusFormatter.humanize(item.status),
                                        tone = tone,
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
