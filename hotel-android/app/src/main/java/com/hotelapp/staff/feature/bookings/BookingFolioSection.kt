package com.hotelapp.staff.feature.bookings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.KeyValueRow
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.MoneyInputParser
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.data.dto.BookingGroupDetailDto
import com.hotelapp.staff.data.dto.BookingPaymentRecordDto
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.dto.InvoiceDto
import com.hotelapp.staff.data.dto.PaymentWorkflowSummaryDto
import com.hotelapp.staff.data.dto.SettlementLockState
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentHistoryCard(
    payments: List<BookingPaymentRecordDto>,
    summary: PaymentWorkflowSummaryDto?,
    currency: String,
    canCreatePayment: Boolean,
    isLoadingInvoice: Boolean,
    onRecordPayment: () -> Unit,
    onOpenInvoice: () -> Unit,
    isGroupBooking: Boolean = false,
    canReadPayment: Boolean = true,
) {
    val colors = HotelTheme.colors
    HotelCard {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Payments & Invoice",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(end = 8.dp),
            )
            summary?.nextAction?.takeIf { it.isNotBlank() }?.let { action ->
                StatusPill(
                    label = StatusFormatter.humanize(action),
                    tone = colors.info,
                )
            }
        }

        if (summary != null && summary.warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.warnings.forEach { warning ->
                    InlineAlertBanner(message = warning, tone = colors.warning)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canCreatePayment) {
                HotelPrimaryButton(
                    text = if (isGroupBooking) "Pay Group" else "Record Payment",
                    icon = Icons.Outlined.Payments,
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1f),
                )
            }
            if (canReadPayment) {
                HotelOutlinedButton(
                    text = "Invoice PDF",
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    isLoading = isLoadingInvoice,
                    onClick = onOpenInvoice,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (canReadPayment && payments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.borderSubtle)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Transactions (${payments.size})",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            payments.forEachIndexed { idx, row ->
                if (idx > 0) {
                    HorizontalDivider(
                        color = colors.borderSubtle,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${StatusFormatter.humanize(row.paymentMethod)} · ${StatusFormatter.humanize(row.paymentType ?: "booking")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        val meta = listOfNotNull(
                            DateFormatter.formatRelativeTimestamp(row.createdAt),
                            row.transactionReference?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val isVoided = row.voidedAt != null ||
                            row.paymentStatus.equals("void", ignoreCase = true) ||
                            row.paymentStatus.equals("voided", ignoreCase = true)
                        Text(
                            text = MoneyFormatter.format(row.totalAmount, currency),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isVoided) colors.textMuted else colors.textPrimary,
                        )
                        val statusLabel = if (isVoided) "Voided" else StatusFormatter.humanize(row.paymentStatus ?: "completed")
                        val tone = if (isVoided) colors.danger else StatusFormatter.paymentTone(row.paymentStatus ?: "paid", colors)
                        StatusPill(label = statusLabel, tone = tone)
                    }
                }
                if (!row.voidReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Voided: ${row.voidReason}" + (row.voidedByName?.let { " by $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.danger.fg,
                    )
                }
            }
        }
    }
}

