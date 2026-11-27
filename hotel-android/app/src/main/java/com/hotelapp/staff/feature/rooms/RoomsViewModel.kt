package com.hotelapp.staff.feature.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.network.ApiException
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.sync.OfflineOpsCoordinator
import com.hotelapp.staff.data.dto.RoomDetailedStatusDto
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto
import com.hotelapp.staff.data.repository.RoomsRepository
import kotlinx.coroutines.Job
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
    val isDetailOfflineFallback: Boolean = false,
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

    private var refreshJob: Job? = null
    private var detailJob: Job? = null

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
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = it.rooms.isEmpty(),
                    isRefreshing = it.rooms.isNotEmpty(),
                    error = null,
                )
            }
            repository.roomsWithOccupancy()
                .onSuccess { list ->
                    val sorted = list.sortedWith { a, b -> compareRoomNumbersNatural(a.roomNumber, b.roomNumber) }
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
                                rooms = cached.sortedWith { a, b -> compareRoomNumbersNatural(a.roomNumber, b.roomNumber) },
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
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedRoomDetail = null,
                    isDetailOfflineFallback = false,
                    isLoadingDetail = true,
                    sheetError = null,
                )
            }
            repository.roomDetail(roomId)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            selectedRoomDetail = detail,
                            isDetailOfflineFallback = false,
                        )
                    }
                }
                .onFailure { err ->
                    val isForbidden = err is ApiException && err.statusCode == 403
                    val isOffline = offlineOps?.isQueueableNetworkFailure(err)
                        ?: (err !is ApiException || err.statusCode == null)
                    val summaryRoom = if (isForbidden || isOffline) {
                        _uiState.value.rooms.find { it.id == roomId }
                    } else null
                    if (summaryRoom != null) {
                        val fallback = RoomDetailedStatusDto(
                            id = summaryRoom.id,
                            roomNumber = summaryRoom.roomNumber,
                            roomType = summaryRoom.roomType,
                            status = if (summaryRoom.isOccupied) "occupied" else (summaryRoom.status ?: "available"),
                            available = summaryRoom.available,
                            statusNotes = summaryRoom.notes,
                        )
                        _uiState.update {
                            it.copy(
                                isLoadingDetail = false,
                                selectedRoomDetail = fallback,
                                isDetailOfflineFallback = !isForbidden,
                                sheetError = null,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoadingDetail = false,
                                isDetailOfflineFallback = false,
                                sheetError = err.message,
                            )
                        }
                    }
                }
        }
    }

    fun dismissDetail() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedRoomDetail = null,
                isDetailOfflineFallback = false,
                sheetError = null,
                isLoadingDetail = false,
            )
        }
    }

    fun endCleaning(roomId: Long) {
        mutateRoom(
            roomId = roomId,
            notice = "Room cleaning marked complete.",
            optimisticStatus = "available",
            optimisticNotes = null,
            offlineFallback = { offlineOps?.enqueueRoomEndCleaning(roomId) },
        ) { repository.endCleaning(roomId) }
    }

    fun endMaintenance(roomId: Long) {
        mutateRoom(
            roomId = roomId,
            notice = "Room maintenance completed.",
            optimisticStatus = "available",
            optimisticNotes = null,
            offlineFallback = { offlineOps?.enqueueRoomEndMaintenance(roomId) },
        ) { repository.endMaintenance(roomId) }
    }

    fun updateRoomStatus(roomId: Long, status: String, notes: String?) {
        mutateRoom(
            roomId = roomId,
            notice = "Room marked ${StatusFormatter.humanize(status)}.",
            optimisticStatus = status,
            optimisticNotes = notes?.trim()?.takeIf { it.isNotEmpty() },
            offlineFallback = { offlineOps?.enqueueRoomStatusUpdate(roomId, status, notes) },
        ) {
            repository.updateStatus(roomId, status, notes)
        }
    }

    private fun mutateRoom(
        roomId: Long,
        notice: String,
        optimisticStatus: String? = null,
        optimisticNotes: String? = null,
        offlineFallback: (suspend () -> Unit)? = null,
        action: suspend () -> Result<Unit>,
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, sheetError = null) }
            action()
                .onSuccess {
                    _uiState.update { cur ->
                        cur.copy(
                            isMutating = false,
                            actionNotice = notice,
                            rooms = applyOptimisticRoomUpdate(cur.rooms, roomId, optimisticStatus, optimisticNotes),
                            selectedRoomDetail = cur.selectedRoomDetail?.let { d ->
                                if (d.id == roomId && optimisticStatus != null) {
                                    d.copy(
                                        status = optimisticStatus,
                                        available = optimisticStatus == "available",
                                        statusNotes = optimisticNotes ?: d.statusNotes,
                                    )
                                } else d
                            },
                        )
                    }
                    _effects.trySend(RoomsEffect.ShowMessage(notice))
                    refresh(silent = true)
                    openRoomDetail(roomId)
                }
                .onFailure { err ->
                    if (offlineFallback != null && offlineOps?.isQueueableNetworkFailure(err) == true) {
                        offlineFallback()
                        val queuedMsg = "Offline — room update queued for sync."
                        _uiState.update { cur ->
                            cur.copy(
                                isMutating = false,
                                selectedRoomDetail = null,
                                isDetailOfflineFallback = false,
                                rooms = applyOptimisticRoomUpdate(cur.rooms, roomId, optimisticStatus, optimisticNotes),
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

    private fun applyOptimisticRoomUpdate(
        rooms: List<RoomWithOccupancyDto>,
        roomId: Long,
        status: String?,
        notes: String?,
    ): List<RoomWithOccupancyDto> {
        if (status == null) return rooms
        return rooms.map { room ->
            if (room.id != roomId) {
                room
            } else {
                room.copy(
                    status = status,
                    available = !room.isOccupied && status == "available",
                    notes = notes ?: room.notes,
                )
            }
        }
    }

    companion object {
        private const val CACHE_KEY = "rooms_with_occupancy"
        private val ROOM_TOKEN_REGEX = Regex("""(\d+|\D+)""")

        internal fun compareRoomNumbersNatural(a: String, b: String): Int {
            val tokensA = ROOM_TOKEN_REGEX.findAll(a.trim()).map { it.value }.toList()
            val tokensB = ROOM_TOKEN_REGEX.findAll(b.trim()).map { it.value }.toList()
            val count = minOf(tokensA.size, tokensB.size)
            for (i in 0 until count) {
                val ta = tokensA[i]
                val tb = tokensB[i]
                val na = ta.toLongOrNull()
                val nb = tb.toLongOrNull()
                val cmp = if (na != null && nb != null) {
                    na.compareTo(nb)
                } else {
                    ta.compareTo(tb, ignoreCase = true)
                }
                if (cmp != 0) return cmp
            }
            return tokensA.size.compareTo(tokensB.size)
        }
    }
}
