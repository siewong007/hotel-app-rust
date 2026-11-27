package com.hotelapp.staff.feature.bookings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.MoneyInputParser
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.dto.SettlementLockState
import java.math.BigDecimal

@Composable
fun CheckInModalSheet(
    booking: BookingWithDetailsDto,
    currency: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String, String) -> Unit,
    effectiveBalanceDue: BigDecimal? = booking.balanceDue,
    canCollectPayment: Boolean = true,
    errorMessage: String? = null,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var icNumber by remember { mutableStateOf("") }
    val balanceDue = effectiveBalanceDue?.takeIf { it > BigDecimal.ZERO }
    val isGroupBooking = !booking.groupNumber.isNullOrBlank()
    val lockState = remember(
        booking.source,
        booking.bookingChannelId,
        booking.bookingChannelName,
        booking.bookingChannelType,
        booking.bookingChannelSettlementMethods,
        booking.channelPayoutRecorded,
    ) {
        booking.settlementLockState(allowDeskRemainder = false, paymentType = "booking")
    }
    val methodOptions = remember(lockState) {
        when (lockState) {
            is SettlementLockState.Restricted -> lockState.methods.map { FilterOption(it, StatusFormatter.humanize(it)) }
            is SettlementLockState.Blocked -> emptyList()
            SettlementLockState.Open -> listOf(
                FilterOption("cash", "Cash"),
                FilterOption("card", "Card"),
                FilterOption("bank_transfer", "Transfer"),
                FilterOption("duitnow", "DuitNow"),
                FilterOption("online_banking", "Online Banking"),
            )
        }
    }
    val canOfferPayment = canCollectPayment && !isGroupBooking && lockState !is SettlementLockState.Blocked
    var collectPayment by remember(canOfferPayment) { mutableStateOf(false) }
    var paymentAmount by remember(balanceDue) {
        mutableStateOf(balanceDue?.toPlainString() ?: "")
    }
    var paymentMethod by remember(methodOptions) {
        mutableStateOf(methodOptions.firstOrNull()?.value ?: "cash")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Check In · Room ${booking.roomNumber}",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                text = "Leave fields blank if already complete on the guest profile.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                InlineAlertBanner(message = errorMessage.orEmpty(), tone = colors.danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First Name (opt)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentType = ContentType.PersonFirstName },
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name (opt)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentType = ContentType.PersonLastName },
                )
            }
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone (opt)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.PhoneNumber },
            )
            OutlinedTextField(
                value = icNumber,
                onValueChange = { icNumber = it },
                label = { Text("IC / Passport Number (if missing)") },
                placeholder = { Text("Required by desk if not yet on file") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (canCollectPayment && isGroupBooking) {
                InlineAlertBanner(
                    message = "Group booking member — collect room balance via Pay Group in the folio section.",
                    tone = colors.info,
                )
            } else if (canCollectPayment && lockState is SettlementLockState.Blocked) {
                InlineAlertBanner(
                    message = lockState.reason,
                    tone = colors.warning,
                )
            }
            if (canOfferPayment) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isSubmitting) { collectPayment = !collectPayment }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Collect payment at check-in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        if (balanceDue != null) {
                            Text(
                                text = "Balance due: ${MoneyFormatter.format(balanceDue, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.danger.fg,
                            )
                        }
                    }
                    Switch(
                        checked = collectPayment,
                        enabled = !isSubmitting,
                        onCheckedChange = { collectPayment = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primaryContrastText,
                            checkedTrackColor = colors.primaryMain,
                        ),
                    )
                }
                AnimatedVisibility(visible = collectPayment) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = paymentAmount,
                            onValueChange = { paymentAmount = it },
                            enabled = !isSubmitting,
                            label = { Text("Payment Amount ($currency)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = hotelTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            trailingIcon = if (balanceDue != null) {
                                {
                                    TextButton(
                                        enabled = !isSubmitting,
                                        onClick = { paymentAmount = balanceDue.toPlainString() },
                                    ) {
                                        Text("Full", color = colors.primaryOnSurface)
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FilterChipRow(
                            options = methodOptions,
                            selected = paymentMethod,
                            onSelect = { if (!isSubmitting) paymentMethod = it },
                        )
                    }
                }
            }
            val parsedPaymentValid = !canOfferPayment || !collectPayment ||
                (MoneyInputParser.parsePositiveAmount(paymentAmount) != null)
            Spacer(modifier = Modifier.height(4.dp))
            HotelPrimaryButton(
                text = if (isSubmitting) "Checking In..." else "Confirm Check-In",
                isLoading = isSubmitting,
                enabled = !isSubmitting && parsedPaymentValid,
                icon = Icons.AutoMirrored.Outlined.Login,
                onClick = {
                    haptics.click()
                    onSubmit(
                        firstName,
                        lastName,
                        phone,
                        icNumber,
                        if (canOfferPayment && collectPayment) paymentAmount else "",
                        paymentMethod,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun BookingReasonDialog(
    title: String,
    requireReason: Boolean,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    minReasonLength: Int = if (requireReason) 1 else 0,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    var reason by remember { mutableStateOf("") }
    val trimmedLen = reason.trim().length
    val isValid = !requireReason || trimmedLen >= minReasonLength
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        containerColor = colors.surfaceOverlay,
        title = { Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!errorMessage.isNullOrBlank()) {
                    InlineAlertBanner(message = errorMessage, tone = colors.danger)
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    enabled = !isSubmitting,
                    label = { Text(if (requireReason) "Reason (required)" else "Reason (optional)") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireReason && minReasonLength > 1 && trimmedLen < minReasonLength) {
                    Text(
                        text = "Enter at least $minReasonLength characters.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            HotelPrimaryButton(
                text = if (isSubmitting) "Working..." else confirmLabel,
                isLoading = isSubmitting,
                enabled = !isSubmitting && isValid,
                containerColor = colors.danger.fg,
                contentColor = colors.primaryContrastText,
                onClick = {
                    haptics.reject()
                    onConfirm(reason)
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
    )
}
