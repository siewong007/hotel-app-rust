package com.hotelapp.staff.feature.notifications

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelExtendedColors
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.HotelTooltipIconButton
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.ListCardSkeleton
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.StatusTone
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.StaffNotificationItemDto
import com.hotelapp.staff.data.repository.NotificationsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationFilter {
    ACTION_NEEDED,
    ALL,
}

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val unreadCount: Long = 0,
    val urgentUnreadCount: Long = 0,
    val actionPendingCount: Long = 0,
    val filter: NotificationFilter = NotificationFilter.ACTION_NEEDED,
    val items: List<StaffNotificationItemDto> = emptyList(),
    val error: String? = null,
)

class NotificationsViewModel(
    private val repository: NotificationsRepository,
    realtimeSocketClient: RealtimeSocketClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val _incomingAlerts = MutableSharedFlow<StaffNotificationItemDto>(extraBufferCapacity = 8)
    val incomingAlerts: SharedFlow<StaffNotificationItemDto> = _incomingAlerts.asSharedFlow()

    private var knownIds: MutableSet<Long>? = null
    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            realtimeSocketClient.domainEvents.collect { domain ->
                if (domain in setOf("notifications", "support", "payments", "bookings", "ekyc", "housekeeping", "maintenance")) {
                    refresh(silent = true)
                }
            }
        }
    }

    fun setFilter(filter: NotificationFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh(silent: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
            }
            repository.list()
                .onSuccess { res ->
                    val currentKnown = knownIds
                    if (currentKnown == null) {
                        knownIds = res.items.map { it.id }.toMutableSet()
                    } else {
                        val freshAlert = res.items.firstOrNull { item ->
                            !currentKnown.contains(item.id) && item.readAt == null && item.actionPending
                        }
                        res.items.forEach { currentKnown.add(it.id) }
                        if (freshAlert != null) {
                            _incomingAlerts.tryEmit(freshAlert)
                        }
                    }

                    val derivedUrgent = if (res.urgentUnread > 0) {
                        res.urgentUnread
                    } else {
                        res.items.count {
                            it.readAt == null && it.actionPending && effectivePriority(it) == "urgent"
                        }.toLong()
                    }
                    val derivedPending = if (res.actionPendingCount > 0) {
                        res.actionPendingCount
                    } else {
                        res.items.count { it.actionPending && it.requiresAction }.toLong()
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            unreadCount = res.unread,
                            urgentUnreadCount = derivedUrgent,
                            actionPendingCount = derivedPending,
                            items = res.items,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, error = if (silent) it.error else (err.message ?: "Unable to load notifications."))
                    }
                }
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch {
            val previous = _uiState.value
            _uiState.update { cur ->
                val target = cur.items.firstOrNull { it.id == id }
                val targetWasUnread = target != null && target.readAt == null && target.actionPending
                val targetWasUrgent = targetWasUnread && effectivePriority(target) == "urgent"
                cur.copy(
                    items = cur.items.map { item -> if (item.id == id) item.copy(readAt = item.readAt ?: "read") else item },
                    unreadCount = if (targetWasUnread) (cur.unreadCount - 1).coerceAtLeast(0L) else cur.unreadCount,
                    urgentUnreadCount = if (targetWasUrgent) (cur.urgentUnreadCount - 1).coerceAtLeast(0L) else cur.urgentUnreadCount,
                    error = null,
                )
            }
            repository.markRead(id)
                .onSuccess { refresh(silent = true) }
                .onFailure { err ->
                    _uiState.value = previous.copy(
                        error = err.message ?: "Unable to mark notification as read.",
                    )
                }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            val previous = _uiState.value
            _uiState.update { cur ->
                cur.copy(
                    items = cur.items.map { item -> item.copy(readAt = item.readAt ?: "read") },
                    unreadCount = 0L,
                    urgentUnreadCount = 0L,
                    error = null,
                )
            }
            repository.markAllRead()
                .onSuccess { refresh(silent = true) }
                .onFailure { err ->
                    _uiState.value = previous.copy(
                        error = err.message ?: "Unable to mark all notifications as read.",
                    )
                }
        }
    }
}

fun effectivePriority(item: StaffNotificationItemDto): String {
    if (item.priority in setOf("urgent", "high", "normal") && item.priority != "normal") {
        return item.priority
    }
    return when (item.kind.lowercase()) {
        "bank_transfer_pending",
        "payment_receipt_uploaded",
        "support_sla_breached",
        "maintenance_critical",
        "job_failure",
        -> "urgent"
        "guest_support_conversation" ->
            if (item.title.equals("Cancellation request", ignoreCase = true)) "urgent" else "high"
        "guest_support_message",
        "support_sla_at_risk",
        "ekyc_pending_review",
        "maintenance_high",
        -> "high"
        else -> item.priority.lowercase()
    }
}

