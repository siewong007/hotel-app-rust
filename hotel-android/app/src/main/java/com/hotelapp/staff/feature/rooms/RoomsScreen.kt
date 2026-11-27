package com.hotelapp.staff.feature.rooms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KingBed
import androidx.compose.material.icons.outlined.SmokingRooms
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSearchBar
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.RoomGridSkeleton
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.ui.CollectEffects
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto

private fun RoomWithOccupancyDto.matchesRoomFilter(filter: String): Boolean {
    val rawStatus = (status ?: "available").lowercase()
    val effectiveStatus = if (isOccupied) "occupied" else rawStatus
    return when (filter) {
        "all" -> true
        "dirty" -> !isOccupied && rawStatus in setOf("dirty", "reserved_dirty")
        "reserved" -> !isOccupied && rawStatus in setOf("reserved", "reserved_dirty")
        else -> effectiveStatus == filter
    }
}

@Composable
fun RoomsScreen(
    session: StaffSession,
    viewModel: RoomsViewModel,
    serverConfigStore: ServerConfigStore,
    onOpenBookingDetail: (Long) -> Unit,
    onShowMessage: (String) -> Unit = {},
    focusRoomId: Long? = null,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val canUpdateRooms = session.hasPermission("rooms:update")

    LaunchedEffect(focusRoomId) {
        if (focusRoomId != null) {
            viewModel.openRoomDetail(focusRoomId)
        }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is RoomsEffect.ShowMessage -> {
                if (effect.isError) haptics.reject() else haptics.confirm()
                onShowMessage(effect.message)
                viewModel.clearNotice()
            }
        }
    }

    OnResumeRefreshEffect { viewModel.refresh(silent = true) }

    val filterOptions = remember(state.rooms, colors) {
        fun countFor(key: String) = state.rooms.count { it.matchesRoomFilter(key) }
        listOf(
            FilterOption("all", "All", count = state.rooms.size),
            FilterOption("available", "Available", count = countFor("available"), dotColor = colors.success.fg),
            FilterOption("occupied", "Occupied", count = countFor("occupied"), dotColor = colors.info.fg),
            FilterOption("dirty", "Dirty", count = countFor("dirty"), dotColor = colors.orange.fg),
            FilterOption("cleaning", "Cleaning", count = countFor("cleaning"), dotColor = colors.warning.fg),
            FilterOption("reserved", "Reserved", count = countFor("reserved"), dotColor = colors.violet.fg),
            FilterOption("maintenance", "Maintenance", count = countFor("maintenance"), dotColor = colors.danger.fg),
            FilterOption("out_of_order", "Out of Order", count = countFor("out_of_order"), dotColor = colors.danger.fg),
        )
    }

    val filtered = remember(state.rooms, state.search, state.statusFilter) {
        val q = state.search.trim().lowercase()
        state.rooms.filter { room ->
            val matchesStatus = room.matchesRoomFilter(state.statusFilter)
            val matchesQuery = q.isEmpty() ||
                room.roomNumber.lowercase().contains(q) ||
                room.roomType.lowercase().contains(q)
            matchesStatus && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        HotelSearchBar(
            query = state.search,
            onQueryChange = viewModel::setSearch,
            placeholder = "Filter by room # or type",
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilterChipRow(
            options = filterOptions,
            selected = state.statusFilter,
            onSelect = viewModel::setStatusFilter,
        )
        if (state.isShowingCachedData) {
            Spacer(modifier = Modifier.height(8.dp))
            InlineAlertBanner(
                message = "Showing cached room statuses — pull to refresh when online.",
                tone = colors.warning,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        when {
            state.isLoading -> RoomGridSkeleton()
            state.error != null && state.rooms.isEmpty() -> ErrorStateView(
                message = state.error!!,
                onRetry = { viewModel.refresh() },
            )
            else -> HotelPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EmptyStateView(
                            title = "No rooms match filter",
                            subtitle = "Adjust the status filter or search query.",
                            icon = Icons.Outlined.KingBed,
                            actionLabel = if (state.search.isNotBlank() || state.statusFilter != "all") "Reset Filter" else null,
                            onAction = if (state.search.isNotBlank() || state.statusFilter != "all") {
                                {
                                    viewModel.setSearch("")
                                    viewModel.setStatusFilter("all")
                                }
                            } else null,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { room ->
                            RoomRackTile(
                                room = room,
                                currency = config.currency,
                                onClick = { viewModel.openRoomDetail(room.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.isLoadingDetail || state.selectedRoomDetail != null || state.sheetError != null) {
        RoomDetailSheet(
            detail = state.selectedRoomDetail,
            isLoading = state.isLoadingDetail,
            isMutating = state.isMutating,
            error = state.sheetError,
            isOfflineFallback = state.isDetailOfflineFallback,
            canUpdateRooms = canUpdateRooms,
            canReadBookings = session.hasPermission("bookings:read"),
            onDismiss = viewModel::dismissDetail,
            onOpenBookingDetail = { id ->
                viewModel.dismissDetail()
                onOpenBookingDetail(id)
            },
            onEndCleaning = { viewModel.endCleaning(it) },
            onEndMaintenance = { viewModel.endMaintenance(it) },
            onUpdateStatus = { id, status, notes -> viewModel.updateRoomStatus(id, status, notes) },
        )
    }
}

@Composable
private fun RoomRackTile(
    room: RoomWithOccupancyDto,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HotelTheme.colors
    val displayStatus = if (room.isOccupied) "occupied" else (room.status ?: "available")
    val tone = StatusFormatter.roomTone(displayStatus, colors)
    val needsAttention = displayStatus.lowercase() in setOf("dirty", "reserved_dirty", "maintenance", "out_of_order")
    val shape = RoundedCornerShape(16.dp)

    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceBase),
        border = BorderStroke(
            width = 1.dp,
            color = if (needsAttention) tone.border else colors.borderSubtle,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(tone.fg),
            )
            Column(modifier = Modifier.padding(13.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = room.roomNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    if (room.isSmoking == true) {
                        Icon(
                            imageVector = Icons.Outlined.SmokingRooms,
                            contentDescription = "Smoking room",
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = room.roomType,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(10.dp))
                StatusPill(
                    label = StatusFormatter.humanize(displayStatus),
                    tone = tone,
                    leadingDot = true,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (room.isOccupied) {
                        "${room.currentTotalGuests}/${room.maxOccupancy} guests"
                    } else {
                        "${MoneyFormatter.format(room.pricePerNight, currency)}/nt"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
