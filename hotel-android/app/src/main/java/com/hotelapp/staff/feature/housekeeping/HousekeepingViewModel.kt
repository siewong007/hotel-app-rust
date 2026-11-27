package com.hotelapp.staff.feature.housekeeping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.sync.OfflineOpsCoordinator
import com.hotelapp.staff.data.dto.AssignableStaffMemberDto
import com.hotelapp.staff.data.dto.CreateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.dto.HousekeepingBoardResponseDto
import com.hotelapp.staff.data.dto.HousekeepingBoardRoomDto
import com.hotelapp.staff.data.dto.HousekeepingTaskDto
import com.hotelapp.staff.data.dto.UpdateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.repository.HousekeepingRepository
import com.hotelapp.staff.feature.rooms.RoomsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HousekeepingMode { BOARD, TASKS }

sealed interface HousekeepingEffect {
    data class ShowMessage(val message: String, val isError: Boolean = false) : HousekeepingEffect
}

data class HousekeepingUiState(
    val mode: HousekeepingMode = HousekeepingMode.BOARD,
    val boardFilter: String = "all",
    val taskFilter: String = "all",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isShowingCachedData: Boolean = false,
    val isMutating: Boolean = false,
    val boardRooms: List<HousekeepingBoardRoomDto> = emptyList(),
    val tasks: List<HousekeepingTaskDto> = emptyList(),
    val isLoadingTasks: Boolean = false,
    val totalTasks: Long = 0,
    val tasksPage: Long = 1,
    val isLoadingMoreTasks: Boolean = false,
    val tasksEndReached: Boolean = false,
    val tasksError: String? = null,
    val staff: List<AssignableStaffMemberDto> = emptyList(),
    val isLoadingStaff: Boolean = false,
    val staffPickerError: String? = null,
    val error: String? = null,
    val sheetError: String? = null,
    val actionError: String? = null,
    val actionNotice: String? = null,
)

