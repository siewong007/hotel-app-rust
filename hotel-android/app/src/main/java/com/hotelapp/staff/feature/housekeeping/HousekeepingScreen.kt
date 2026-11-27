package com.hotelapp.staff.feature.housekeeping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.design.ConfirmActionDialog
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSegmentedControl
import com.hotelapp.staff.core.design.HotelSnackbarHost
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.HousekeepingSkeleton
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.ui.CollectEffects
import com.hotelapp.staff.data.dto.HousekeepingBoardRoomDto
import com.hotelapp.staff.data.dto.HousekeepingTaskDto

private val TASK_FILTERS = listOf(
    FilterOption("all", "All"),
    FilterOption("pending", "Pending"),
    FilterOption("in_progress", "In Progress"),
    FilterOption("completed", "Completed"),
    FilterOption("unassigned", "Unassigned"),
)

private fun HousekeepingBoardRoomDto.matchesBoardFilter(filter: String): Boolean {
    val s = status.lowercase()
    return when (filter) {
        "all" -> true
        "needs_clean" -> s in setOf("dirty", "reserved_dirty")
        "cleaning" -> s == "cleaning"
        "clean" -> s == "available"
        "occupied" -> s == "occupied"
        else -> true
    }
}

@Composable
fun HousekeepingScreen(
    session: StaffSession,
    viewModel: HousekeepingViewModel,
    onShowMessage: (String) -> Unit = {},
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val canUpdate = session.hasAnyPermission("housekeeping:update", "housekeeping:manage")
    val canCreate = session.hasAnyPermission("housekeeping:create", "housekeeping:manage")

    var assignTargetTask by remember { mutableStateOf<HousekeepingTaskDto?>(null) }
    var voidTargetTask by remember { mutableStateOf<HousekeepingTaskDto?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HousekeepingEffect.ShowMessage -> {
                if (effect.isError) haptics.reject() else haptics.confirm()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(effect.message)
                viewModel.clearNotice()
            }
        }
    }

    OnResumeRefreshEffect { viewModel.refresh(silent = true) }

    val boardFilters = remember(state.boardRooms, colors) {
        fun countFor(key: String) = state.boardRooms.count { it.matchesBoardFilter(key) }
        listOf(
            FilterOption("all", "All", count = state.boardRooms.size),
            FilterOption("needs_clean", "Dirty", count = countFor("needs_clean"), dotColor = colors.orange.fg),
            FilterOption("cleaning", "Cleaning", count = countFor("cleaning"), dotColor = colors.warning.fg),
            FilterOption("clean", "Clean", count = countFor("clean"), dotColor = colors.success.fg),
            FilterOption("occupied", "Occupied", count = countFor("occupied"), dotColor = colors.info.fg),
        )
    }

    val filteredBoardRooms = remember(state.boardRooms, state.boardFilter) {
        state.boardRooms.filter { it.matchesBoardFilter(state.boardFilter) }
    }

    Scaffold(
        containerColor = colors.surfaceApp,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { HotelSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (canCreate && state.boardRooms.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        viewModel.clearSheetError()
                        showCreateSheet = true
                    },
                    containerColor = colors.primaryMain,
                    contentColor = colors.primaryContrastText,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create Housekeeping Task")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            HotelSegmentedControl(
                options = listOf(
                    HousekeepingMode.BOARD to "Room Board",
                    HousekeepingMode.TASKS to "Tasks (${if (state.totalTasks > 0) state.totalTasks else state.tasks.size})",
                ),
                selected = state.mode,
                onSelect = viewModel::setMode,
            )

            Spacer(modifier = Modifier.height(8.dp))
            if (state.mode == HousekeepingMode.BOARD) {
                FilterChipRow(
                    options = boardFilters,
                    selected = state.boardFilter,
                    onSelect = viewModel::setBoardFilter,
                )
            } else {
                FilterChipRow(
                    options = TASK_FILTERS,
                    selected = state.taskFilter,
                    onSelect = viewModel::setTaskFilter,
                )
            }

            if (state.isShowingCachedData) {
                Spacer(modifier = Modifier.height(8.dp))
                InlineAlertBanner(
                    message = "Showing cached housekeeping board — pull to refresh when online.",
                    tone = colors.warning,
                )
            }
            if (state.actionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                InlineAlertBanner(
                    message = state.actionError!!,
                    tone = colors.danger,
                    onDismiss = viewModel::clearActionError,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            when {
                state.isLoading -> HousekeepingSkeleton()
                state.error != null -> ErrorStateView(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                )
                else -> HotelPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.mode == HousekeepingMode.BOARD) {
                        if (filteredBoardRooms.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                EmptyStateView(
                                    title = "No rooms match filter",
                                    icon = Icons.Outlined.CleaningServices,
                                    actionLabel = if (state.boardFilter != "all") "Show All Rooms" else null,
                                    onAction = if (state.boardFilter != "all") { { viewModel.setBoardFilter("all") } } else null,
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(filteredBoardRooms, key = { it.id }) { room ->
                                    val roomTone = StatusFormatter.roomTone(room.status, colors)
                                    HotelCard(
                                        accentColor = roomTone.fg,
                                        modifier = Modifier.animateItem(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Room ${room.roomNumber}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = colors.textPrimary,
                                                )
                                                Text(
                                                    text = room.roomType + (room.floor?.let { " · Floor $it" } ?: ""),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            StatusPill(
                                                label = StatusFormatter.humanize(room.status),
                                                tone = roomTone,
                                                leadingDot = true,
                                            )
                                        }
                                        room.openTask?.let { task ->
                                            Spacer(modifier = Modifier.height(10.dp))
                                            TaskActionBlock(
                                                task = task,
                                                canUpdate = canUpdate,
                                                isBusy = state.isMutating,
                                                onTransition = { next ->
                                                    if (next == "void") {
                                                        voidTargetTask = task
                                                    } else {
                                                        viewModel.updateTaskStatus(task.id, next)
                                                    }
                                                },
                                                onAssignClick = { assignTargetTask = task },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.isLoadingTasks && state.tasks.isEmpty()) {
                            HousekeepingSkeleton()
                        } else if (state.tasksError != null && state.tasks.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                ErrorStateView(
                                    message = state.tasksError!!,
                                    onRetry = { viewModel.refresh() },
                                )
                            }
                        } else if (state.tasks.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                EmptyStateView(
                                    title = "No housekeeping tasks",
                                    subtitle = "Adjust filter or create a new task.",
                                    icon = Icons.Outlined.CleaningServices,
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (state.tasksError != null) {
                                    item(key = "hk_tasks_inline_error") {
                                        InlineAlertBanner(message = state.tasksError!!, tone = colors.danger)
                                    }
                                }
                                items(state.tasks, key = { it.id }) { task ->
                                    val priorityTone = StatusFormatter.priorityTone(task.priority, colors)
                                    HotelCard(
                                        accentColor = priorityTone.fg,
                                        modifier = Modifier.animateItem(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Room ${task.roomNumber} · ${StatusFormatter.humanize(task.taskType)}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            StatusPill(
                                                label = StatusFormatter.humanize(task.priority),
                                                tone = priorityTone,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        TaskActionBlock(
                                            task = task,
                                            canUpdate = canUpdate,
                                            isBusy = state.isMutating,
                                            onTransition = { next ->
                                                if (next == "void") {
                                                    voidTargetTask = task
                                                } else {
                                                    viewModel.updateTaskStatus(task.id, next)
                                                }
                                            },
                                            onAssignClick = { assignTargetTask = task },
                                        )
                                    }
                                }
                                if (!state.tasksEndReached && state.tasks.size < state.totalTasks) {
                                    item(key = "hk_tasks_load_more") {
                                        if (state.isLoadingMoreTasks) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                            }
                                        } else {
                                            HotelOutlinedButton(
                                                text = "Load More (${state.tasks.size}/${state.totalTasks})",
                                                onClick = viewModel::loadMoreTasks,
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
    }

    assignTargetTask?.let { task ->
        AssignStaffSheet(
            title = "Assign Room ${task.roomNumber}",
            staff = state.staff,
            currentAssigneeId = task.assignedTo,
            staffPickerError = state.staffPickerError,
            isLoadingStaff = state.isLoadingStaff,
            onRetryStaff = viewModel::retryLoadStaff,
            onAssign = { staffId ->
                assignTargetTask = null
                viewModel.assignTask(task.id, staffId)
            },
            onDismiss = { assignTargetTask = null },
        )
    }

    voidTargetTask?.let { task ->
        ConfirmActionDialog(
            title = "Void Housekeeping Task",
            message = "Void the ${StatusFormatter.humanize(task.taskType).lowercase()} task for Room ${task.roomNumber}?",
            confirmLabel = "Void Task",
            isDestructive = true,
            onConfirm = {
                voidTargetTask = null
                viewModel.updateTaskStatus(task.id, "void")
            },
            onDismiss = { voidTargetTask = null },
        )
    }

    if (showCreateSheet) {
        CreateHousekeepingTaskSheet(
            rooms = state.boardRooms,
            staff = state.staff,
            isSubmitting = state.isMutating,
            error = state.sheetError,
            onDismiss = {
                viewModel.clearSheetError()
                showCreateSheet = false
            },
            onCreate = { roomId, type, priority, assignedTo, notes ->
                viewModel.createTask(roomId, type, priority, assignedTo, notes) {
                    showCreateSheet = false
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskActionBlock(
    task: HousekeepingTaskDto,
    canUpdate: Boolean,
    isBusy: Boolean,
    onTransition: (String) -> Unit,
    onAssignClick: () -> Unit,
) {
    val colors = HotelTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                label = StatusFormatter.humanize(task.status),
                tone = StatusFormatter.taskTone(task.status, colors),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = task.assignedToName?.let { "Assigned: $it" } ?: "Unassigned",
                style = MaterialTheme.typography.bodySmall,
                color = if (task.assignedToName != null) colors.textSecondary else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (!task.notes.isNullOrBlank()) {
            Text(
                text = task.notes,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        if (canUpdate && task.status in setOf("pending", "in_progress")) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (task.status == "pending") {
                    HotelPrimaryButton(
                        text = "Start",
                        icon = Icons.Outlined.PlayArrow,
                        enabled = !isBusy,
                        onClick = { onTransition("in_progress") },
                    )
                } else if (task.status == "in_progress") {
                    HotelPrimaryButton(
                        text = "Complete",
                        icon = Icons.Outlined.CheckCircleOutline,
                        enabled = !isBusy,
                        containerColor = colors.success.fg,
                        contentColor = colors.primaryContrastText,
                        onClick = { onTransition("completed") },
                    )
                }
                HotelOutlinedButton(
                    text = "Assign",
                    icon = Icons.Outlined.PersonAddAlt1,
                    enabled = !isBusy,
                    onClick = onAssignClick,
                )
                HotelOutlinedButton(
                    text = "Void",
                    isDestructive = true,
                    enabled = !isBusy,
                    onClick = { onTransition("void") },
                )
            }
        }
    }
}