@Composable
fun RecordPaymentSheet(
    booking: BookingWithDetailsDto,
    defaultAmount: BigDecimal?,
    currency: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        amountText: String,
        method: String,
        paymentType: String,
        reference: String,
        notes: String,
        idempotencyKey: String,
    ) -> Unit,
    groupDetail: BookingGroupDetailDto? = null,
    errorMessage: String? = null,
) {
    val colors = HotelTheme.colors
    val submissionKey = remember(booking.id) { UUID.randomUUID().toString() }
    val hasGroup = !booking.groupNumber.isNullOrBlank()
    var paymentType by remember(booking.id) { mutableStateOf("booking") }
    val isGroupRoomCharge = hasGroup && paymentType == "booking"

    val lockState = remember(
        booking.source,
        booking.bookingChannelId,
        booking.bookingChannelName,
        booking.bookingChannelType,
        booking.bookingChannelSettlementMethods,
        booking.channelPayoutRecorded,
        isGroupRoomCharge,
        paymentType,
    ) {
        booking.settlementLockState(
            allowDeskRemainder = !isGroupRoomCharge,
            paymentType = paymentType,
        )
    }

    val methodOptions = remember(lockState) {
        when (lockState) {
            is SettlementLockState.Restricted -> lockState.methods.map { FilterOption(it, StatusFormatter.humanize(it)) }
            is SettlementLockState.Blocked -> emptyList()
            SettlementLockState.Open -> listOf(
                FilterOption("cash", "Cash"),
                FilterOption("card", "Card"),
                FilterOption("bank_transfer", "Bank Transfer"),
                FilterOption("duitnow", "DuitNow QR"),
                FilterOption("online_banking", "Online Banking"),
            )
        }
    }

    val activeGroupRooms = remember(groupDetail) {
        groupDetail?.rooms?.filter { !it.status.equals("voided", ignoreCase = true) }.orEmpty()
    }
    val unpaidGroupRooms = remember(activeGroupRooms) {
        activeGroupRooms.filter {
            !it.paymentStatus.equals("paid", ignoreCase = true) &&
                !it.paymentStatus.equals("paid_rate", ignoreCase = true)
        }
    }
    val isGroupAlreadySettled = isGroupRoomCharge && (
        groupDetail?.group?.paymentStatus.equals("paid", ignoreCase = true) ||
            (groupDetail != null && unpaidGroupRooms.isEmpty())
        )

    var amount by remember(booking.id) {
        mutableStateOf(defaultAmount?.takeIf { it > BigDecimal.ZERO }?.toPlainString() ?: "")
    }
    var method by remember(methodOptions) {
        mutableStateOf(methodOptions.firstOrNull()?.value ?: "cash")
    }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val parsedAmount = MoneyInputParser.parsePositiveAmount(amount)

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
                text = if (isGroupRoomCharge) "Pay Group Balance" else "Record Payment",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text("Payment Category", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            FilterChipRow(
                options = listOf(
                    FilterOption("booking", if (hasGroup) "Group Room Charge" else "Room Charge"),
                    FilterOption("deposit", "Keycard Deposit"),
                    FilterOption("service", "Service / Extra"),
                    FilterOption("damage", "Damage Fee"),
                ),
                selected = paymentType,
                onSelect = { if (!isSubmitting) paymentType = it },
            )

            if (isGroupRoomCharge) {
                if (groupDetail != null) {
                    val g = groupDetail.group
                    val grpCur = g.currency.ifBlank { currency }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceSunken)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        KeyValueRow("Group #", "${g.groupNumber} (${activeGroupRooms.size} rooms)")
                        KeyValueRow("Group Total", MoneyFormatter.format(g.totalAmount, grpCur))
                        KeyValueRow("Unpaid Rooms", "${unpaidGroupRooms.size} remaining")
                        activeGroupRooms.take(5).forEach { member ->
                            KeyValueRow(
                                label = "Rm ${member.roomNumber ?: member.roomId}",
                                value = "${StatusFormatter.humanize(member.paymentStatus)} · ${MoneyFormatter.format(member.totalAmount, grpCur)}",
                            )
                        }
                    }
                    if (isGroupAlreadySettled) {
                        InlineAlertBanner(
                            message = "This group booking has no remaining unpaid rooms.",
                            tone = colors.success,
                        )
                    }
                } else {
                    InlineAlertBanner(
                        message = "Group booking: paying room charges settles the remaining balance across all unpaid rooms in this group.",
                        tone = colors.info,
                    )
                }
            }

            if (lockState is SettlementLockState.Blocked) {
                InlineAlertBanner(message = lockState.reason, tone = colors.danger)
            }
            AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                InlineAlertBanner(message = errorMessage.orEmpty(), tone = colors.danger)
            }

            if (!isGroupRoomCharge) {
                val showBalanceShortcut = paymentType == "booking" && defaultAmount != null && defaultAmount > BigDecimal.ZERO
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    enabled = !isSubmitting && lockState !is SettlementLockState.Blocked,
                    label = { Text("Amount ($currency)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = if (showBalanceShortcut) {
                        {
                            TextButton(
                                enabled = !isSubmitting,
                                onClick = { amount = defaultAmount!!.toPlainString() },
                            ) {
                                Text("Balance", color = colors.primaryOnSurface)
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (methodOptions.isNotEmpty()) {
                Text("Payment Method", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                FilterChipRow(
                    options = methodOptions,
                    selected = method,
                    onSelect = { if (!isSubmitting) method = it },
                )
            }
            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it },
                enabled = !isSubmitting,
                label = { Text("Transaction Reference (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                enabled = !isSubmitting,
                label = { Text("Notes (optional)") },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            HotelPrimaryButton(
                text = if (isSubmitting) "Recording..." else if (isGroupRoomCharge) "Pay Group Balance" else "Save Payment",
                isLoading = isSubmitting,
                enabled = !isSubmitting &&
                    lockState !is SettlementLockState.Blocked &&
                    methodOptions.isNotEmpty() &&
                    !isGroupAlreadySettled &&
                    (isGroupRoomCharge || parsedAmount != null),
                onClick = { onSubmit(amount, method, paymentType, reference, notes, submissionKey) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun InvoicePreviewSheet(
    invoice: InvoiceDto,
    canCreateInvoice: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onGenerateOfficial: () -> Unit,
    onPrint: () -> Unit,
    onSharePdf: () -> Unit,
    errorMessage: String? = null,
) {
    val colors = HotelTheme.colors
    val cur = invoice.currency.ifBlank { "MYR" }

    HotelModalSheetContainer(
        dismissible = !isLoading,
        onDismiss = { if (!isLoading) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invoice.invoiceNumber.ifBlank { "Invoice Preview" },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = invoice.billingName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                StatusPill(
                    label = StatusFormatter.humanize(invoice.status),
                    tone = StatusFormatter.paymentTone(invoice.status, colors),
                )
            }

            AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                InlineAlertBanner(message = errorMessage.orEmpty(), tone = colors.danger)
            }

            HorizontalDivider(color = colors.borderSubtle)
            KeyValueRow("Issue Date", DateFormatter.formatDate(invoice.issueDate))
            invoice.dueDate?.let { KeyValueRow("Due Date", DateFormatter.formatDate(it)) }
            invoice.roomNumber?.takeIf { it.isNotBlank() }?.let { roomNum ->
                val roomLabel = listOfNotNull(
                    roomNum,
                    invoice.roomType?.takeIf { it.isNotBlank() }?.let { "($it)" },
                ).joinToString(" ")
                KeyValueRow("Room", roomLabel)
            }
            val dates = DateFormatter.formatDateRange(invoice.checkInDate, invoice.checkOutDate)
            if (dates.isNotBlank()) KeyValueRow("Stay Dates", dates)
            invoice.numberOfNights?.let { KeyValueRow("Nights", it.toString()) }

            HorizontalDivider(color = colors.borderSubtle)
            KeyValueRow("Subtotal", MoneyFormatter.format(invoice.subtotal, cur))
            KeyValueRow("Taxes & Fees", MoneyFormatter.format(invoice.taxAmount, cur))
            if (invoice.discountAmount > BigDecimal.ZERO) {
                KeyValueRow("Discount", "-${MoneyFormatter.format(invoice.discountAmount, cur)}")
            }
            KeyValueRow("Total", MoneyFormatter.format(invoice.totalAmount, cur))
            KeyValueRow("Paid", MoneyFormatter.format(invoice.paidAmount, cur), valueColor = colors.success.fg)
            KeyValueRow(
                "Balance Due",
                MoneyFormatter.format(invoice.balanceDue, cur),
                valueColor = if (invoice.balanceDue > BigDecimal.ZERO) colors.danger.fg else colors.success.fg,
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HotelPrimaryButton(
                    text = "Print / Save PDF",
                    icon = Icons.Outlined.Print,
                    onClick = onPrint,
                    modifier = Modifier.weight(1f),
                )
                HotelOutlinedButton(
                    text = "Share PDF",
                    icon = Icons.Outlined.Share,
                    onClick = onSharePdf,
                    modifier = Modifier.weight(1f),
                )
            }

            if (canCreateInvoice && (invoice.id <= 0L || invoice.invoiceNumber.isBlank())) {
                HotelOutlinedButton(
                    text = if (isLoading) "Generating..." else "Generate Official Invoice",
                    isLoading = isLoading,
                    onClick = onGenerateOfficial,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun DepositActionDialog(
    isForfeit: Boolean,
    heldDeposit: BigDecimal,
    currency: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onConfirm: (amount: BigDecimal, method: String, reasonOrNote: String, reference: String) -> Unit,
    onDismiss: () -> Unit,
    refundMethodOptions: List<String> = emptyList(),
) {
    val colors = HotelTheme.colors
    val methodChips = remember(refundMethodOptions) {
        val list = if (refundMethodOptions.isNotEmpty()) {
            refundMethodOptions
        } else {
            listOf("cash", "card", "bank_transfer", "duitnow", "online_banking")
        }
        list.distinct().map { FilterOption(it, StatusFormatter.humanize(it)) }
    }
    var amountText by remember(heldDeposit) {
        mutableStateOf(heldDeposit.stripTrailingZeros().toPlainString())
    }
    var method by remember(methodChips) {
        mutableStateOf(methodChips.firstOrNull()?.value ?: "cash")
    }
    var reasonOrNote by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }

    val parsedAmount = MoneyInputParser.parsePositiveAmount(amountText, maxAmount = heldDeposit)
    val trimmedNoteLen = reasonOrNote.trim().length
    val reasonValid = trimmedNoteLen in 3..500

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        containerColor = colors.surfaceOverlay,
        title = {
            Text(
                text = if (isForfeit) "Forfeit Held Deposit" else "Refund Keycard Deposit",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Remaining held deposit: ${MoneyFormatter.format(heldDeposit, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                if (!errorMessage.isNullOrBlank()) {
                    InlineAlertBanner(message = errorMessage, tone = colors.danger)
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    enabled = !isSubmitting,
                    label = { Text("Amount ($currency, max ${heldDeposit.toPlainString()})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = {
                        TextButton(
                            enabled = !isSubmitting,
                            onClick = { amountText = heldDeposit.stripTrailingZeros().toPlainString() },
                        ) {
                            Text("Full", color = colors.primaryOnSurface)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isForfeit) {
                    FilterChipRow(
                        options = methodChips,
                        selected = method,
                        onSelect = { if (!isSubmitting) method = it },
                    )
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        enabled = !isSubmitting,
                        label = { Text("Receipt / Reference (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = hotelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = reasonOrNote,
                    onValueChange = { reasonOrNote = it },
                    enabled = !isSubmitting,
                    label = { Text(if (isForfeit) "Forfeit Reason (3-500 chars)" else "Refund Note (3-500 chars)") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (reasonOrNote.isNotEmpty() && !reasonValid) {
                    Text(
                        text = "Enter 3 to 500 characters.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.warning.fg,
                    )
                }
            }
        },
        confirmButton = {
            HotelPrimaryButton(
                text = if (isSubmitting) "Processing..." else if (isForfeit) "Forfeit Deposit" else "Refund Deposit",
                isLoading = isSubmitting,
                enabled = !isSubmitting && parsedAmount != null && reasonValid,
                containerColor = if (isForfeit) colors.danger.fg else colors.primaryMain,
                contentColor = colors.primaryContrastText,
                onClick = {
                    val amt = parsedAmount ?: return@HotelPrimaryButton
                    onConfirm(amt, method, reasonOrNote, reference)
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
