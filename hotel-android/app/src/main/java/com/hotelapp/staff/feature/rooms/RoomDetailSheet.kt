package com.hotelapp.staff.feature.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelLogoLoader
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.KeyValueRow
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.data.dto.RoomDetailedStatusDto

@Composable
fun RoomDetailSheet(
    detail: RoomDetailedStatusDto?,
    isLoading: Boolean,
    isMutating: Boolean,
    error: String?,
    canUpdateRooms: Boolean,
    canReadBookings: Boolean = true,
    onDismiss: () -> Unit,
    onOpenBookingDetail: (Long) -> Unit,
    onEndCleaning: (Long) -> Unit,
    onEndMaintenance: (Long) -> Unit,
    onUpdateStatus: (Long, String, String?) -> Unit,
) {
    val colors = HotelTheme.colors
    var selectedTargetStatus by remember(detail?.id, detail?.status) {
        val initial = detail?.status?.takeIf { it in setOf("available", "dirty", "maintenance") } ?: "available"
        mutableStateOf(initial)
    }
    var statusNotes by remember(detail?.id) { mutableStateOf("") }

    HotelModalSheetContainer(onDismiss = { if (!isMutating) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                isLoading -> HotelLogoLoader(
                    message = "Loading room details...",
                    delayMs = 0L,
                    modifier = Modifier.height(180.dp),
                )
                detail != null -> {
                    val tone = StatusFormatter.roomTone(detail.status, colors)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Room ${detail.roomNumber}",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = detail.roomType,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        StatusPill(
                            label = StatusFormatter.humanize(detail.status),
                            tone = tone,
                            leadingDot = true,
                        )
                    }

                    if (error != null) {
                        InlineAlertBanner(message = error, tone = colors.danger)
                    }

                    detail.currentBooking?.let { cur ->
                        HotelCard(
                            onClick = if (canReadBookings) { { onOpenBookingDetail(cur.id) } } else null,
                            accentColor = colors.success.fg,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Hotel,
                                        contentDescription = null,
                                        tint = colors.success.fg,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Current Stay",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.success.fg,
                                    )
                                }
                                if (canReadBookings) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = "Open booking",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            KeyValueRow("Guest", cur.guestName)
                            KeyValueRow("Booking #", cur.bookingNumber)
                            KeyValueRow("Dates", DateFormatter.formatDateRangeWithNights(cur.checkInDate, cur.checkOutDate))
                        }
                    }

                    detail.nextBooking?.let { next ->
                        HotelCard(
                            onClick = if (canReadBookings) { { onOpenBookingDetail(next.id) } } else null,
                            accentColor = colors.info.fg,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Event,
                                        contentDescription = null,
                                        tint = colors.info.fg,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Next Arrival",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.info.fg,
                                    )
                                }
                                if (canReadBookings) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = "Open booking",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            KeyValueRow("Guest", next.guestName)
                            KeyValueRow("Dates", DateFormatter.formatDateRangeWithNights(next.checkInDate, next.checkOutDate))
                        }
                    }

                    if (!detail.statusNotes.isNullOrBlank() || !detail.maintenanceNotes.isNullOrBlank()) {
                        HotelCard {
                            detail.statusNotes?.takeIf { it.isNotBlank() }?.let {
                                Text("Status Notes", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                            }
                            detail.maintenanceNotes?.takeIf { it.isNotBlank() }?.let {
                                if (!detail.statusNotes.isNullOrBlank()) Spacer(modifier = Modifier.height(6.dp))
                                Text("Maintenance Notes", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                        }
                    }

                    if (detail.recentEvents.isNotEmpty()) {
                        HotelCard {
                            Text(
                                text = "Recent Room Events",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            detail.recentEvents.take(5).forEachIndexed { idx, ev ->
                                if (idx > 0) {
                                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = StatusFormatter.humanize(ev.eventType),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = DateFormatter.formatRelativeTimestamp(ev.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textMuted,
                                    )
                                }
                                ev.notes?.takeIf { it.isNotBlank() }?.let { note ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    if (canUpdateRooms) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)

                        if (detail.status == "cleaning") {
                            HotelPrimaryButton(
                                text = "Complete Cleaning",
                                icon = Icons.Outlined.CheckCircleOutline,
                                isLoading = isMutating,
                                containerColor = colors.success.fg,
                                contentColor = colors.primaryContrastText,
                                onClick = { onEndCleaning(detail.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (detail.status in setOf("dirty", "reserved_dirty")) {
                            HotelPrimaryButton(
                                text = "Mark Clean / Available",
                                icon = Icons.Outlined.CleaningServices,
                                isLoading = isMutating,
                                containerColor = colors.success.fg,
                                contentColor = colors.primaryContrastText,
                                onClick = { onUpdateStatus(detail.id, "available", "Cleaned") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (detail.status == "maintenance") {
                            HotelPrimaryButton(
                                text = "End Maintenance",
                                icon = Icons.Outlined.Build,
                                isLoading = isMutating,
                                containerColor = colors.info.fg,
                                contentColor = colors.primaryContrastText,
                                onClick = { onEndMaintenance(detail.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Text("Set Room Status", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        FilterChipRow(
                            options = listOf(
                                FilterOption("available", "Available", dotColor = colors.success.fg),
                                FilterOption("dirty", "Dirty", dotColor = colors.orange.fg),
                                FilterOption("maintenance", "Maintenance", dotColor = colors.danger.fg),
                            ),
                            selected = selectedTargetStatus,
                            onSelect = { selectedTargetStatus = it },
                        )
                        OutlinedTextField(
                            value = statusNotes,
                            onValueChange = { statusNotes = it },
                            label = { Text("Notes / Reason (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = hotelTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HotelOutlinedButton(
                            text = if (isMutating) "Updating..." else "Apply Status Change",
                            isLoading = isMutating,
                            enabled = selectedTargetStatus != detail.status,
                            onClick = { onUpdateStatus(detail.id, selectedTargetStatus, statusNotes) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                error != null -> InlineAlertBanner(message = error, tone = colors.danger)
            }
        }
    }
}
