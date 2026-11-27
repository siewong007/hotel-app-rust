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
import com.hotelapp.staff.feature.rooms.RoomsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val isMutating: Boolean = false,
    val tickets: List<MaintenanceTicketDto> = emptyList(),
    val page: Long = 1,
    val total: Long = 0,
    val rooms: List<RoomWithOccupancyDto> = emptyList(),
    val staff: List<AssignableStaffMemberDto> = emptyList(),
    val isLoadingPickers: Boolean = false,
    val pickerError: String? = null,
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

    private var refreshJob: Job? = null

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
        _uiState.update { it.copy(statusFilter = status, endReached = false) }
        refresh()
    }

    fun setPriorityFilter(priority: String) {
        if (_uiState.value.priorityFilter == priority) return
        _uiState.update { it.copy(priorityFilter = priority, endReached = false) }
        refresh()
    }

    fun selectTicket(ticket: MaintenanceTicketDto?) {
        _uiState.update { it.copy(selectedTicket = ticket, sheetError = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(actionNotice = null) }
    }

    fun clearSheetError() {
        _uiState.update { it.copy(sheetError = null) }
    }

    fun retryPickers() {
        if (canWrite) loadPickers()
    }

    fun refresh(silent: Boolean = false) {
        if (canWrite && (_uiState.value.staff.isEmpty() || _uiState.value.rooms.isEmpty())) {
            loadPickers()
        }
        load(page = 1, replace = true, silent = silent)
    }

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoadingMore || s.endReached || s.tickets.size >= s.total) return
        load(page = s.page + 1, replace = false, silent = false)
    }

    private fun load(page: Long, replace: Boolean, silent: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val s = _uiState.value
            val statusAtDispatch = s.statusFilter
            val priorityAtDispatch = s.priorityFilter
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = replace && it.tickets.isEmpty(),
                    isRefreshing = replace && it.tickets.isNotEmpty(),
                    isLoadingMore = !replace,
                    endReached = if (replace) false else it.endReached,
                    error = null,
                )
            }
            repository.list(
                status = statusAtDispatch.takeIf { it != "all" },
                priority = priorityAtDispatch.takeIf { it != "all" },
                page = page,
            ).onSuccess { res ->
                _uiState.update { cur ->
                    if (cur.statusFilter != statusAtDispatch || cur.priorityFilter != priorityAtDispatch) {
                        return@update cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                        )
                    }
                    val preservePagedScroll = silent && replace && cur.page > 1 && cur.tickets.isNotEmpty()
                    val nextTickets = when {
                        preservePagedScroll -> {
                            val updatedById = res.items.associateBy { it.id }
                            (res.items + cur.tickets.filter { it.id !in updatedById }).distinctBy { it.id }
                        }
                        replace -> res.items
                        else -> (cur.tickets + res.items).distinctBy { it.id }
                    }
                    val updatedSelected = cur.selectedTicket?.let { sel ->
                        nextTickets.find { it.id == sel.id } ?: sel
                    }
                    val nextPage = if (preservePagedScroll) cur.page else res.page
                    val reachedEnd = if (preservePagedScroll) {
                        nextTickets.size >= res.total || cur.endReached
                    } else {
                        res.items.isEmpty() ||
                            nextTickets.size >= res.total ||
                            (!replace && nextTickets.size == cur.tickets.size)
                    }
                    cur.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        endReached = reachedEnd,
                        tickets = nextTickets,
                        page = nextPage,
                        total = res.total,
                        selectedTicket = updatedSelected,
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update { cur ->
                    if (cur.statusFilter != statusAtDispatch || cur.priorityFilter != priorityAtDispatch) {
                        return@update cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                        )
                    }
                    cur.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = if (silent && cur.tickets.isNotEmpty()) cur.error else err.message,
                    )
                }
            }
        }
    }

    private fun loadPickers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPickers = true, pickerError = null) }
            coroutineScope {
                val staffDef = async { housekeepingRepository.assignableStaff("maintenance") }
                val roomsDef = if (session.hasPermission("rooms:read")) {
                    async { roomsRepository.roomsWithOccupancy() }
                } else null
                val staffRes = staffDef.await()
                val roomsRes = roomsDef?.await()
                val firstErr = staffRes.exceptionOrNull() ?: roomsRes?.exceptionOrNull()
                _uiState.update { cur ->
                    cur.copy(
                        isLoadingPickers = false,
                        staff = staffRes.getOrNull() ?: cur.staff,
                        rooms = roomsRes?.getOrNull()?.sortedWith { a, b ->
                            RoomsViewModel.compareRoomNumbersNatural(a.roomNumber, b.roomNumber)
                        } ?: cur.rooms,
                        pickerError = if (staffRes.isFailure || roomsRes?.isFailure == true) {
                            firstErr?.message ?: "Unable to load room/staff options."
                        } else null,
                    )
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
        idempotencyKey: String? = null,
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isMutating) return
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
            repository.create(request, idempotencyKey = idempotencyKey).onSuccess {
                val msg = "Maintenance ticket created."
                _uiState.update { it.copy(isMutating = false, actionNotice = msg) }
                _effects.trySend(MaintenanceEffect.ShowMessage(msg))
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                if (offlineOps?.isQueueableNetworkFailure(err) == true) {
                    offlineOps.enqueueMaintenanceCreate(request, idempotencyKey = idempotencyKey)
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
        if (_uiState.value.isMutating) return
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
