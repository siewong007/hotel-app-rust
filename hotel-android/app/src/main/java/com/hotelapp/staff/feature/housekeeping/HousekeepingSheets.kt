package com.hotelapp.staff.feature.housekeeping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelAvatar
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.data.dto.AssignableStaffMemberDto
import com.hotelapp.staff.data.dto.HousekeepingBoardRoomDto

@Composable
fun AssignStaffSheet(
    title: String,
    staff: List<AssignableStaffMemberDto>,
    currentAssigneeId: Long?,
    onAssign: (Long?) -> Unit,
    onDismiss: () -> Unit,
    staffPickerError: String? = null,
    isLoadingStaff: Boolean = false,
    onRetryStaff: () -> Unit = {},
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    HotelModalSheetContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            if (staffPickerError != null) {
                InlineAlertBanner(message = staffPickerError, tone = colors.danger)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = !isLoadingStaff,
                        onClick = onRetryStaff,
                    ) {
                        Text(if (isLoadingStaff) "Retrying..." else "Retry", color = colors.primaryOnSurface)
                    }
                }
            }
            if (currentAssigneeId != null) {
                OutlinedCard(
                    onClick = {
                        haptics.tick()
                        onAssign(null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = colors.danger.bg),
                    border = BorderStroke(1.dp, colors.danger.border),
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.PersonOff,
                                contentDescription = null,
                                tint = colors.danger.fg,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = "Unassign Current Staff",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.danger.fg,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            if (isLoadingStaff && staff.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.primaryMain,
                    )
                }
            } else if (staff.isEmpty()) {
                Text(
                    text = "No assignable staff available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(staff, key = { it.id }) { member ->
                        val isCurrent = member.id == currentAssigneeId
                        OutlinedCard(
                            onClick = {
                                haptics.tick()
                                onAssign(member.id)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isCurrent) colors.surfaceSelected else colors.surfaceRaised,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) colors.primarySubtleBorder else colors.borderSubtle,
                            ),
                        ) {
                            ListItem(
                                leadingContent = {
                                    HotelAvatar(name = member.displayName(), size = 36.dp)
                                },
                                headlineContent = {
                                    Text(
                                        text = member.displayName(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isCurrent) colors.primaryOnSurface else colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "@${member.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                },
                                trailingContent = if (isCurrent) {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(colors.primaryMain),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = "Currently assigned",
                                                tint = colors.primaryContrastText,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHousekeepingTaskSheet(
    rooms: List<HousekeepingBoardRoomDto>,
    staff: List<AssignableStaffMemberDto>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onCreate: (Long, String, String, Long?, String) -> Unit,
    error: String? = null,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val firstRoom = rooms.firstOrNull()
    var selectedRoomId by remember { mutableLongStateOf(firstRoom?.id ?: 0L) }
    var roomQuery by remember {
        mutableStateOf(firstRoom?.let { "Room ${it.roomNumber} (${StatusFormatter.humanize(it.status)})" }.orEmpty())
    }
    var roomSelectionValid by remember(rooms) { mutableStateOf(firstRoom != null) }
    var roomExpanded by remember { mutableStateOf(false) }

    var taskType by remember { mutableStateOf("cleaning") }
    var priority by remember { mutableStateOf("normal") }
    var selectedStaffId by remember { mutableStateOf<Long?>(null) }
    var staffExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val selectedRoomLabel = remember(rooms, selectedRoomId) {
        rooms.find { it.id == selectedRoomId }?.let {
            "Room ${it.roomNumber} (${StatusFormatter.humanize(it.status)})"
        }.orEmpty()
    }
    val matchingRooms = remember(rooms, roomQuery, selectedRoomLabel) {
        val q = roomQuery.trim().lowercase()
        if (q.isEmpty() || roomQuery == selectedRoomLabel) {
            rooms
        } else {
            rooms.filter {
                it.roomNumber.lowercase().contains(q) ||
                    it.roomType.lowercase().contains(q) ||
                    StatusFormatter.humanize(it.status).lowercase().contains(q)
            }
        }
    }
    val selectedStaffLabel = remember(staff, selectedStaffId) {
        staff.find { it.id == selectedStaffId }?.displayName() ?: "Unassigned"
    }

    HotelModalSheetContainer(
        dismissible = !isSubmitting,
        onDismiss = { if (!isSubmitting) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("New Housekeeping Task", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)

            if (error != null) {
                InlineAlertBanner(message = error, tone = colors.danger)
            }

            ExposedDropdownMenuBox(
                expanded = roomExpanded,
                onExpandedChange = { roomExpanded = it },
            ) {
                OutlinedTextField(
                    value = roomQuery,
                    onValueChange = { nextQuery ->
                        roomQuery = nextQuery
                        val trimmed = nextQuery.trim()
                        val exactMatch = if (trimmed.isEmpty()) null else rooms.find { room ->
                            trimmed.equals(room.roomNumber, ignoreCase = true) ||
                                trimmed.equals("Room ${room.roomNumber}", ignoreCase = true) ||
                                trimmed.equals("Room ${room.roomNumber} (${StatusFormatter.humanize(room.status)})", ignoreCase = true)
                        }
                        if (exactMatch != null) {
                            selectedRoomId = exactMatch.id
                            roomSelectionValid = true
                        } else {
                            selectedRoomId = 0L
                            roomSelectionValid = false
                        }
                        roomExpanded = true
                    },
                    label = { Text("Room") },
                    placeholder = { Text("Search room # or type") },
                    supportingText = if (!roomSelectionValid) {
                        { Text("Select a valid room from the list", color = colors.danger.fg) }
                    } else null,
                    isError = !roomSelectionValid,
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
                        if (!roomSelectionValid && selectedRoomLabel.isNotEmpty()) {
                            roomQuery = selectedRoomLabel
                            roomSelectionValid = true
                        }
                    },
                    containerColor = colors.surfaceOverlay,
                    modifier = Modifier.heightIn(max = 260.dp),
                ) {
                    if (matchingRooms.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No matching rooms", color = colors.textMuted) },
                            onClick = { roomExpanded = false },
                            enabled = false,
                        )
                    } else {
                        matchingRooms.forEach { room ->
                            val tone = StatusFormatter.roomTone(room.status, colors)
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(tone.fg),
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Room ${room.roomNumber} · ${room.roomType} (${StatusFormatter.humanize(room.status)})",
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    haptics.tick()
                                    selectedRoomId = room.id
                                    roomQuery = "Room ${room.roomNumber} (${StatusFormatter.humanize(room.status)})"
                                    roomSelectionValid = true
                                    roomExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Text("Task Type", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            FilterChipRow(
                options = listOf(
                    FilterOption("cleaning", "Cleaning"),
                    FilterOption("checkout_clean", "Checkout Clean"),
                    FilterOption("inspection", "Inspection"),
                    FilterOption("maintenance_followup", "Maint. Follow-up"),
                ),
                selected = taskType,
                onSelect = { taskType = it },
            )

            Text("Priority", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            FilterChipRow(
                options = listOf(
                    FilterOption("low", "Low", dotColor = colors.neutral.fg),
                    FilterOption("normal", "Normal", dotColor = colors.info.fg),
                    FilterOption("high", "High", dotColor = colors.orange.fg),
                    FilterOption("urgent", "Urgent", dotColor = colors.danger.fg),
                ),
                selected = priority,
                onSelect = { priority = it },
            )

            if (staff.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = staffExpanded,
                    onExpandedChange = { staffExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedStaffLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assign To (Optional)") },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = staffExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        colors = hotelTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = staffExpanded,
                        onDismissRequest = { staffExpanded = false },
                        containerColor = colors.surfaceOverlay,
                        modifier = Modifier.heightIn(max = 260.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unassigned", color = colors.textSecondary) },
                            onClick = {
                                haptics.tick()
                                selectedStaffId = null
                                staffExpanded = false
                            },
                        )
                        staff.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.displayName(), color = colors.textPrimary) },
                                onClick = {
                                    haptics.tick()
                                    selectedStaffId = member.id
                                    staffExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))
            HotelPrimaryButton(
                text = if (isSubmitting) "Creating..." else "Create Task",
                isLoading = isSubmitting,
                enabled = selectedRoomId > 0 && roomSelectionValid,
                onClick = { onCreate(selectedRoomId, taskType, priority, selectedStaffId, notes) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
