package com.hotelapp.staff.feature.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.sync.OfflineOpsCoordinator
import com.hotelapp.staff.data.dto.AssignableStaffMemberDto
import com.hotelapp.staff.data.dto.CreateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.MaintenanceTicketDto
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto
import com.hotelapp.staff.data.dto.UpdateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.repository.HousekeepingRepository
import com.hotelapp.staff.data.repository.MaintenanceRepository
import com.hotelapp.staff.data.repository.RoomsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MaintenanceEffect {
    data class ShowMessage(val message: String, val isError: Boolean = false) : MaintenanceEffect
}

data class MaintenanceUiState(
    val searchQuery: String = "",
    val statusFilter: String = "all",
    val priorityFilter: String = "all",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val tickets: List<MaintenanceTicketDto> = emptyList(),
    val rooms: List<RoomWithOccupancyDto> = emptyList(),
    val staff: List<AssignableStaffMemberDto> = emptyList(),
    val selectedTicket: MaintenanceTicketDto? = null,
    val error: String? = null,
    val sheetError: String? = null,
    val actionNotice: String? = null,
)

class MaintenanceViewModel(
    private val session: StaffSession,
    private val repository: MaintenanceRepository,
    private val housekeepingRepository: HousekeepingRepository,
    private val roomsRepository: RoomsRepository,
    realtimeSocketClient: RealtimeSocketClient,
    private val offlineOps: OfflineOpsCoordinator? = null,
) : ViewModel() {

    private val canWrite = session.hasAnyPermission("maintenance:write", "maintenance:manage")

    private val _uiState = MutableStateFlow(MaintenanceUiState())
    val uiState: StateFlow<MaintenanceUiState> = _uiState.asStateFlow()

    private val _effects = Channel<MaintenanceEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refresh()
        if (canWrite) {
            loadPickers()
        }
        viewModelScope.launch {
            realtimeSocketClient.domainEvents.collect { domain ->
                if (domain == "maintenance" || domain == "rooms") {
                    refresh(silent = true)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setStatusFilter(status: String) {
        if (_uiState.value.statusFilter == status) return
        _uiState.update { it.copy(statusFilter = status) }
        refresh()
    }

    fun setPriorityFilter(priority: String) {
        if (_uiState.value.priorityFilter == priority) return
        _uiState.update { it.copy(priorityFilter = priority) }
        refresh()
    }

    fun selectTicket(ticket: MaintenanceTicketDto?) {
        _uiState.update { it.copy(selectedTicket = ticket, sheetError = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(actionNotice = null) }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = it.tickets.isEmpty(),
                    isRefreshing = it.tickets.isNotEmpty(),
                    error = null,
                )
            }
            repository.list(
                status = s.statusFilter.takeIf { it != "all" },
                priority = s.priorityFilter.takeIf { it != "all" },
            ).onSuccess { res ->
                val updatedSelected = s.selectedTicket?.let { sel ->
                    res.items.find { it.id == sel.id } ?: sel
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        tickets = res.items,
                        selectedTicket = updatedSelected,
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (silent && it.tickets.isNotEmpty()) it.error else err.message,
                    )
                }
            }
        }
    }

    private fun loadPickers() {
        viewModelScope.launch {
            housekeepingRepository.assignableStaff("maintenance").onSuccess { staff ->
                _uiState.update { it.copy(staff = staff) }
            }
            if (session.hasPermission("rooms:read")) {
                roomsRepository.roomsWithOccupancy().onSuccess { rooms ->
                    _uiState.update { it.copy(rooms = rooms.sortedBy { r -> r.roomNumber }) }
                }
            }
        }
    }

    fun updateTicketStatus(ticketId: Long, nextStatus: String, resolutionNotes: String?) {
        mutate(notice = "Ticket marked ${StatusFormatter.humanize(nextStatus)}.") {
            repository.update(
                ticketId,
                UpdateMaintenanceTicketRequestDto(
                    status = nextStatus,
                    resolutionNotes = resolutionNotes?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }
    }

    fun assignTicket(ticketId: Long, staffId: Long?) {
        val req = if (staffId == null) {
            UpdateMaintenanceTicketRequestDto(clearAssignee = true)
        } else {
            UpdateMaintenanceTicketRequestDto(assignedTo = staffId)
        }
        val notice = if (staffId == null) "Ticket unassigned." else "Ticket assigned."
        mutate(notice = notice) { repository.update(ticketId, req) }
    }

    fun createTicket(
        roomId: Long?,
        title: String,
        category: String,
        priority: String,
        description: String,
        onSuccess: () -> Unit,
    ) {
        if (title.isBlank()) {
            _uiState.update { it.copy(sheetError = "Title is required.") }
            return
        }
        viewModelScope.launch {
            val request = CreateMaintenanceTicketRequestDto(
                roomId = roomId,
                title = title.trim(),
                category = category,
                priority = priority,
                description = description.trim().takeIf { it.isNotEmpty() },
            )
            _uiState.update { it.copy(isMutating = true, sheetError = null) }
            repository.create(request).onSuccess {
                val msg = "Maintenance ticket created."
                _uiState.update { it.copy(isMutating = false, actionNotice = msg) }
                _effects.trySend(MaintenanceEffect.ShowMessage(msg))
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                if (offlineOps?.isQueueableNetworkFailure(err) == true) {
                    offlineOps.enqueueMaintenanceCreate(request)
                    val queuedMsg = "Offline — maintenance ticket queued for sync."
                    _uiState.update { it.copy(isMutating = false, actionNotice = queuedMsg) }
                    _effects.trySend(MaintenanceEffect.ShowMessage(queuedMsg))
                    onSuccess()
                } else {
                    _uiState.update { it.copy(isMutating = false, sheetError = err.message) }
                }
            }
        }
    }

    private fun mutate(notice: String, block: suspend () -> Result<MaintenanceTicketDto>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, sheetError = null) }
            block()
                .onSuccess { updated ->
                    _uiState.update { it.copy(isMutating = false, selectedTicket = updated, actionNotice = notice) }
                    _effects.trySend(MaintenanceEffect.ShowMessage(notice))
                    refresh(silent = true)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isMutating = false, sheetError = err.message) }
                }
        }
    }
}