class HousekeepingViewModel(
    session: StaffSession,
    private val repository: HousekeepingRepository,
    realtimeSocketClient: RealtimeSocketClient,
    private val offlineOps: OfflineOpsCoordinator? = null,
) : ViewModel() {

    private val canUpdate = session.hasAnyPermission("housekeeping:update", "housekeeping:manage")

    private val _uiState = MutableStateFlow(HousekeepingUiState())
    val uiState: StateFlow<HousekeepingUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HousekeepingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var refreshJob: Job? = null
    private var loadMoreTasksJob: Job? = null

    init {
        refresh()
        if (canUpdate) {
            loadAssignableStaff()
        }
        viewModelScope.launch {
            realtimeSocketClient.domainEvents.collect { domain ->
                if (domain == "housekeeping" || domain == "rooms") {
                    refresh(silent = true)
                }
            }
        }
    }

    fun setMode(mode: HousekeepingMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setBoardFilter(filter: String) {
        _uiState.update { it.copy(boardFilter = filter) }
    }

    fun setTaskFilter(filter: String) {
        if (_uiState.value.taskFilter == filter) return
        _uiState.update {
            it.copy(
                taskFilter = filter,
                isLoadingTasks = true,
                tasks = emptyList(),
                totalTasks = 0,
                tasksPage = 1,
                tasksEndReached = false,
                tasksError = null,
            )
        }
        refresh()
    }

    fun clearNotice() {
        _uiState.update { it.copy(actionNotice = null) }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    fun clearSheetError() {
        _uiState.update { it.copy(sheetError = null) }
    }

    fun retryLoadStaff() {
        if (canUpdate) loadAssignableStaff()
    }

    fun refresh(silent: Boolean = false) {
        if (canUpdate && _uiState.value.staff.isEmpty()) {
            loadAssignableStaff()
        }
        loadMoreTasksJob?.cancel()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val snapshot = _uiState.value
            val taskFilterAtDispatch = snapshot.taskFilter
            _uiState.update {
                if (silent) {
                    it.copy(isLoadingMoreTasks = false)
                } else {
                    it.copy(
                        isLoading = it.boardRooms.isEmpty() && it.tasks.isEmpty() && !it.isLoadingTasks,
                        isRefreshing = it.boardRooms.isNotEmpty() || (it.tasks.isNotEmpty() && !it.isLoadingTasks),
                        isLoadingMoreTasks = false,
                        error = null,
                        tasksError = null,
                        actionError = null,
                    )
                }
            }
            try {
                coroutineScopeCompat(taskFilterAtDispatch, silent)
            } finally {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingTasks = false,
                    )
                }
            }
        }
    }

    private suspend fun coroutineScopeCompat(taskFilterAtDispatch: String, silent: Boolean) {
        kotlinx.coroutines.coroutineScope {
            val boardDef = async { repository.board() }
            val (statusParam, unassignedParam) = taskQueryParams(taskFilterAtDispatch)
            val tasksDef = async { repository.tasks(status = statusParam, unassigned = unassignedParam, page = 1) }

            val boardRes = boardDef.await()
            val tasksRes = tasksDef.await()

            boardRes.getOrNull()?.let { board ->
                offlineOps?.saveCache(BOARD_CACHE_KEY, HousekeepingBoardResponseDto.serializer(), board)
            }
            val cachedBoard = if (boardRes.isFailure && _uiState.value.boardRooms.isEmpty()) {
                offlineOps?.loadCache(BOARD_CACHE_KEY, HousekeepingBoardResponseDto.serializer())
            } else null

            val firstErr = boardRes.exceptionOrNull() ?: tasksRes.exceptionOrNull()
            _uiState.update { cur ->
                val resolvedRooms = (boardRes.getOrNull() ?: cachedBoard)?.rooms?.sortedWith { a, b ->
                    val prio = roomPriorityRank(a.status).compareTo(roomPriorityRank(b.status))
                    if (prio != 0) prio else RoomsViewModel.compareRoomNumbersNatural(a.roomNumber, b.roomNumber)
                } ?: cur.boardRooms

                val sameTaskFilter = cur.taskFilter == taskFilterAtDispatch
                val taskPageRes = tasksRes.getOrNull()
                val preservePagedScroll = sameTaskFilter && silent && taskPageRes != null && cur.tasksPage > 1 && cur.tasks.isNotEmpty()
                val nextTasks = when {
                    !sameTaskFilter -> cur.tasks
                    preservePagedScroll && taskPageRes != null -> {
                        val updatedById = taskPageRes.items.associateBy { it.id }
                        (taskPageRes.items + cur.tasks.filter { it.id !in updatedById }).distinctBy { it.id }
                    }
                    taskPageRes != null -> taskPageRes.items
                    else -> cur.tasks
                }
                val nextTotalTasks = if (sameTaskFilter && taskPageRes != null) taskPageRes.total else cur.totalTasks
                val nextTasksPage = when {
                    !sameTaskFilter -> cur.tasksPage
                    preservePagedScroll -> cur.tasksPage
                    taskPageRes != null -> taskPageRes.page
                    else -> cur.tasksPage
                }
                val nextTasksEnd = when {
                    !sameTaskFilter -> cur.tasksEndReached
                    preservePagedScroll && taskPageRes != null -> nextTasks.size >= taskPageRes.total || cur.tasksEndReached
                    taskPageRes != null -> taskPageRes.items.isEmpty() || nextTasks.size >= taskPageRes.total
                    else -> cur.tasksEndReached
                }
                val nextTasksError = if (sameTaskFilter && tasksRes.isFailure && (!silent || cur.tasks.isEmpty())) {
                    tasksRes.exceptionOrNull()?.message ?: "Failed to load housekeeping tasks."
                } else null

                cur.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingTasks = if (sameTaskFilter) false else cur.isLoadingTasks,
                    isShowingCachedData = boardRes.isFailure && (cachedBoard != null || cur.boardRooms.isNotEmpty()),
                    boardRooms = resolvedRooms,
                    tasks = nextTasks,
                    totalTasks = nextTotalTasks,
                    tasksPage = nextTasksPage,
                    tasksEndReached = nextTasksEnd,
                    tasksError = nextTasksError,
                    error = if (resolvedRooms.isEmpty() && nextTasks.isEmpty() && tasksRes.isFailure && boardRes.isFailure) {
                        firstErr?.message
                    } else if (resolvedRooms.isEmpty() && taskPageRes?.items == null && !silent) {
                        firstErr?.message
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun loadMoreTasks() {
        val s = _uiState.value
        if (s.isLoadingMoreTasks || s.tasksEndReached || s.tasks.size >= s.totalTasks) return
        loadMoreTasksJob?.cancel()
        loadMoreTasksJob = viewModelScope.launch {
            val taskFilterAtDispatch = s.taskFilter
            _uiState.update { it.copy(isLoadingMoreTasks = true, tasksError = null) }
            val (statusParam, unassignedParam) = taskQueryParams(taskFilterAtDispatch)
            repository.tasks(
                status = statusParam,
                unassigned = unassignedParam,
                page = s.tasksPage + 1,
            ).onSuccess { res ->
                _uiState.update { cur ->
                    if (cur.taskFilter != taskFilterAtDispatch) {
                        return@update cur.copy(isLoadingMoreTasks = false)
                    }
                    val combined = (cur.tasks + res.items).distinctBy { it.id }
                    val reachedEnd = res.items.isEmpty() ||
                        combined.size >= res.total ||
                        combined.size == cur.tasks.size
                    cur.copy(
                        isLoadingMoreTasks = false,
                        tasks = combined,
                        totalTasks = res.total,
                        tasksPage = res.page,
                        tasksEndReached = reachedEnd,
                        tasksError = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update { cur ->
                    cur.copy(
                        isLoadingMoreTasks = false,
                        tasksError = err.message ?: "Failed to load more tasks.",
                    )
                }
            }
        }
    }

    private fun taskQueryParams(filter: String): Pair<String?, Boolean?> = when (filter) {
        "unassigned" -> null to true
        "all" -> null to null
        else -> filter to null
    }

    private fun loadAssignableStaff() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStaff = true, staffPickerError = null) }
            repository.assignableStaff("housekeeping")
                .onSuccess { staff ->
                    _uiState.update { it.copy(isLoadingStaff = false, staff = staff, staffPickerError = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingStaff = false,
                            staffPickerError = err.message ?: "Unable to load assignable staff.",
                        )
                    }
                }
        }
    }

    fun updateTaskStatus(taskId: Long, newStatus: String) {
        val label = StatusFormatter.humanize(newStatus)
        val req = UpdateHousekeepingTaskRequestDto(status = newStatus)
        mutate(
            taskId = taskId,
            notice = "Task marked $label.",
            optimisticTransform = { task -> task.copy(status = newStatus) },
            offlineFallback = { offlineOps?.enqueueHousekeepingTaskUpdate(taskId, req) },
        ) {
            repository.updateTask(taskId, req)
        }
    }

    fun assignTask(taskId: Long, staffId: Long?) {
        val req = if (staffId == null) {
            UpdateHousekeepingTaskRequestDto(clearAssignee = true)
        } else {
            UpdateHousekeepingTaskRequestDto(assignedTo = staffId)
        }
        val assigneeName = staffId?.let { id ->
            _uiState.value.staff.find { it.id == id }?.displayName()
        }
        val notice = if (staffId == null) "Task unassigned." else "Task assigned."
        mutate(
            taskId = taskId,
            notice = notice,
            optimisticTransform = { task ->
                if (staffId == null) {
                    task.copy(assignedTo = null, assignedToName = null)
                } else {
                    task.copy(assignedTo = staffId, assignedToName = assigneeName ?: task.assignedToName)
                }
            },
            offlineFallback = { offlineOps?.enqueueHousekeepingTaskUpdate(taskId, req) },
        ) { repository.updateTask(taskId, req) }
    }

    fun createTask(
        roomId: Long,
        taskType: String,
        priority: String,
        assignedTo: Long?,
        notes: String?,
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, actionError = null, sheetError = null) }
            repository.createTask(
                CreateHousekeepingTaskRequestDto(
                    roomId = roomId,
                    taskType = taskType,
                    priority = priority,
                    assignedTo = assignedTo,
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                ),
            ).onSuccess {
                val msg = "Housekeeping task created."
                _uiState.update { it.copy(isMutating = false, actionNotice = msg, sheetError = null) }
                _effects.trySend(HousekeepingEffect.ShowMessage(msg))
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                _uiState.update { it.copy(isMutating = false, sheetError = err.message ?: "Unable to create task.") }
            }
        }
    }

    private fun mutate(
        taskId: Long,
        notice: String,
        optimisticTransform: (HousekeepingTaskDto) -> HousekeepingTaskDto,
        offlineFallback: (suspend () -> Unit)? = null,
        block: suspend () -> Result<HousekeepingTaskDto>,
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, actionError = null) }
            block()
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, actionNotice = notice) }
                    _effects.trySend(HousekeepingEffect.ShowMessage(notice))
                    refresh(silent = true)
                }
                .onFailure { err ->
                    if (offlineFallback != null && offlineOps?.isQueueableNetworkFailure(err) == true) {
                        offlineFallback()
                        val queuedMsg = "Offline — housekeeping update queued for sync."
                        _uiState.update { cur ->
                            cur.copy(
                                isMutating = false,
                                tasks = cur.tasks.map { task ->
                                    if (task.id == taskId) optimisticTransform(task) else task
                                },
                                boardRooms = cur.boardRooms.map { room ->
                                    val open = room.openTask
                                    if (open != null && open.id == taskId) {
                                        room.copy(openTask = optimisticTransform(open))
                                    } else room
                                },
                                actionNotice = queuedMsg,
                            )
                        }
                        _effects.trySend(HousekeepingEffect.ShowMessage(queuedMsg))
                    } else {
                        _uiState.update { it.copy(isMutating = false, actionError = err.message) }
                    }
                }
        }
    }

    private fun roomPriorityRank(status: String): Int = when (status.lowercase()) {
        "dirty", "reserved_dirty" -> 0
        "cleaning" -> 1
        "maintenance", "out_of_order" -> 2
        "occupied" -> 3
        "reserved" -> 4
        else -> 5
    }

    companion object {
        private const val BOARD_CACHE_KEY = "housekeeping_board"
    }
}