fun notificationActionLabel(item: StaffNotificationItemDto): String =
    when (item.actionLabelKey) {
        "reviewPayment" -> "Review payment"
        "reviewReceipt" -> "Review receipt"
        "reviewCancellation" -> "Review cancellation"
        "replyGuest", "replyBeforeSla", "handleSlaBreach" -> "Open support"
        "reviewEkyc" -> "Review eKYC"
        "openMaintenance" -> "Open maintenance"
        "viewBooking" -> "View booking"
        "inspectJobs" -> "Inspect jobs"
        else -> when (item.kind.lowercase()) {
            "bank_transfer_pending" -> "Review payment"
            "payment_receipt_uploaded" -> "Review receipt"
            "guest_support_conversation",
            "guest_support_message",
            "support_sla_breached",
            "support_sla_at_risk",
            -> "Open support"
            "ekyc_pending_review" -> "Review eKYC"
            "maintenance_critical", "maintenance_high" -> "Open maintenance"
            "online_booking_created" -> "View booking"
            "job_failure" -> "Inspect jobs"
            else -> "Open"
        }
    }

private fun notificationIconAndTone(
    kind: String,
    priority: String,
    colors: HotelExtendedColors,
): Pair<ImageVector, StatusTone> {
    val icon = when (kind.lowercase()) {
        "online_booking_created", "booking", "bookings", "reservation", "checkin", "checkout" ->
            Icons.Outlined.CalendarMonth
        "bank_transfer_pending", "payment_receipt_uploaded", "payment", "payments", "invoice" ->
            Icons.Outlined.Payments
        "housekeeping", "cleaning" ->
            Icons.Outlined.CleaningServices
        "maintenance_critical", "maintenance_high", "maintenance", "repair" ->
            Icons.Outlined.Build
        "ekyc_pending_review", "ekyc", "identity", "verification" ->
            Icons.Outlined.Badge
        "guest_support_conversation",
        "guest_support_message",
        "support_sla_breached",
        "support_sla_at_risk",
        "support",
        "guest_message",
        "inquiry",
        -> Icons.Outlined.SupportAgent
        "job_failure" ->
            Icons.Outlined.ErrorOutline
        else -> Icons.Outlined.Notifications
    }
    val tone = when {
        priority == "urgent" -> colors.danger
        priority == "high" -> colors.warning
        kind.lowercase() in setOf("online_booking_created", "booking", "bookings", "ekyc_pending_review", "ekyc") -> colors.info
        kind.lowercase() in setOf("housekeeping", "cleaning") -> colors.warning
        kind.lowercase() in setOf("maintenance_high", "maintenance", "repair") -> colors.orange
        kind.lowercase() in setOf("payment", "payments", "invoice") -> colors.success
        else -> colors.neutral
    }
    return icon to tone
}

