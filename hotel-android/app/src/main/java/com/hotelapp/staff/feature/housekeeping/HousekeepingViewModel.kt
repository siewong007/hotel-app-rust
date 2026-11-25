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
    val staff: List<AssignableStaffMemberDto> = emptyList(),
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
        _uiState.update { it.copy(taskFilter = filter) }
        refresh()
    }

    fun clearNotice() {
        _uiState.update { it.copy(actionNotice = null) }
    }

    fun clearSheetError() {
        _uiState.update { it.copy(sheetError = null) }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            val snapshot = _uiState.value
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = it.boardRooms.isEmpty() && it.tasks.isEmpty(),
                    isRefreshing = it.boardRooms.isNotEmpty() || it.tasks.isNotEmpty(),
                    error = null,
                    actionError = null,
                )
            }
            val boardDef = async { repository.board() }
            val (statusParam, unassignedParam) = when (snapshot.taskFilter) {
                "unassigned" -> null to true
                "all" -> null to null
                else -> snapshot.taskFilter to null
            }
            val tasksDef = async { repository.tasks(status = statusParam, unassigned = unassignedParam) }

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
                val resolvedRooms = (boardRes.getOrNull() ?: cachedBoard)?.rooms?.sortedWith(
                    compareBy<HousekeepingBoardRoomDto> { roomPriorityRank(it.status) }.thenBy { it.roomNumber },
                ) ?: cur.boardRooms
                cur.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isShowingCachedData = boardRes.isFailure && (cachedBoard != null || cur.boardRooms.isNotEmpty()),
                    boardRooms = resolvedRooms,
                    tasks = tasksRes.getOrNull()?.items ?: cur.tasks,
                    error = if (resolvedRooms.isEmpty() && cur.tasks.isEmpty()) firstErr?.message else null,
                )
            }
        }
    }

    private fun loadAssignableStaff() {
        viewModelScope.launch {
            repository.assignableStaff("housekeeping").onSuccess { staff ->
                _uiState.update { it.copy(staff = staff) }
            }
        }
    }

    fun updateTaskStatus(taskId: Long, newStatus: String) {
        val label = StatusFormatter.humanize(newStatus)
        val req = UpdateHousekeepingTaskRequestDto(status = newStatus)
        mutate(
            notice = "Task marked $label.",
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
        val notice = if (staffId == null) "Task unassigned." else "Task assigned."
        mutate(
            notice = notice,
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
        notice: String,
        offlineFallback: (suspend () -> Unit)? = null,
        block: suspend () -> Result<HousekeepingTaskDto>,
    ) {
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
                        _uiState.update { it.copy(isMutating = false, actionNotice = queuedMsg) }
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
