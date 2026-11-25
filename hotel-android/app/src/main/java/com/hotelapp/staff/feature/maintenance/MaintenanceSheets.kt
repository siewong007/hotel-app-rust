package com.hotelapp.staff.feature.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.KeyValueRow
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.data.dto.MaintenanceTicketDto
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaintenanceDetailSheet(
    ticket: MaintenanceTicketDto,
    currency: String,
    canWrite: Boolean,
    isMutating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onTransition: (String, String?) -> Unit,
    onAssignClick: () -> Unit,
) {
    val colors = HotelTheme.colors
    var resolutionNotes by remember(ticket.id) { mutableStateOf(ticket.resolutionNotes ?: "") }

    val primaryAction: Pair<String, String>? = when (ticket.status.lowercase()) {
        "open", "on_hold" -> "in_progress" to "Start Work"
        "in_progress" -> "resolved" to "Mark Resolved"
        "resolved" -> "closed" to "Close Ticket"
        else -> null
    }
    val secondaryActions: List<String> = when (ticket.status.lowercase()) {
        "open" -> listOf("on_hold", "closed")
        "in_progress" -> listOf("on_hold", "closed")
        "on_hold" -> listOf("closed")
        "resolved" -> listOf("in_progress")
        else -> emptyList()
    }

    HotelModalSheetContainer(onDismiss = { if (!isMutating) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ticket.ticketNumber, style = MaterialTheme.typography.labelLarge, color = colors.primaryOnSurface)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        label = StatusFormatter.humanize(ticket.priority),
                        tone = StatusFormatter.priorityTone(ticket.priority, colors),
                    )
                    StatusPill(
                        label = StatusFormatter.humanize(ticket.status),
                        tone = StatusFormatter.taskTone(ticket.status, colors),
                    )
                }
            }
            Text(ticket.title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            if (!ticket.description.isNullOrBlank()) {
                Text(ticket.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            KeyValueRow("Location", ticket.roomNumber?.let { "Room $it" } ?: "Common Area")
            KeyValueRow("Category", StatusFormatter.humanize(ticket.category))
            KeyValueRow("Assignee", ticket.assignedToName ?: "Unassigned")
            ticket.estimatedCost?.let { KeyValueRow("Estimated Cost", MoneyFormatter.format(it, currency)) }
            ticket.actualCost?.let { KeyValueRow("Actual Cost", MoneyFormatter.format(it, currency)) }
            KeyValueRow("Created", DateFormatter.formatTimestamp(ticket.createdAt))
            KeyValueRow("Updated", DateFormatter.formatRelativeTimestamp(ticket.updatedAt))

            if (error != null) {
                InlineAlertBanner(message = error, tone = colors.danger)
            }

            if (canWrite) {
                HorizontalDivider(color = colors.borderSubtle)
                HotelOutlinedButton(
                    text = "Change Assignee",
                    icon = Icons.Outlined.PersonAddAlt1,
                    enabled = !isMutating,
                    onClick = onAssignClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (primaryAction != null || secondaryActions.isNotEmpty()) {
                    OutlinedTextField(
                        value = resolutionNotes,
                        onValueChange = { resolutionNotes = it },
                        label = { Text("Resolution / Progress Notes") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = hotelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    primaryAction?.let { (target, label) ->
                        HotelPrimaryButton(
                            text = label,
                            icon = if (target == "resolved") Icons.Outlined.CheckCircleOutline else Icons.Outlined.PlayArrow,
                            isLoading = isMutating,
                            containerColor = if (target == "resolved") colors.success.fg else colors.primaryMain,
                            contentColor = colors.primaryContrastText,
                            onClick = { onTransition(target, resolutionNotes) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (secondaryActions.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            secondaryActions.forEach { target ->
                                HotelOutlinedButton(
                                    text = "Mark ${StatusFormatter.humanize(target)}",
                                    enabled = !isMutating,
                                    onClick = { onTransition(target, resolutionNotes) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMaintenanceTicketSheet(
    rooms: List<RoomWithOccupancyDto>,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (Long?, String, String, String, String) -> Unit,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    var selectedRoomId by remember { mutableStateOf<Long?>(null) }
    var roomQuery by remember { mutableStateOf("Common Area") }
    var roomExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var priority by remember { mutableStateOf("medium") }
    var description by remember { mutableStateOf("") }

    val selectedRoomLabel = remember(rooms, selectedRoomId) {
        if (selectedRoomId == null) {
            "Common Area"
        } else {
            rooms.find { it.id == selectedRoomId }?.let { "Room ${it.roomNumber} (${it.roomType})" } ?: "Common Area"
        }
    }
    val visibleRooms = remember(rooms, roomQuery, selectedRoomLabel) {
        val q = roomQuery.trim().lowercase()
        if (q.isEmpty() || roomQuery == selectedRoomLabel) {
            rooms
        } else {
            rooms.filter {
                it.roomNumber.lowercase().contains(q) || it.roomType.lowercase().contains(q)
            }
        }
    }

    HotelModalSheetContainer(onDismiss = { if (!isSubmitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("New Maintenance Ticket", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)

            if (error != null) {
                InlineAlertBanner(message = error, tone = colors.danger)
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Issue Title") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (rooms.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = roomExpanded,
                    onExpandedChange = { roomExpanded = it },
                ) {
                    OutlinedTextField(
                        value = roomQuery,
                        onValueChange = {
                            roomQuery = it
                            roomExpanded = true
                        },
                        label = { Text("Location / Room (Optional)") },
                        placeholder = { Text("Common Area or room #") },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        colors = hotelTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = roomExpanded,
                        onDismissRequest = {
                            roomExpanded = false
                            roomQuery = selectedRoomLabel
                        },
                        containerColor = colors.surfaceOverlay,
                        modifier = Modifier.heightIn(max = 260.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Common Area", color = colors.textPrimary) },
                            onClick = {
                                haptics.tick()
                                selectedRoomId = null
                                roomQuery = "Common Area"
                                roomExpanded = false
                            },
                        )
                        visibleRooms.forEach { room ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Room ${room.roomNumber} · ${room.roomType}",
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    haptics.tick()
                                    selectedRoomId = room.id
                                    roomQuery = "Room ${room.roomNumber} (${room.roomType})"
                                    roomExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Text("Category", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            FilterChipRow(
                options = listOf(
                    FilterOption("electrical", "Electrical"),
                    FilterOption("plumbing", "Plumbing"),
                    FilterOption("hvac", "HVAC"),
                    FilterOption("furniture", "Furniture"),
                    FilterOption("appliance", "Appliance"),
                    FilterOption("structural", "Structural"),
                    FilterOption("other", "Other"),
                ),
                selected = category,
                onSelect = { category = it },
            )

            Text("Priority", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            FilterChipRow(
                options = listOf(
                    FilterOption("low", "Low", dotColor = colors.neutral.fg),
                    FilterOption("medium", "Medium", dotColor = colors.info.fg),
                    FilterOption("high", "High", dotColor = colors.orange.fg),
                    FilterOption("critical", "Critical", dotColor = colors.danger.fg),
                ),
                selected = priority,
                onSelect = { priority = it },
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))
            HotelPrimaryButton(
                text = if (isSubmitting) "Submitting..." else "Create Ticket",
                isLoading = isSubmitting,
                enabled = title.isNotBlank(),
                onClick = { onCreate(selectedRoomId, title, category, priority, description) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
