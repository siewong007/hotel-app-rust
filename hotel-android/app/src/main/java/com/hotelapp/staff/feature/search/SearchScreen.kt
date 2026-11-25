package com.hotelapp.staff.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KingBed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.rememberPlatformHaptics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    serverConfigStore: ServerConfigStore,
    onBack: () -> Unit,
    onOpenBookingDetail: (Long) -> Unit,
    onOpenGuestDetail: (Long) -> Unit,
    onOpenRooms: () -> Unit,
    session: StaffSession? = null,
    onShowMessage: (String) -> Unit = {},
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config by serverConfigStore.config.collectAsStateWithLifecycle()

    val canSearchBookings = session?.hasPermission("bookings:read") ?: true
    val canSearchGuests = session?.hasPermission("guests:read") ?: true
    val canSearchRooms = session?.hasPermission("rooms:read") ?: true
    val canSearchLedgers = session?.hasPermission("ledgers:read") ?: true

    val searchScopes = remember(canSearchBookings, canSearchGuests, canSearchRooms, canSearchLedgers) {
        buildList {
            add(FilterOption("all", "All"))
            if (canSearchBookings) add(FilterOption("bookings", "Bookings"))
            if (canSearchGuests) add(FilterOption("guests", "Guests"))
            if (canSearchRooms) add(FilterOption("rooms", "Rooms"))
            if (canSearchLedgers) add(FilterOption("ledgers", "Ledgers"))
        }
    }
    val placeholderText = remember(canSearchBookings, canSearchGuests, canSearchRooms, canSearchLedgers) {
        val parts = buildList {
            if (canSearchBookings) add("bookings")
            if (canSearchGuests) add("guests")
            if (canSearchRooms) add("rooms")
            if (canSearchLedgers) add("ledgers")
        }
        if (parts.isEmpty()) "Search property records..." else "Search ${parts.joinToString(", ")}..."
    }

    Scaffold(
        containerColor = colors.surfaceApp,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = state.query,
                            onQueryChange = viewModel::setQuery,
                            onSearch = { viewModel.submitSearch() },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = {
                                Text(
                                    text = placeholderText,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colors.textPrimary,
                                    )
                                }
                            },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear search",
                                            tint = colors.textMuted,
                                        )
                                    }
                                }
                            },
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    colors = SearchBarDefaults.colors(containerColor = colors.surfaceBase),
                    modifier = Modifier.fillMaxWidth(),
                ) {}
                if (searchScopes.size > 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilterChipRow(
                        options = searchScopes,
                        selected = state.selectedType,
                        onSelect = viewModel::setType,
                    )
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
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.primaryMain,
                    trackColor = colors.surfaceRaised,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            when {
                state.error != null -> ErrorStateView(
                    message = state.error!!,
                    onRetry = viewModel::submitSearch,
                )
                state.query.trim().length < 2 -> {
                    if (config.recentSearches.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            TextButton(onClick = viewModel::clearRecentSearches) {
                                Text("Clear", color = colors.primaryOnSurface)
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            config.recentSearches.forEach { term ->
                                AssistChip(
                                    onClick = {
                                        haptics.tick()
                                        viewModel.applyRecentQuery(term)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = { Text(term) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    EmptyStateView(
                        title = "Command Search",
                        subtitle = "Type at least 2 characters to search bookings, guests, rooms, and company ledgers.",
                        icon = Icons.Outlined.Search,
                    )
                }
                state.hasSearched && state.groups.isEmpty() && !state.isLoading -> {
                    EmptyStateView(
                        title = "No matching records",
                        subtitle = "Try another booking #, guest name, phone, or room number.",
                        icon = Icons.Outlined.Search,
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.groups, key = { it.type }) { group ->
                            val icon = when (group.type) {
                                "bookings" -> Icons.Outlined.CalendarMonth
                                "guests" -> Icons.Outlined.Group
                                "rooms" -> Icons.Outlined.KingBed
                                else -> Icons.Outlined.AccountBalance
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${group.label} (${group.results.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textSecondary,
                                )
                                HotelCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                                    group.results.forEachIndexed { idx, hit ->
                                        if (idx > 0) {
                                            HorizontalDivider(
                                                color = colors.borderSubtle,
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                            )
                                        }
                                        val canOpenGroup = when (group.type) {
                                            "bookings" -> canSearchBookings
                                            "guests" -> canSearchGuests
                                            "rooms" -> canSearchRooms
                                            else -> false
                                        }
                                        ListItem(
                                            modifier = Modifier.clickable {
                                                haptics.click()
                                                viewModel.saveCurrentSearchToHistory()
                                                when (group.type) {
                                                    "bookings" -> if (canSearchBookings) onOpenBookingDetail(hit.id)
                                                    "guests" -> if (canSearchGuests) onOpenGuestDetail(hit.id)
                                                    "rooms" -> if (canSearchRooms) onOpenRooms()
                                                    else -> onShowMessage("Company ledger details are available in the web portal.")
                                                }
                                            },
                                            leadingContent = {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = colors.primaryOnSurface,
                                                )
                                            },
                                            headlineContent = {
                                                Text(
                                                    text = hit.title,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = colors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            supportingContent = {
                                                Text(
                                                    text = hit.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            trailingContent = if (canOpenGroup) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Outlined.ChevronRight,
                                                        contentDescription = null,
                                                        tint = colors.textMuted,
                                                    )
                                                }
                                            } else null,
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
