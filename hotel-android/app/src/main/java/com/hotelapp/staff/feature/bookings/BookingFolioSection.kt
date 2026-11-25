package com.hotelapp.staff.feature.bookings

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelModalSheetContainer
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.KeyValueRow
import com.hotelapp.staff.core.design.StatusPill
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.data.dto.BookingPaymentRecordDto
import com.hotelapp.staff.data.dto.InvoiceDto
import com.hotelapp.staff.data.dto.PaymentWorkflowSummaryDto
import java.math.BigDecimal

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
) {
    val colors = HotelTheme.colors
    HotelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Payments & Invoice",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                summary.warnings.forEach { warning ->
                    StatusPill(label = warning, tone = colors.warning)
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
                    text = "Record Payment",
                    icon = Icons.Outlined.Payments,
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1f),
                )
            }
            HotelOutlinedButton(
                text = "Invoice PDF",
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                isLoading = isLoadingInvoice,
                onClick = onOpenInvoice,
                modifier = Modifier.weight(1f),
            )
        }

        if (payments.isNotEmpty()) {
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
                        Text(
                            text = MoneyFormatter.format(row.totalAmount, currency),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (row.voidedAt != null) colors.textMuted else colors.textPrimary,
                        )
                        val statusLabel = if (row.voidedAt != null) "Voided" else StatusFormatter.humanize(row.paymentStatus ?: "completed")
                        val tone = if (row.voidedAt != null) colors.danger else StatusFormatter.paymentTone(row.paymentStatus ?: "paid", colors)
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
    defaultAmount: BigDecimal?,
    currency: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (amountText: String, method: String, reference: String, notes: String) -> Unit,
) {
    val colors = HotelTheme.colors
    var amount by remember {
        mutableStateOf(defaultAmount?.takeIf { it > BigDecimal.ZERO }?.toPlainString() ?: "")
    }
    var method by remember { mutableStateOf("cash") }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    HotelModalSheetContainer(onDismiss = { if (!isSubmitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Record Booking Payment",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount ($currency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = if (defaultAmount != null && defaultAmount > BigDecimal.ZERO) {
                    {
                        TextButton(onClick = { amount = defaultAmount.toPlainString() }) {
                            Text("Balance", color = colors.primaryOnSurface)
                        }
                    }
                } else null,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Payment Method", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            FilterChipRow(
                options = listOf(
                    FilterOption("cash", "Cash"),
                    FilterOption("card", "Card"),
                    FilterOption("bank_transfer", "Bank Transfer"),
                    FilterOption("duitnow", "DuitNow QR"),
                    FilterOption("online_banking", "Online Banking"),
                ),
                selected = method,
                onSelect = { method = it },
            )
            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it },
                label = { Text("Transaction Reference (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                colors = hotelTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            HotelPrimaryButton(
                text = if (isSubmitting) "Recording..." else "Save Payment",
                isLoading = isSubmitting,
                enabled = (amount.trim().toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = { onSubmit(amount, method, reference, notes) },
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
) {
    val colors = HotelTheme.colors
    val cur = invoice.currency.ifBlank { "MYR" }

    HotelModalSheetContainer(onDismiss = onDismiss) {
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

            HorizontalDivider(color = colors.borderSubtle)
            KeyValueRow("Issue Date", DateFormatter.formatDate(invoice.issueDate))
            invoice.dueDate?.let { KeyValueRow("Due Date", DateFormatter.formatDate(it)) }
            invoice.roomNumber?.let { KeyValueRow("Room", "$it (${invoice.roomType.orEmpty()})".trim()) }
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

            if (canCreateInvoice && (invoice.id <= 0L || invoice.status.equals("draft", ignoreCase = true))) {
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
