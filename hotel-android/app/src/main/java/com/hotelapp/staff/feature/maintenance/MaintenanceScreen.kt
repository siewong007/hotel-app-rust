package com.hotelapp.staff.feature.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSearchBar
import com.hotelapp.staff.core.design.HotelSnackbarHost
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.ListCardSkeleton
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.ui.CollectEffects
import com.hotelapp.staff.feature.housekeeping.AssignStaffSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    session: StaffSession,
    viewModel: MaintenanceViewModel,
    serverConfigStore: ServerConfigStore,
    onBack: () -> Unit,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val canWrite = session.hasAnyPermission("maintenance:write", "maintenance:manage")
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showCreateSheet by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is MaintenanceEffect.ShowMessage -> {
                if (effect.isError) haptics.reject() else haptics.confirm()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(effect.message)
                viewModel.clearNotice()
            }
        }
    }

    val statusFilters = remember(colors) {
        listOf(
            FilterOption("all", "All"),
            FilterOption("open", "Open", dotColor = colors.warning.fg),
            FilterOption("in_progress", "In Progress", dotColor = colors.info.fg),
            FilterOption("on_hold", "On Hold", dotColor = colors.orange.fg),
            FilterOption("resolved", "Resolved", dotColor = colors.success.fg),
            FilterOption("closed", "Closed", dotColor = colors.neutral.fg),
        )
    }

    val priorityFilters = remember(colors) {
        listOf(
            FilterOption("all", "Any Priority"),
            FilterOption("critical", "Critical", dotColor = colors.danger.fg),
            FilterOption("high", "High", dotColor = colors.orange.fg),
            FilterOption("medium", "Medium", dotColor = colors.info.fg),
            FilterOption("low", "Low", dotColor = colors.neutral.fg),
        )
    }

    val filteredTickets = remember(state.tickets, state.searchQuery) {
        val q = state.searchQuery.trim().lowercase()
        if (q.isEmpty()) state.tickets else state.tickets.filter { t ->
            t.ticketNumber.lowercase().contains(q) ||
                t.title.lowercase().contains(q) ||
                (t.roomNumber?.lowercase()?.contains(q) == true) ||
                (t.assignedToName?.lowercase()?.contains(q) == true)
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
                    title = { Text("Maintenance", color = colors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
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
        floatingActionButton = {
            if (canWrite) {
                FloatingActionButton(
                    onClick = { showCreateSheet = true },
                    containerColor = colors.primaryMain,
                    contentColor = colors.primaryContrastText,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create Ticket")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            HotelSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                placeholder = "Search ticket #, title, room #, assignee",
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilterChipRow(
                options = statusFilters,
                selected = state.statusFilter,
                onSelect = viewModel::setStatusFilter,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FilterChipRow(
                options = priorityFilters,
                selected = state.priorityFilter,
                onSelect = viewModel::setPriorityFilter,
            )
            Spacer(modifier = Modifier.height(4.dp))

            when {
                state.isLoading -> ListCardSkeleton()
                state.error != null -> ErrorStateView(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                )
                else -> HotelPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (filteredTickets.isEmpty()) {
                        EmptyStateView(
                            title = "No maintenance tickets",
                            subtitle = "Adjust filters or create a new repair ticket.",
                            icon = Icons.Outlined.Build,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filteredTickets, key = { it.id }) { ticket ->
                                val priorityTone = StatusFormatter.priorityTone(ticket.priority, colors)
                                HotelCard(
                                    onClick = { viewModel.selectTicket(ticket) },
                                    accentColor = priorityTone.fg,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = ticket.ticketNumber,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.primaryOnSurface,
                                            )
                                            Text(
                                                text = DateFormatter.formatRelativeTimestamp(ticket.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textMuted,
                                            )
                                        }
                                        StatusPill(
                                            label = StatusFormatter.humanize(ticket.priority),
                                            tone = priorityTone,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = ticket.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val roomLabel = ticket.roomNumber?.let { "Room $it" } ?: "Common Area"
                                    Text(
                                        text = "$roomLabel · ${StatusFormatter.humanize(ticket.category)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        StatusPill(
                                            label = StatusFormatter.humanize(ticket.status),
                                            tone = StatusFormatter.taskTone(ticket.status, colors),
                                        )
                                        Text(
                                            text = ticket.assignedToName ?: "Unassigned",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (ticket.assignedToName != null) colors.textSecondary else colors.textMuted,
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

    state.selectedTicket?.let { ticket ->
        MaintenanceDetailSheet(
            ticket = ticket,
            currency = config.currency,
            canWrite = canWrite,
            isMutating = state.isMutating,
            error = state.sheetError,
            onDismiss = { viewModel.selectTicket(null) },
            onTransition = { nextStatus, notes -> viewModel.updateTicketStatus(ticket.id, nextStatus, notes) },
            onAssignClick = { showAssignDialog = true },
        )

        if (showAssignDialog) {
            AssignStaffSheet(
                title = "Assign ${ticket.ticketNumber}",
                staff = state.staff,
                currentAssigneeId = ticket.assignedTo,
                onAssign = { staffId ->
                    showAssignDialog = false
                    viewModel.assignTicket(ticket.id, staffId)
                },
                onDismiss = { showAssignDialog = false },
            )
        }
    }

    if (showCreateSheet) {
        CreateMaintenanceTicketSheet(
            rooms = state.rooms,
            isSubmitting = state.isMutating,
            error = state.sheetError,
            onDismiss = { showCreateSheet = false },
            onCreate = { roomId, title, category, priority, desc ->
                viewModel.createTicket(roomId, title, category, priority, desc) {
                    showCreateSheet = false
                }
            },
        )
    }
}
