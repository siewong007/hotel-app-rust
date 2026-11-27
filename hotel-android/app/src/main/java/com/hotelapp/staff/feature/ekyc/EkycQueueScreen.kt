package com.hotelapp.staff.feature.ekyc

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.ErrorStateView
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPullToRefreshBox
import com.hotelapp.staff.core.design.HotelSearchBar
import com.hotelapp.staff.core.design.HotelSnackbarHost
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.HotelTooltipIconButton
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.ListCardSkeleton
import com.hotelapp.staff.core.design.OnResumeRefreshEffect
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.core.ui.CollectEffects

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EkycQueueScreen(
    session: StaffSession,
    viewModel: EkycViewModel,
    onBack: () -> Unit,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is EkycEffect.ShowMessage -> {
                if (effect.isError) haptics.reject() else haptics.confirm()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val showCounts = state.searchQuery.isBlank()
    val statusFilters = remember(state.metrics, showCounts, colors) {
        listOf(
            FilterOption("all", "All", count = if (showCounts) state.metrics.totalSubmitted.toInt() else null),
            FilterOption("pending_manual_review", "Pending Review", dotColor = colors.warning.fg),
            FilterOption("submitted", "Submitted", dotColor = colors.warning.fg),
            FilterOption("in_review", "In Review", dotColor = colors.info.fg),
            FilterOption("on_hold", "On Hold", dotColor = colors.warning.fg),
            FilterOption(
                "additional_information_required",
                "Resubmission",
                count = if (showCounts) state.metrics.resubmissionRequired.toInt() else null,
                dotColor = colors.warning.fg,
            ),
            FilterOption("escalated", "Escalated", dotColor = colors.danger.fg),
            FilterOption("approved", "Approved", count = if (showCounts) state.metrics.approved.toInt() else null, dotColor = colors.success.fg),
            FilterOption("rejected", "Rejected", count = if (showCounts) state.metrics.rejected.toInt() else null, dotColor = colors.neutral.fg),
        )
    }

    OnResumeRefreshEffect { viewModel.refresh(silent = true) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.surfaceApp,
        snackbarHost = { HotelSnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("eKYC Review Queue", color = colors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                        }
                    },
                    actions = {
                        HotelTooltipIconButton(
                            tooltip = "Refresh",
                            onClick = { viewModel.refresh() },
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = colors.textPrimary)
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
                query = state.searchQuery,
                onQueryChange = viewModel::setSearch,
                placeholder = "Search guest name, email, application ID",
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilterChipRow(
                options = statusFilters,
                selected = state.statusFilter,
                onSelect = viewModel::setStatusFilter,
            )
            if (state.metrics.totalSubmitted > 0 && state.metrics.pendingReview > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${state.metrics.pendingReview} awaiting review · ${state.metrics.nearingSla} nearing SLA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
            if (state.error != null && state.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                InlineAlertBanner(
                    message = state.error!!,
                    tone = colors.danger,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyStateView(
                                title = "No eKYC applications",
                                subtitle = "No verification requests match the current filter.",
                                icon = Icons.Outlined.Badge,
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.items, key = { it.id }) { app ->
                                val tone = StatusFormatter.ekycTone(app.status, colors)
                                HotelCard(
                                    onClick = { viewModel.openDetail(app.id) },
                                    accentColor = tone.fg,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.fullName ?: app.applicationId,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colors.textPrimary,
                                            )
                                            val docInfo = listOfNotNull(
                                                app.idType?.let { StatusFormatter.humanize(it) },
                                                app.idNumberMasked,
                                            ).joinToString(" · ")
                                            if (docInfo.isNotEmpty()) {
                                                Text(
                                                    text = docInfo,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.textSecondary,
                                                )
                                            }
                                        }
                                        StatusPill(
                                            label = StatusFormatter.humanize(app.status),
                                            tone = tone,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        val riskTone = when (app.riskLevel.lowercase()) {
                                            "high", "critical" -> colors.danger
                                            "medium" -> colors.warning
                                            else -> colors.neutral
                                        }
                                        StatusPill("Risk: ${StatusFormatter.humanize(app.riskLevel)}", riskTone)
                                        if (app.overdueSla) {
                                            StatusPill("SLA Overdue", colors.danger)
                                        } else if (app.nearingSla) {
                                            StatusPill("SLA Soon", colors.warning)
                                        }
                                        if (app.arrivalImminent) {
                                            StatusPill("Arriving Soon", colors.info)
                                        }
                                        app.nextArrivalDate?.let {
                                            StatusPill("Arrival ${DateFormatter.formatDate(it)}", colors.neutral)
                                        }
                                    }
                                }
                            }
                            if (state.hasMore && state.items.size < state.total) {
                                item(key = "ekyc_load_more") {
                                    if (state.isLoadingMore) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
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

    if (state.isLoadingDetail || state.selectedDetail != null || state.sheetError != null) {
        EkycDetailSheet(
            session = session,
            detail = state.selectedDetail,
            isLoading = state.isLoadingDetail,
            isMutating = state.isMutating,
            error = state.sheetError,
            reasonCodes = state.reasonCodes,
            isLoadingReasonCodes = state.isLoadingReasonCodes,
            reasonCodesError = state.reasonCodesError,
            onRetryReasonCodes = viewModel::retryLoadReasonCodes,
            onRefresh = viewModel::refreshSelectedDetail,
            activeDocumentKind = state.activeDocumentKind,
            activeDocumentBytes = state.activeDocumentBytes,
            isLoadingDocument = state.isLoadingDocument,
            documentError = state.documentError,
            onOpenDocument = viewModel::openDocument,
            onDismissDocument = viewModel::dismissDocument,
            revealedFields = state.revealedFields,
            isRevealing = state.isRevealing,
            revealError = state.revealError,
            onRevealField = viewModel::revealSensitiveField,
            onClearRevealError = viewModel::clearRevealError,
            onDismiss = viewModel::dismissDetail,
            onAction = { id, ver, action, code, reason, msg, selfCheckin, idempotencyKey, onSuccess ->
                viewModel.applyAction(
                    id = id,
                    expectedVersion = ver,
                    action = action,
                    reasonCode = code,
                    reason = reason,
                    customerMessage = msg,
                    selfCheckinEnabled = selfCheckin,
                    idempotencyKey = idempotencyKey,
                    onSuccess = onSuccess,
                )
            },
        )
    }
}
