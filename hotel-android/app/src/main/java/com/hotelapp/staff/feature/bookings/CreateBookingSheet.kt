package com.hotelapp.staff.feature.bookings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.data.dto.CreateBookingRequestDto
import com.hotelapp.staff.data.dto.GuestDto
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto
import com.hotelapp.staff.data.repository.BookingsRepository
import com.hotelapp.staff.data.repository.GuestsRepository
import com.hotelapp.staff.data.repository.RoomsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBookingSheet(
    currency: String,
    bookingsRepository: BookingsRepository,
    guestsRepository: GuestsRepository,
    roomsRepository: RoomsRepository,
    onDismiss: () -> Unit,
    onCreated: (Long) -> Unit,
    session: StaffSession? = null,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val scope = rememberCoroutineScope()
    val canReadRooms = session?.hasPermission("rooms:read") ?: true
    val canReadGuests = session?.hasPermission("guests:read") ?: true

    var guests by remember { mutableStateOf<List<GuestDto>>(emptyList()) }
    var guestQuery by remember { mutableStateOf("") }
    var selectedGuest by remember { mutableStateOf<GuestDto?>(null) }
    var guestExpanded by remember { mutableStateOf(false) }

    var rooms by remember { mutableStateOf<List<RoomWithOccupancyDto>>(emptyList()) }
    var selectedRoomId by remember { mutableLongStateOf(0L) }
    var roomExpanded by remember { mutableStateOf(false) }

    var checkInDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var checkOutDate by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var source by remember { mutableStateOf("walk_in") }
    var paymentMethod by remember { mutableStateOf("cash") }
    var depositAmount by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!canReadRooms || !canReadGuests) {
            error = "Walk-in creation requires rooms:read and guests:read permissions."
            return@LaunchedEffect
        }
        roomsRepository.roomsWithOccupancy()
            .onSuccess { all ->
                val sorted = all.sortedWith(
                    compareBy<RoomWithOccupancyDto> { it.isOccupied || it.status != "available" }
                        .thenBy { it.roomNumber },
                )
                rooms = sorted
                if (selectedRoomId == 0L) {
                    sorted.firstOrNull()?.let { selectedRoomId = it.id }
                }
            }
            .onFailure { err ->
                error = err.message ?: "Unable to load rooms."
            }
        guestsRepository.list(page = 1)
            .onSuccess { res ->
                guests = res.data
            }
            .onFailure { err ->
                if (error == null) error = err.message ?: "Unable to load guests."
            }
    }

    LaunchedEffect(guestQuery) {
        if (!canReadGuests) return@LaunchedEffect
        if (guestQuery == selectedGuest?.displayName()) return@LaunchedEffect
        delay(250)
        guestsRepository.list(search = guestQuery, page = 1).onSuccess { res ->
            guests = res.data
        }
    }

    val selectedRoom = rooms.find { it.id == selectedRoomId }

    HotelModalSheetContainer(onDismiss = { if (!isSubmitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "New Walk-In / Reservation",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )

            if (error != null) {
                InlineAlertBanner(message = error!!, tone = colors.danger)
            }

            ExposedDropdownMenuBox(
                expanded = guestExpanded,
                onExpandedChange = { guestExpanded = it },
            ) {
                OutlinedTextField(
                    value = guestQuery,
                    onValueChange = {
                        guestQuery = it
                        if (selectedGuest != null && it != selectedGuest?.displayName()) {
                            selectedGuest = null
                        }
                        guestExpanded = true
                    },
                    label = { Text("Guest") },
                    placeholder = { Text("Search name, email or phone") },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = guestExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(
                    expanded = guestExpanded,
                    onDismissRequest = { guestExpanded = false },
                    containerColor = colors.surfaceOverlay,
                    modifier = Modifier.heightIn(max = 240.dp),
                ) {
                    if (guests.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No matching guests", color = colors.textMuted) },
                            onClick = { guestExpanded = false },
                            enabled = false,
                        )
                    } else {
                        guests.forEach { guest ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = buildString {
                                            append(guest.displayName())
                                            guest.phone?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                        },
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    haptics.tick()
                                    selectedGuest = guest
                                    guestQuery = guest.displayName()
                                    guestExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = roomExpanded,
                onExpandedChange = { roomExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedRoom?.let {
                        "Room ${it.roomNumber} · ${it.roomType} (${MoneyFormatter.format(it.pricePerNight, currency)}/nt)"
                    } ?: "Select Room",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Room") },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = roomExpanded,
                    onDismissRequest = { roomExpanded = false },
                    containerColor = colors.surfaceOverlay,
                    modifier = Modifier.heightIn(max = 260.dp),
                ) {
                    rooms.forEach { room ->
                        val statusTag = if (room.isOccupied) "Occupied" else (room.status ?: "available")
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Rm ${room.roomNumber} · ${room.roomType} ($statusTag) · ${MoneyFormatter.format(room.pricePerNight, currency)}",
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                haptics.tick()
                                selectedRoomId = room.id
                                roomExpanded = false
                            },
                        )
                    }
                }
            }

            HotelOutlinedButton(
                text = "Stay: $checkInDate → $checkOutDate",
                icon = Icons.Outlined.DateRange,
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Booking Source", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            FilterChipRow(
                options = listOf(
                    FilterOption("walk_in", "Walk-In"),
                    FilterOption("phone", "Phone"),
                    FilterOption("online", "Direct Online"),
                ),
                selected = source,
                onSelect = { source = it },
            )

            Text("Payment Method", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            FilterChipRow(
                options = listOf(
                    FilterOption("cash", "Cash"),
                    FilterOption("card", "Card"),
                    FilterOption("bank_transfer", "Bank Transfer"),
                ),
                selected = paymentMethod,
                onSelect = { paymentMethod = it },
            )

            OutlinedTextField(
                value = depositAmount,
                onValueChange = { depositAmount = it },
                label = { Text("Deposit Amount (optional, $currency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Staff Remarks / Special Requests (optional)") },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))
            HotelPrimaryButton(
                text = if (isSubmitting) "Creating Booking..." else "Create Booking",
                isLoading = isSubmitting,
                enabled = selectedGuest != null && selectedRoomId > 0L,
                onClick = {
                    val guest = selectedGuest ?: return@HotelPrimaryButton
                    val rawDeposit = depositAmount.trim()
                    val parsedDeposit = rawDeposit.toDoubleOrNull()
                    if (rawDeposit.isNotEmpty() && (parsedDeposit == null || parsedDeposit <= 0.0)) {
                        error = "Enter a valid deposit amount greater than 0, or leave blank."
                        return@HotelPrimaryButton
                    }
                    isSubmitting = true
                    error = null
                    scope.launch {
                        bookingsRepository.createBooking(
                            CreateBookingRequestDto(
                                guestId = guest.id,
                                roomId = selectedRoomId,
                                checkInDate = checkInDate,
                                checkOutDate = checkOutDate,
                                paymentMethod = paymentMethod,
                                source = source,
                                bookingRemarks = remarks.trim().takeIf { it.isNotEmpty() },
                                depositAmount = parsedDeposit,
                                depositPaid = if (parsedDeposit != null) true else null,
                            ),
                        ).onSuccess { created ->
                            isSubmitting = false
                            haptics.confirm()
                            onCreated(created.id)
                        }.onFailure { err ->
                            isSubmitting = false
                            haptics.reject()
                            error = err.message ?: "Failed to create booking."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = LocalDate.parse(checkInDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = LocalDate.parse(checkOutDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMs = pickerState.selectedStartDateMillis
                        val endMs = pickerState.selectedEndDateMillis
                        if (startMs != null && endMs != null && endMs > startMs) {
                            checkInDate = Instant.ofEpochMilli(startMs).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            checkOutDate = Instant.ofEpochMilli(endMs).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            Box(modifier = Modifier.height(440.dp)) {
                DateRangePicker(state = pickerState)
            }
        }
    }
}
