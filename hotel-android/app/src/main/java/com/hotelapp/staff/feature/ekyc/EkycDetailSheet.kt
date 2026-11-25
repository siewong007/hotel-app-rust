package com.hotelapp.staff.feature.ekyc

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.design.FilterChipRow
import com.hotelapp.staff.core.design.FilterOption
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
import com.hotelapp.staff.data.dto.EkycApplicationDetailDto
import com.hotelapp.staff.data.dto.EkycReasonCodeDto
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EkycDetailSheet(
    session: StaffSession,
    detail: EkycApplicationDetailDto?,
    isLoading: Boolean,
    isMutating: Boolean,
    error: String?,
    reasonCodes: List<EkycReasonCodeDto>,
    onDismiss: () -> Unit,
    onAction: (
        id: Long,
        expectedVersion: Int,
        action: String,
        reasonCode: String?,
        reason: String?,
        customerMessage: String?,
        selfCheckinEnabled: Boolean?,
    ) -> Unit,
) {
    val colors = HotelTheme.colors
    var actionDialogMode by remember { mutableStateOf<String?>(null) }
    var selfCheckinOnApprove by remember(detail?.summary?.id) {
        mutableStateOf(true)
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
            when {
                isLoading -> HotelLogoLoader(
                    message = "Loading eKYC application...",
                    delayMs = 0L,
                    modifier = Modifier.height(180.dp),
                )
                detail != null -> {
                    val s = detail.summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.fullName ?: s.applicationId,
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "${s.applicationId} · Risk: ${s.riskLevel.uppercase()} (${s.riskScore})",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        StatusPill(
                            label = StatusFormatter.humanize(s.status),
                            tone = StatusFormatter.ekycTone(s.status, colors),
                        )
                    }

                    if (error != null) {
                        InlineAlertBanner(message = error, tone = colors.danger)
                    }

                    HorizontalDivider(color = colors.borderSubtle)
                    s.idType?.let { KeyValueRow("Document Type", StatusFormatter.humanize(it)) }
                    s.idNumberMasked?.let { KeyValueRow("Document #", it) }
                    detail.idIssuingCountry?.let { KeyValueRow("Issuing Country", it) }
                    detail.idExpiryDate?.let { KeyValueRow("Expiry Date", DateFormatter.formatDate(it)) }
                    detail.dateOfBirthMasked?.let { KeyValueRow("Date of Birth", it) }
                    detail.faceMatchScore?.let {
                        val passed = if (detail.faceMatchPassed == true) "Passed" else "Check"
                        KeyValueRow("Face Match", "${String.format(Locale.US, "%.1f", it)}% ($passed)")
                    }
                    detail.livenessScore?.let {
                        val passed = if (detail.livenessPassed == true) "Passed" else "Check"
                        KeyValueRow("Liveness", "${String.format(Locale.US, "%.1f", it)}% ($passed)")
                    }
                    detail.documentAuthenticityResult?.let {
                        KeyValueRow("Authenticity", StatusFormatter.humanize(it))
                    }
                    detail.duplicateCheckResult?.let {
                        KeyValueRow("Duplicate Check", StatusFormatter.humanize(it))
                    }

                    if (detail.differences.any { !it.matches }) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Text("OCR vs Submitted Mismatches", style = MaterialTheme.typography.titleSmall, color = colors.warning.fg)
                        detail.differences.filter { !it.matches }.forEach { diff ->
                            KeyValueRow(
                                label = StatusFormatter.humanize(diff.field),
                                value = "${diff.submittedValue ?: "-"} ≠ ${diff.extractedValue ?: "-"}",
                                valueColor = colors.warning.fg,
                            )
                        }
                    }

                    val normalizedStatus = s.status.lowercase()
                    val isTerminal = normalizedStatus in setOf("approved", "rejected", "expired", "void")
                    val canAssign = session.hasAnyPermission("ekyc:assign", "ekyc:manage")
                    val canApprove = session.hasAnyPermission("ekyc:approve", "ekyc:manage")
                    val canReject = session.hasAnyPermission("ekyc:reject", "ekyc:manage")
                    val canResubmit = session.hasAnyPermission("ekyc:request_resubmission", "ekyc:manage")
                    val canEscalate = session.hasAnyPermission("ekyc:escalate", "ekyc:manage")

                    val canClaimNow = canAssign &&
                        s.assignedReviewerId == null &&
                        normalizedStatus in setOf("submitted", "pending", "automated_review", "pending_manual_review", "escalated")
                    val canDecideNow = normalizedStatus in setOf(
                        "submitted",
                        "pending",
                        "automated_review",
                        "pending_manual_review",
                        "in_review",
                        "under_review",
                        "escalated",
                    )

                    if (!isTerminal && (canClaimNow || (canDecideNow && canApprove) || (canDecideNow && canReject) || canResubmit || canEscalate)) {
                        HorizontalDivider(color = colors.borderSubtle)
                        if (canApprove && canDecideNow) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Enable kiosk / self check-in on approval",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = selfCheckinOnApprove,
                                    onCheckedChange = { selfCheckinOnApprove = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.primaryContrastText,
                                        checkedTrackColor = colors.primaryMain,
                                    ),
                                )
                            }
                            HotelPrimaryButton(
                                text = "Approve Verification",
                                isLoading = isMutating,
                                containerColor = colors.success.fg,
                                contentColor = colors.primaryContrastText,
                                onClick = { actionDialogMode = "approve" },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (canClaimNow) {
                                HotelOutlinedButton(
                                    text = "Claim Review",
                                    enabled = !isMutating,
                                    onClick = { onAction(s.id, s.version, "claim", null, null, null, null) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (canResubmit) {
                                HotelOutlinedButton(
                                    text = "Resubmit",
                                    enabled = !isMutating,
                                    onClick = { actionDialogMode = "request_resubmission" },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (canEscalate) {
                                HotelOutlinedButton(
                                    text = "Escalate",
                                    enabled = !isMutating,
                                    onClick = { actionDialogMode = "escalate" },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (canReject && canDecideNow) {
                                HotelOutlinedButton(
                                    text = "Reject",
                                    isDestructive = true,
                                    enabled = !isMutating,
                                    onClick = { actionDialogMode = "reject" },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    if (detail.history.isNotEmpty()) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Text("Decision History", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        detail.history.take(5).forEach { h ->
                            KeyValueRow(
                                label = StatusFormatter.humanize(h.action),
                                value = "${h.actorName ?: "System"} · ${DateFormatter.formatRelativeTimestamp(h.createdAt)}",
                            )
                        }
                    }
                }
                error != null -> InlineAlertBanner(message = error, tone = colors.danger)
            }
        }
    }

    val targetDetail = detail
    val mode = actionDialogMode
    if (mode != null && targetDetail != null) {
        val matchingCodes = reasonCodes.filter {
            when (mode) {
                "approve" -> it.category.contains("approv", ignoreCase = true)
                "reject" -> it.category.contains("reject", ignoreCase = true)
                "request_resubmission" -> it.category.contains("resub", ignoreCase = true)
                "escalate" -> it.category.contains("escal", ignoreCase = true)
                else -> false
            }
        }.ifEmpty { reasonCodes }
        var selectedCode by remember(mode, reasonCodes) { mutableStateOf(matchingCodes.firstOrNull()?.code) }
        val selectedReasonObj = matchingCodes.find { it.code == selectedCode }
        val requiresDetails = selectedReasonObj?.requiresDetails == true
        var reasonText by remember(mode) { mutableStateOf("") }
        var messageText by remember(mode) { mutableStateOf("") }
        val isConfirmValid = !selectedCode.isNullOrBlank() &&
            (!requiresDetails || reasonText.trim().length >= 5)

        AlertDialog(
            onDismissRequest = { actionDialogMode = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceOverlay,
            title = { Text(StatusFormatter.humanize(mode), color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (matchingCodes.isNotEmpty()) {
                        Text(
                            text = "Reason Code (required)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        FilterChipRow(
                            options = matchingCodes.take(6).map { FilterOption(it.code, it.label) },
                            selected = selectedCode ?: "",
                            onSelect = { selectedCode = it },
                        )
                    } else {
                        InlineAlertBanner(
                            message = "No reason codes loaded for your role (ekyc:review is required by the server).",
                            tone = colors.warning,
                        )
                    }
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = {
                            Text(
                                if (requiresDetails) {
                                    "Detailed Reason (min 5 chars required)"
                                } else {
                                    "Reason / Internal Notes (optional)"
                                },
                            )
                        },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = hotelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mode == "request_resubmission") {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            label = { Text("Message to Guest") },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            colors = hotelTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                HotelPrimaryButton(
                    text = "Confirm",
                    enabled = isConfirmValid,
                    containerColor = when (mode) {
                        "reject" -> colors.danger.fg
                        "approve" -> colors.success.fg
                        else -> colors.primaryMain
                    },
                    contentColor = colors.primaryContrastText,
                    onClick = {
                        actionDialogMode = null
                        onAction(
                            targetDetail.summary.id,
                            targetDetail.summary.version,
                            mode,
                            selectedCode,
                            reasonText,
                            messageText.takeIf { it.isNotBlank() },
                            if (mode == "approve") selfCheckinOnApprove else null,
                        )
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { actionDialogMode = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }
}
