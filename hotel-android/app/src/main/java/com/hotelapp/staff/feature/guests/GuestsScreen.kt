package com.hotelapp.staff.feature.guests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.HotelAvatar
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSearchBar
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.ListCardSkeleton
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.StatusFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuestsScreen(
    viewModel: GuestsViewModel,
    onBack: () -> Unit,
    onOpenGuestDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HotelTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.surfaceApp,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Guest Directory", color = colors.textPrimary) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            HotelSearchBar(
                query = state.search,
                onQueryChange = viewModel::setSearch,
                placeholder = "Search name, nickname, email, phone",
            )
            if (!state.isLoading) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (state.total > state.items.size) {
                        "Showing ${state.items.size} of ${state.total} guests"
                    } else {
                        "${state.total} guest${if (state.total == 1L) "" else "s"}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            when {
                state.isLoading -> ListCardSkeleton()
                state.error != null && state.items.isEmpty() -> ErrorStateView(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                )
                else -> HotelPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.items.isEmpty()) {
                        EmptyStateView(
                            title = "No guests found",
                            subtitle = "Try another name, email, or phone number.",
                            icon = Icons.Outlined.PersonSearch,
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.items, key = { it.id }) { guest ->
                                HotelCard(
                                    onClick = { onOpenGuestDetail(guest.id) },
                                    accentColor = when {
                                        guest.isBlacklisted == true -> colors.danger.fg
                                        !guest.vipStatus.isNullOrBlank() && guest.vipStatus != "none" -> colors.warning.fg
                                        else -> null
                                    },
                                    modifier = Modifier.animateItem(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        HotelAvatar(
                                            name = guest.displayName(),
                                            size = 42.dp,
                                            tone = if (guest.isBlacklisted == true) colors.danger else null,
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = guest.displayName(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = colors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    if (guest.isBlacklisted == true) {
                                                        StatusPill("Blacklisted", colors.danger)
                                                    }
                                                    if (!guest.vipStatus.isNullOrBlank() && guest.vipStatus != "none") {
                                                        val vipLabel = if (guest.vipStatus.equals("vip", ignoreCase = true)) {
                                                            "VIP"
                                                        } else {
                                                            "VIP ${StatusFormatter.humanize(guest.vipStatus)}"
                                                        }
                                                        StatusPill(vipLabel, colors.warning)
                                                    }
                                                }
                                            }
                                            val contact = listOfNotNull(guest.phone, guest.email).joinToString(" · ")
                                            if (contact.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = contact,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val lastStay = guest.lastStayDate?.let { " · Last ${DateFormatter.formatDate(it)}" }.orEmpty()
                                            Text(
                                                text = "${guest.bookingsCount ?: 0} stay(s) · ${StatusFormatter.humanize(guest.guestType)}$lastStay",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            if (state.items.size < state.total) {
                                item(key = "guests_load_more") {
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
                                            text = "Load More (${state.items.size}/${state.total})",
                                            onClick = viewModel::loadMore,
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
