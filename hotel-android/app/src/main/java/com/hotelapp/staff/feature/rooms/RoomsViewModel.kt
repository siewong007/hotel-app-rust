package com.hotelapp.staff.feature.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.sync.OfflineOpsCoordinator
import com.hotelapp.staff.data.dto.RoomDetailedStatusDto
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto
import com.hotelapp.staff.data.repository.RoomsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

sealed interface RoomsEffect {
    data class ShowMessage(val message: String, val isError: Boolean = false) : RoomsEffect
}

data class RoomsUiState(
    val search: String = "",
    val statusFilter: String = "all",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isShowingCachedData: Boolean = false,
    val rooms: List<RoomWithOccupancyDto> = emptyList(),
    val selectedRoomDetail: RoomDetailedStatusDto? = null,
    val isLoadingDetail: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
    val sheetError: String? = null,
    val actionNotice: String? = null,
)

class RoomsViewModel(
    private val repository: RoomsRepository,
    realtimeSocketClient: RealtimeSocketClient,
    private val offlineOps: OfflineOpsCoordinator? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomsUiState())
    val uiState: StateFlow<RoomsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<RoomsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refresh()
        viewModelScope.launch {
            realtimeSocketClient.domainEvents.collect { domain ->
                if (domain == "rooms" || domain == "housekeeping" || domain == "bookings") {
                    refresh(silent = true)
                }
            }
        }
    }

    fun setSearch(value: String) {
        _uiState.update { it.copy(search = value) }
    }

    fun setStatusFilter(status: String) {
        _uiState.update { it.copy(statusFilter = status) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(actionNotice = null) }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = it.rooms.isEmpty(),
                    isRefreshing = it.rooms.isNotEmpty(),
                    error = null,
                )
            }
            repository.roomsWithOccupancy()
                .onSuccess { list ->
                    val sorted = list.sortedBy { r -> r.roomNumber }
                    offlineOps?.saveCache(CACHE_KEY, ListSerializer(RoomWithOccupancyDto.serializer()), sorted)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isShowingCachedData = false,
                            rooms = sorted,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    val cached = if (_uiState.value.rooms.isEmpty()) {
                        offlineOps?.loadCache(CACHE_KEY, ListSerializer(RoomWithOccupancyDto.serializer()))
                    } else null
                    _uiState.update {
                        if (cached != null) {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isShowingCachedData = true,
                                rooms = cached,
                                error = null,
                            )
                        } else {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isShowingCachedData = it.rooms.isNotEmpty(),
                                error = if (silent && it.rooms.isNotEmpty()) it.error else err.message,
                            )
                        }
                    }
                }
        }
    }

    fun openRoomDetail(roomId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedRoomDetail = null, isLoadingDetail = true, sheetError = null) }
            repository.roomDetail(roomId)
                .onSuccess { detail ->
                    _uiState.update { it.copy(isLoadingDetail = false, selectedRoomDetail = detail) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingDetail = false, sheetError = err.message) }
                }
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(selectedRoomDetail = null, sheetError = null, isLoadingDetail = false) }
    }

    fun endCleaning(roomId: Long) {
        mutateRoom(
            roomId = roomId,
            notice = "Room cleaning marked complete.",
            offlineFallback = { offlineOps?.enqueueRoomEndCleaning(roomId) },
        ) { repository.endCleaning(roomId) }
    }

    fun endMaintenance(roomId: Long) {
        mutateRoom(roomId, notice = "Room maintenance completed.") { repository.endMaintenance(roomId) }
    }

    fun updateRoomStatus(roomId: Long, status: String, notes: String?) {
        mutateRoom(
            roomId = roomId,
            notice = "Room marked ${StatusFormatter.humanize(status)}.",
            offlineFallback = { offlineOps?.enqueueRoomStatusUpdate(roomId, status, notes) },
        ) {
            repository.updateStatus(roomId, status, notes)
        }
    }

    private fun mutateRoom(
        roomId: Long,
        notice: String,
        offlineFallback: (suspend () -> Unit)? = null,
        action: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, sheetError = null) }
            action()
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, actionNotice = notice) }
                    _effects.trySend(RoomsEffect.ShowMessage(notice))
                    refresh(silent = true)
                    openRoomDetail(roomId)
                }
                .onFailure { err ->
                    if (offlineFallback != null && offlineOps?.isQueueableNetworkFailure(err) == true) {
                        offlineFallback()
                        val queuedMsg = "Offline — room update queued for sync."
                        _uiState.update {
                            it.copy(
                                isMutating = false,
                                selectedRoomDetail = null,
                                actionNotice = queuedMsg,
                            )
                        }
                        _effects.trySend(RoomsEffect.ShowMessage(queuedMsg))
                    } else {
                        _uiState.update { it.copy(isMutating = false, sheetError = err.message) }
                    }
                }
        }
    }

    companion object {
        private const val CACHE_KEY = "rooms_with_occupancy"
    }
}
