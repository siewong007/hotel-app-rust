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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SupportAgent
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.hotelapp.staff.core.design.ListCardSkeleton
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.StatusTone
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.StaffNotificationItemDto
import com.hotelapp.staff.data.repository.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val unreadCount: Long = 0,
    val items: List<StaffNotificationItemDto> = emptyList(),
    val error: String? = null,
)

class NotificationsViewModel(
    private val repository: NotificationsRepository,
    realtimeSocketClient: RealtimeSocketClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

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

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
            }
            repository.list()
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            unreadCount = res.unread,
                            items = res.items,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, error = if (silent) it.error else err.message)
                    }
                }
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch {
            _uiState.update { cur ->
                val targetWasUnread = cur.items.any { it.id == id && it.readAt == null }
                cur.copy(
                    items = cur.items.map { item -> if (item.id == id) item.copy(readAt = item.readAt ?: "read") else item },
                    unreadCount = if (targetWasUnread) (cur.unreadCount - 1).coerceAtLeast(0L) else cur.unreadCount,
                )
            }
            repository.markRead(id).onSuccess {
                refresh(silent = true)
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            _uiState.update { cur ->
                cur.copy(
                    items = cur.items.map { item -> item.copy(readAt = item.readAt ?: "read") },
                    unreadCount = 0L,
                )
            }
            repository.markAllRead().onSuccess {
                refresh(silent = true)
            }
        }
    }
}

private fun notificationIconAndTone(kind: String, colors: HotelExtendedColors): Pair<ImageVector, StatusTone> =
    when (kind.lowercase()) {
        "booking", "bookings", "reservation", "checkin", "checkout" ->
            Icons.Outlined.CalendarMonth to colors.info
        "payment", "payments", "invoice" ->
            Icons.Outlined.Payments to colors.success
        "housekeeping", "cleaning" ->
            Icons.Outlined.CleaningServices to colors.warning
        "maintenance", "repair" ->
            Icons.Outlined.Build to colors.orange
        "ekyc", "identity", "verification" ->
            Icons.Outlined.Badge to colors.info
        "support", "guest_message", "inquiry" ->
            Icons.Outlined.SupportAgent to colors.violet
        else -> Icons.Outlined.Notifications to colors.neutral
    }

@Composable
fun NotificationsModalSheet(
    viewModel: NotificationsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HotelModalSheetContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 580.dp)
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
                    )
                    if (state.unreadCount > 0) {
                        StatusPill(
                            label = "${state.unreadCount} unread",
                            tone = StatusTone(
                                fg = colors.primaryOnSurface,
                                bg = colors.primarySubtle,
                                border = colors.primarySubtleBorder,
                            ),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.unreadCount > 0) {
                        TextButton(
                            onClick = {
                                haptics.confirm()
                                viewModel.markAllRead()
                            },
                        ) {
                            Text("Mark all read", color = colors.primaryOnSurface)
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
            Spacer(modifier = Modifier.height(8.dp))

            when {
                state.isLoading -> ListCardSkeleton(
                    count = 3,
                    modifier = Modifier.height(260.dp),
                )
                state.error != null -> ErrorStateView(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                )
                state.items.isEmpty() -> EmptyStateView(
                    title = "All caught up",
                    subtitle = "No staff notifications yet.",
                    icon = Icons.Outlined.NotificationsNone,
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 24.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        val isUnread = item.readAt == null
                        val (icon, tone) = notificationIconAndTone(item.kind, colors)
                        val dismissState = rememberSwipeToDismissBoxState()

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = isUnread,
                            enableDismissFromEndToStart = false,
                            onDismiss = { direction ->
                                if (direction == SwipeToDismissBoxValue.StartToEnd && isUnread) {
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
                            HotelCard(
                                onClick = if (isUnread) {
                                    {
                                        haptics.tick()
                                        viewModel.markRead(item.id)
                                    }
                                } else null,
                                contentPadding = PaddingValues(0.dp),
                                containerColor = if (isUnread) colors.surfaceRaised else colors.surfaceBase,
                                borderColor = if (isUnread) colors.primarySubtleBorder else colors.borderSubtle,
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
                                        Text(
                                            text = item.title,
                                            color = colors.textPrimary,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Medium,
                                        )
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
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = DateFormatter.formatRelativeTimestamp(item.createdAt),
                                                color = colors.textMuted,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    },
                                    trailingContent = if (isUnread) {
                                        {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.primaryMain),
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