@Composable
fun NotificationsModalSheet(
    viewModel: NotificationsViewModel,
    onDismiss: () -> Unit,
    onOpenItem: (StaffNotificationItemDto) -> Unit = {},
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val displayedItems = remember(state.items, state.filter) {
        when (state.filter) {
            NotificationFilter.ACTION_NEEDED -> state.items.filter { it.actionPending }
            NotificationFilter.ALL -> state.items
        }
    }

    HotelModalSheetContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 600.dp)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Notifications",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.unreadCount > 0) {
                        val urgent = state.urgentUnreadCount > 0
                        StatusPill(
                            label = "${state.unreadCount} unread",
                            tone = if (urgent) {
                                colors.danger
                            } else {
                                StatusTone(
                                    fg = colors.primaryOnSurface,
                                    bg = colors.primarySubtle,
                                    border = colors.primarySubtleBorder,
                                )
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.unreadCount > 0) {
                        TextButton(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = {
                                haptics.confirm()
                                viewModel.markAllRead()
                            },
                        ) {
                            Text(
                                text = "Mark all read",
                                color = colors.primaryOnSurface,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    HotelTooltipIconButton(
                        tooltip = "Refresh notifications",
                        onClick = { viewModel.refresh() },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh notifications",
                            tint = colors.textSecondary,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            ) {
                val pendingTotal = state.items.count { it.actionPending }
                FilterChip(
                    selected = state.filter == NotificationFilter.ACTION_NEEDED,
                    onClick = { viewModel.setFilter(NotificationFilter.ACTION_NEEDED) },
                    label = { Text("Action needed ($pendingTotal)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primarySubtle,
                        selectedLabelColor = colors.primaryOnSurface,
                    ),
                )
                FilterChip(
                    selected = state.filter == NotificationFilter.ALL,
                    onClick = { viewModel.setFilter(NotificationFilter.ALL) },
                    label = { Text("All (${state.items.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primarySubtle,
                        selectedLabelColor = colors.primaryOnSurface,
                    ),
                )
            }

            when {
                state.isLoading && state.items.isEmpty() -> ListCardSkeleton(
                    count = 3,
                    modifier = Modifier.height(260.dp),
                )
                state.error != null && state.items.isEmpty() -> ErrorStateView(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                )
                displayedItems.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.error != null) {
                        InlineAlertBanner(
                            message = state.error!!,
                            tone = colors.danger,
                            onDismiss = viewModel::clearError,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    EmptyStateView(
                        title = "All caught up",
                        subtitle = if (state.filter == NotificationFilter.ACTION_NEEDED && state.items.isNotEmpty()) {
                            "No pending actions right now."
                        } else {
                            "No staff notifications yet."
                        },
                        icon = Icons.Outlined.NotificationsNone,
                    )
                    if (state.filter == NotificationFilter.ACTION_NEEDED && state.items.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setFilter(NotificationFilter.ALL) }) {
                            Text("Show all history", color = colors.primaryOnSurface)
                        }
                    }
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 24.dp),
                ) {
                    if (state.error != null) {
                        item(key = "notif_inline_error") {
                            InlineAlertBanner(
                                message = state.error!!,
                                tone = colors.danger,
                                onDismiss = viewModel::clearError,
                            )
                        }
                    }
                    items(displayedItems, key = { it.id }) { item ->
                        val priority = effectivePriority(item)
                        val isPending = item.actionPending
                        val isUnread = item.readAt == null && isPending
                        val (icon, tone) = notificationIconAndTone(item.kind, priority, colors)
                        val dismissState = rememberSwipeToDismissBoxState()

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = item.readAt == null,
                            enableDismissFromEndToStart = false,
                            onDismiss = { direction ->
                                if (direction == SwipeToDismissBoxValue.StartToEnd && item.readAt == null) {
                                    haptics.confirm()
                                    viewModel.markRead(item.id)
                                    scope.launch {
                                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                    }
                                }
                            },
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.primarySubtle)
                                        .border(1.dp, colors.primarySubtleBorder, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DoneAll,
                                            contentDescription = "Mark read",
                                            tint = colors.primaryOnSurface,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = "Mark as read",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.primaryOnSurface,
                                        )
                                    }
                                }
                            },
                        ) {
                            val cardBorder = when {
                                isUnread && priority == "urgent" -> colors.danger.border
                                isUnread -> colors.primarySubtleBorder
                                else -> colors.borderSubtle
                            }
                            HotelCard(
                                onClick = { onOpenItem(item) },
                                contentPadding = PaddingValues(0.dp),
                                containerColor = if (isUnread) colors.surfaceRaised else colors.surfaceBase,
                                borderColor = cardBorder,
                            ) {
                                ListItem(
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(tone.bg)
                                                .border(1.dp, tone.border, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = tone.fg,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    headlineContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (priority == "urgent" || priority == "high" || item.requiresAction) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (priority == "urgent") {
                                                        StatusPill(label = "URGENT", tone = colors.danger)
                                                    } else if (priority == "high") {
                                                        StatusPill(label = "HIGH", tone = colors.warning)
                                                    }
                                                    if (item.requiresAction) {
                                                        StatusPill(
                                                            label = if (isPending) "Action needed" else "Handled",
                                                            tone = if (isPending) colors.info else colors.success,
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = item.title,
                                                color = colors.textPrimary,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Medium,
                                            )
                                        }
                                    },
                                    supportingContent = {
                                        Column {
                                            if (!item.body.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.body,
                                                    color = colors.textSecondary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = DateFormatter.formatRelativeTimestamp(item.createdAt),
                                                    color = colors.textMuted,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                                Text(
                                                    text = notificationActionLabel(item),
                                                    color = if (priority == "urgent" && isPending) colors.danger.fg else colors.primaryOnSurface,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = if (isUnread) {
                                        {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (priority == "urgent") colors.danger.fg else colors.primaryMain)
                                                    .semantics { contentDescription = "Unread" },
                                            )
                                        }
                                    } else null,
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
