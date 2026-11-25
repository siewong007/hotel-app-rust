package com.hotelapp.staff.feature.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.BookingListSkeleton
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSearchBar
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.repository.BookingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class BookingsUiState(
    val search: String = "",
    val view: String = "all",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<BookingWithDetailsDto> = emptyList(),
    val page: Long = 1,
    val total: Long = 0,
    val error: String? = null,
)

class BookingsViewModel(
    initialView: String,
    private val repository: BookingsRepository,
    realtimeSocketClient: RealtimeSocketClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingsUiState(view = initialView))
    val uiState: StateFlow<BookingsUiState> = _uiState.asStateFlow()

    private var searchDebounceJob: Job? = null

    init {
        load(page = 1, replace = true)
        viewModelScope.launch {
            realtimeSocketClient.domainEvents.collect { domain ->
                if (domain == "bookings" || domain == "payments") {
                    load(page = 1, replace = true, silent = true)
                }
            }
        }
    }

    fun setView(view: String) {
        if (_uiState.value.view == view) return
        _uiState.update { it.copy(view = view) }
        load(page = 1, replace = true)
    }

    fun setSearch(query: String) {
        _uiState.update { it.copy(search = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(320)
            load(page = 1, replace = true)
        }
    }

    fun clearFilters() {
        searchDebounceJob?.cancel()
        _uiState.update { it.copy(search = "", view = "all") }
        load(page = 1, replace = true)
    }

    fun refresh(silent: Boolean = false) {
        load(page = 1, replace = true, silent = silent)
    }

    fun loadMore() {
        val snapshot = _uiState.value
        if (snapshot.isLoadingMore || snapshot.items.size >= snapshot.total) return
        load(page = snapshot.page + 1, replace = false)
    }

    private fun load(page: Long, replace: Boolean, silent: Boolean = false) {
        viewModelScope.launch {
            val snapshot = _uiState.value
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = replace && it.items.isEmpty(),
                    isRefreshing = replace && it.items.isNotEmpty(),
                    isLoadingMore = !replace,
                    error = null,
                )
            }
            repository.list(
                page = page,
                pageSize = 25,
                search = snapshot.search,
                view = snapshot.view,
            ).onSuccess { res ->
                _uiState.update { cur ->
                    cur.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = if (replace) res.data else (cur.items + res.data).distinctBy { it.id },
                        page = res.page,
                        total = res.total,
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update { cur ->
                    cur.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = if (silent && cur.items.isNotEmpty()) cur.error else err.message,
                    )
                }
            }
        }
    }
}

private val BOOKING_VIEWS = listOf(
    FilterOption("all", "All"),
    FilterOption("arriving", "Arriving"),
    FilterOption("in_house", "In House"),
    FilterOption("departing", "Departing"),
    FilterOption("upcoming", "Upcoming"),
    FilterOption("balance", "Balance Due"),
)

@Composable
fun BookingsScreen(
    viewModel: BookingsViewModel,
    serverConfigStore: ServerConfigStore,
    onOpenBookingDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    canCreateBooking: Boolean = false,
    onOpenCreateBooking: () -> Unit = {},
) {
    val colors = HotelTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(state.items.size, state.total, state.isLoadingMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.items.isNotEmpty() &&
                state.items.size < state.total &&
                !state.isLoadingMore &&
                lastVisible >= state.items.size - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    OnResumeRefreshEffect { viewModel.refresh(silent = true) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.surfaceApp,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (canCreateBooking) {
                FloatingActionButton(
                    onClick = onOpenCreateBooking,
                    containerColor = colors.primaryMain,
                    contentColor = colors.primaryContrastText,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "New Booking")
                }
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp),
        ) {
        Spacer(modifier = Modifier.height(10.dp))
        HotelSearchBar(
            query = state.search,
            onQueryChange = viewModel::setSearch,
            placeholder = "Search guest, booking #, room #",
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilterChipRow(
            options = BOOKING_VIEWS,
            selected = state.view,
            onSelect = viewModel::setView,
        )

        if (!state.isLoading) {
            val hasFilter = state.search.isNotBlank() || state.view != "all"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val summaryLabel = if (state.total > state.items.size) {
                    "Showing ${state.items.size} of ${state.total} stays"
                } else {
                    "${state.total} stay${if (state.total == 1L) "" else "s"}"
                }
                Text(
                    text = summaryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
                if (hasFilter) {
                    TextButton(
                        onClick = viewModel::clearFilters,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primaryOnSurface,
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        when {
            state.isLoading -> BookingListSkeleton()
            state.error != null && state.items.isEmpty() -> ErrorStateView(
                message = state.error!!,
                onRetry = viewModel::refresh,
            )
            else -> HotelPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.items.isEmpty()) {
                    EmptyStateView(
                        title = "No matching bookings",
                        subtitle = "Try another filter or search query.",
                        icon = Icons.Outlined.EventBusy,
                        actionLabel = if (state.search.isNotBlank() || state.view != "all") "Reset Filters" else null,
                        onAction = if (state.search.isNotBlank() || state.view != "all") viewModel::clearFilters else null,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = if (canCreateBooking) 88.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.items, key = { it.id }) { booking ->
                            BookingListCard(
                                booking = booking,
                                currency = config.currency,
                                onClick = { onOpenBookingDetail(booking.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (state.items.size < state.total) {
                            item(key = "load_more_footer") {
                                if (state.isLoadingMore) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.primaryMain,
                                        )
                                    }
                                } else {
                                    HotelOutlinedButton(
                                        text = "Load More (${state.items.size} / ${state.total})",
                                        onClick = viewModel::loadMore,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingListCard(
    booking: BookingWithDetailsDto,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HotelTheme.colors
    val balanceDue = booking.balanceDue ?: BigDecimal.ZERO
    val statusTone = StatusFormatter.bookingTone(booking.status, colors)

    HotelCard(
        modifier = modifier,
        onClick = onClick,
        accentColor = statusTone.fg,
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Rm ${booking.roomNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = booking.bookingNumber,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primaryOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                label = StatusFormatter.humanize(booking.status),
                tone = statusTone,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = booking.guestName,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${booking.roomType} · ${DateFormatter.formatDateRangeWithNights(booking.checkInDate, booking.checkOutDate)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                booking.paymentStatus?.let { payStatus ->
                    StatusPill(
                        label = StatusFormatter.humanize(payStatus),
                        tone = StatusFormatter.paymentTone(payStatus, colors),
                    )
                }
                if (!booking.companyName.isNullOrBlank()) {
                    StatusPill(label = booking.companyName, tone = colors.neutral)
                } else if (!booking.bookingChannelName.isNullOrBlank()) {
                    StatusPill(label = booking.bookingChannelName, tone = colors.neutral)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MoneyFormatter.format(booking.totalAmount, currency),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
                if (balanceDue > BigDecimal.ZERO) {
                    Text(
                        text = "Due ${MoneyFormatter.format(balanceDue, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.danger.fg,
                    )
                }
            }
        }
    }
}
