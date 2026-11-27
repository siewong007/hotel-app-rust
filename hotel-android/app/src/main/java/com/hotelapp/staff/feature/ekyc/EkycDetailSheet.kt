package com.hotelapp.staff.feature.ekyc

import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.hotelapp.staff.BuildConfig
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
import java.util.UUID

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
        idempotencyKey: String,
        onSuccess: () -> Unit,
    ) -> Unit,
    isLoadingReasonCodes: Boolean = false,
    reasonCodesError: String? = null,
    onRetryReasonCodes: () -> Unit = {},
    onRefresh: () -> Unit = {},
    activeDocumentKind: String? = null,
    activeDocumentBytes: ByteArray? = null,
    isLoadingDocument: Boolean = false,
    documentError: String? = null,
    onOpenDocument: (String) -> Unit = {},
    onDismissDocument: () -> Unit = {},
    revealedFields: Map<String, String?> = emptyMap(),
    isRevealing: Boolean = false,
    revealError: String? = null,
    onRevealField: (field: String, reason: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    onClearRevealError: () -> Unit = {},
) {
    val colors = HotelTheme.colors
    var actionDialogMode by remember { mutableStateOf<String?>(null) }
    var showRevealDialog by remember { mutableStateOf(false) }
    var selfCheckinOnApprove by remember(detail?.summary?.id) {
        mutableStateOf(true)
    }

    HotelModalSheetContainer(
        dismissible = !isMutating,
        onDismiss = { if (!isMutating) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                isLoading && detail == null -> HotelLogoLoader(
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
                                text = "${s.applicationId} · Risk: ${s.riskLevel.uppercase()} (${s.riskScore}) · v${s.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            enabled = !isLoading && !isMutating,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh eKYC application",
                                tint = colors.textPrimary,
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

                    if (s.fraudSuspected) {
                        InlineAlertBanner(
                            message = "Fraud Suspected — verify applicant identity and uploaded documents carefully.",
                            tone = colors.danger,
                        )
                    }
                    if (s.potentialDuplicate) {
                        InlineAlertBanner(
                            message = "Potential Duplicate — review duplicate check findings before approving.",
                            tone = colors.warning,
                        )
                    }
                    val watchlist = detail.watchlistResult?.trim()
                    if (!watchlist.isNullOrBlank() &&
                        !watchlist.equals("clear", ignoreCase = true) &&
                        !watchlist.equals("none", ignoreCase = true) &&
                        !watchlist.equals("passed", ignoreCase = true)
                    ) {
                        InlineAlertBanner(
                            message = "Watchlist check: ${StatusFormatter.humanize(watchlist)}",
                            tone = colors.danger,
                        )
                    }
                    if (s.triggeredRiskRules.isNotEmpty()) {
                        InlineAlertBanner(
                            message = "Triggered risk rules: ${s.triggeredRiskRules.joinToString(", ") { StatusFormatter.humanize(it) }}",
                            tone = colors.warning,
                        )
                    }

                    HorizontalDivider(color = colors.borderSubtle)
                    KeyValueRow(
                        label = "Assigned Reviewer",
                        value = s.assignedReviewerName ?: s.assignedReviewerId?.let { "Staff #$it" } ?: "Unassigned",
                    )
                    s.idType?.let { KeyValueRow("Document Type", StatusFormatter.humanize(it)) }
                    s.idNumberMasked?.let { KeyValueRow("Document #", it) }
                    detail.idIssuingCountry?.let { KeyValueRow("Issuing Country", it) }
                    detail.idExpiryDate?.let { KeyValueRow("Expiry Date", DateFormatter.formatDate(it)) }
                    detail.dateOfBirthMasked?.let { KeyValueRow("Date of Birth", it) }
                    detail.faceMatchScore?.let {
                        val passed = if (detail.faceMatchPassed == true) "Passed" else "Check"
                        val pct = if (it <= 1.0) it * 100.0 else it
                        KeyValueRow("Face Match", "${String.format(Locale.US, "%.1f", pct)}% ($passed)")
                    }
                    detail.livenessScore?.let {
                        val passed = if (detail.livenessPassed == true) "Passed" else "Check"
                        val pct = if (it <= 1.0) it * 100.0 else it
                        KeyValueRow("Liveness", "${String.format(Locale.US, "%.1f", pct)}% ($passed)")
                    }
                    detail.documentAuthenticityResult?.let {
                        KeyValueRow("Authenticity", StatusFormatter.humanize(it))
                    }
                    detail.duplicateCheckResult?.let {
                        KeyValueRow("Duplicate Check", StatusFormatter.humanize(it))
                    }
                    detail.verificationNotes?.takeIf { it.isNotBlank() }?.let {
                        KeyValueRow("Verification Notes", it)
                    }
                    detail.decisionReason?.takeIf { it.isNotBlank() }?.let {
                        KeyValueRow("Decision Reason", it)
                    }
                    detail.customerMessage?.takeIf { it.isNotBlank() }?.let {
                        KeyValueRow("Guest Message", it)
                    }

                    val canDownloadDocs = session.hasAnyPermission("ekyc:download_documents", "ekyc:manage")
                    val docKinds = remember(detail.documents) {
                        buildList {
                            if (detail.documents.idFront) add("id_front" to "Front ID")
                            if (detail.documents.idBack) add("id_back" to "Back ID")
                            if (detail.documents.selfie) add("selfie" to "Selfie")
                            if (detail.documents.proofOfAddress) add("proof_of_address" to "Proof of Address")
                        }
                    }
                    if (canDownloadDocs && docKinds.isNotEmpty()) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Text("Uploaded Identity Documents", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 2,
                        ) {
                            docKinds.forEach { (kind, label) ->
                                HotelOutlinedButton(
                                    text = label,
                                    icon = Icons.Outlined.Badge,
                                    enabled = !isMutating,
                                    onClick = { onOpenDocument(kind) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    val canRevealSensitive = session.hasAnyPermission("ekyc:reveal_sensitive", "ekyc:manage")
                    if (canRevealSensitive) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Sensitive PII Access", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            HotelOutlinedButton(
                                text = "Reveal Field",
                                icon = Icons.Outlined.LockOpen,
                                enabled = !isMutating && !isRevealing,
                                onClick = {
                                    onClearRevealError()
                                    showRevealDialog = true
                                },
                            )
                        }
                        if (revealedFields.isNotEmpty()) {
                            revealedFields.forEach { (field, value) ->
                                KeyValueRow(
                                    label = "${StatusFormatter.humanize(field)} (Revealed)",
                                    value = value?.takeIf { it.isNotBlank() } ?: "—",
                                    valueColor = colors.warning.fg,
                                )
                            }
                        }
                    }

                    if (detail.notes.isNotEmpty()) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Text("Reviewer Notes (${detail.notes.size})", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        detail.notes.take(5).forEach { note ->
                            KeyValueRow(
                                label = note.createdByName ?: StatusFormatter.humanize(note.noteType),
                                value = note.body,
                            )
                        }
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

                    val normalizedStatus = when (val raw = s.status.trim().lowercase()) {
                        "pending" -> "submitted"
                        "under_review", "in_progress" -> "in_review"
                        "verified" -> "approved"
                        else -> raw
                    }
                    val isTerminal = normalizedStatus in setOf("approved", "rejected", "expired", "void")
                    val canReview = session.hasAnyPermission("ekyc:review", "ekyc:manage")
                    val canAssign = session.hasAnyPermission("ekyc:assign", "ekyc:manage")
                    val canApprove = session.hasAnyPermission("ekyc:approve", "ekyc:manage")
                    val canReject = session.hasAnyPermission("ekyc:reject", "ekyc:manage")
                    val canResubmit = session.hasAnyPermission("ekyc:request_resubmission", "ekyc:manage")
                    val canEscalate = session.hasAnyPermission("ekyc:escalate", "ekyc:manage")
                    val isAssignedToOther = s.assignedReviewerId != null && s.assignedReviewerId != session.user.id
                    val canActOnAssignment = !isAssignedToOther

                    val canClaimNow = canAssign &&
                        s.assignedReviewerId == null &&
                        normalizedStatus in setOf("submitted", "automated_review", "pending_manual_review", "in_review", "escalated")
                    val canDecideNow = canActOnAssignment && normalizedStatus in setOf(
                        "submitted",
                        "automated_review",
                        "pending_manual_review",
                        "in_review",
                        "escalated",
                    )
                    val canResubmitNow = canResubmit && canActOnAssignment && !isTerminal &&
                        normalizedStatus != "additional_information_required"
                    val canEscalateNow = canEscalate && canActOnAssignment && !isTerminal &&
                        normalizedStatus != "escalated"
                    val canHoldNow = canReview && canActOnAssignment &&
                        normalizedStatus in setOf("submitted", "pending_manual_review", "in_review", "escalated")
                    val canReleaseHoldNow = canReview && canActOnAssignment && normalizedStatus == "on_hold"

                    if (!isTerminal && isAssignedToOther) {
                        InlineAlertBanner(
                            message = "Currently claimed by ${s.assignedReviewerName ?: "another reviewer"}.",
                            tone = colors.info,
                        )
                    } else if (normalizedStatus == "on_hold") {
                        InlineAlertBanner(
                            message = "Application is on hold. Release the hold before approving or rejecting.",
                            tone = colors.warning,
                        )
                    }

                    if (!isTerminal && (canClaimNow || canReleaseHoldNow || (canDecideNow && canApprove) || (canDecideNow && canReject) || canResubmitNow || canEscalateNow || canHoldNow)) {
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
                                    enabled = !isMutating,
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

                        if (canReleaseHoldNow) {
                            HotelPrimaryButton(
                                text = "Release Hold",
                                isLoading = isMutating,
                                onClick = {
                                    onAction(
                                        s.id,
                                        s.version,
                                        "release_hold",
                                        null,
                                        null,
                                        null,
                                        null,
                                        UUID.randomUUID().toString(),
                                        {},
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 2,
                        ) {
                            if (canClaimNow) {
                                HotelOutlinedButton(
                                    text = "Claim Review",
                                    enabled = !isMutating,
                                    onClick = {
                                        onAction(
                                            s.id,
                                            s.version,
                                            "claim",
                                            null,
                                            null,
                                            null,
                                            null,
                                            UUID.randomUUID().toString(),
                                            {},
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (canResubmitNow) {
                                HotelOutlinedButton(
                                    text = "Resubmit",
                                    enabled = !isMutating,
                                    onClick = { actionDialogMode = "request_resubmission" },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (canEscalateNow) {
                                HotelOutlinedButton(
                                    text = "Escalate",
                                    enabled = !isMutating,
                                    onClick = { actionDialogMode = "escalate" },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (canHoldNow) {
                                HotelOutlinedButton(
                                    text = "Place On Hold",
                                    enabled = !isMutating,
                                    onClick = { actionDialogMode = "hold" },
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

    if (activeDocumentKind != null) {
        AlertDialog(
            onDismissRequest = onDismissDocument,
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceOverlay,
            title = { Text("Document · ${StatusFormatter.humanize(activeDocumentKind)}", color = colors.textPrimary) },
            text = {
                val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
                DisposableEffect(dialogWindow) {
                    if (dialogWindow != null && !BuildConfig.DEBUG) {
                        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    onDispose {}
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        isLoadingDocument -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = colors.primaryMain)
                        }
                        documentError != null -> InlineAlertBanner(message = documentError, tone = colors.danger)
                        activeDocumentBytes != null -> {
                            val bitmap = remember(activeDocumentKind, activeDocumentBytes) {
                                BitmapFactory.decodeByteArray(activeDocumentBytes, 0, activeDocumentBytes.size)?.asImageBitmap()
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = StatusFormatter.humanize(activeDocumentKind),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 380.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                InlineAlertBanner(message = "Unable to decode document image.", tone = colors.danger)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissDocument) {
                    Text("Close", color = colors.primaryOnSurface)
                }
            },
        )
    }

    if (showRevealDialog) {
        val revealOptions = remember {
            listOf(
                FilterOption("id_number", "ID Number"),
                FilterOption("full_name", "Full Name"),
                FilterOption("date_of_birth", "Date of Birth"),
                FilterOption("email", "Email"),
                FilterOption("phone", "Phone"),
                FilterOption("current_address", "Address"),
            )
        }
        var selectedField by remember { mutableStateOf("id_number") }
        var revealReason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!isRevealing) showRevealDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceOverlay,
            title = { Text("Reveal Sensitive PII", color = colors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Audited action: select the field and enter a reason (min 5 chars).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    if (!revealError.isNullOrBlank()) {
                        InlineAlertBanner(message = revealError, tone = colors.danger)
                    }
                    FilterChipRow(
                        options = revealOptions,
                        selected = selectedField,
                        onSelect = { if (!isRevealing) selectedField = it },
                    )
                    OutlinedTextField(
                        value = revealReason,
                        onValueChange = { revealReason = it },
                        enabled = !isRevealing,
                        label = { Text("Reason (min 5 chars)") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = hotelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                HotelPrimaryButton(
                    text = if (isRevealing) "Revealing..." else "Reveal",
                    isLoading = isRevealing,
                    enabled = !isRevealing && revealReason.trim().length >= 5,
                    onClick = {
                        onRevealField(selectedField, revealReason) { showRevealDialog = false }
                    },
                )
            },
            dismissButton = {
                TextButton(enabled = !isRevealing, onClick = { showRevealDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }

    val targetDetail = detail
    val mode = actionDialogMode
    if (mode != null && targetDetail != null) {
        val submissionKey = remember(mode, targetDetail.summary.id, targetDetail.summary.version) {
            UUID.randomUUID().toString()
        }
        val hasReviewPerm = session.hasAnyPermission("ekyc:review", "ekyc:manage")
        val matchingCodes = when (mode) {
            "approve" -> reasonCodes.sortedByDescending {
                it.category.contains("approv", ignoreCase = true) ||
                    it.category.equals("other", ignoreCase = true) ||
                    it.code.equals("other_requires_details", ignoreCase = true)
            }
            "escalate", "hold" -> reasonCodes.sortedByDescending {
                it.category.equals("risk", ignoreCase = true) ||
                    it.code.contains("escal", ignoreCase = true) ||
                    it.category.contains("escal", ignoreCase = true)
            }
            "reject", "request_resubmission" -> reasonCodes.sortedByDescending {
                it.category.lowercase() in setOf("document", "biometric", "identity")
            }
            else -> reasonCodes
        }
        var selectedCode by remember(mode, reasonCodes) {
            mutableStateOf(matchingCodes.firstOrNull()?.code)
        }
        val selectedReasonObj = matchingCodes.find { it.code == selectedCode }
        val requiresDetails = selectedReasonObj?.requiresDetails == true
        var reasonText by remember(mode) { mutableStateOf("") }
        var messageText by remember(mode) { mutableStateOf("") }
        LaunchedEffect(mode, selectedCode) {
            if (mode == "request_resubmission" && messageText.isBlank()) {
                val tpl = matchingCodes.find { it.code == selectedCode }?.customerMessageTemplate
                if (!tpl.isNullOrBlank()) {
                    messageText = tpl
                }
            }
        }
        val isConfirmValid = !selectedCode.isNullOrBlank() &&
            (!requiresDetails || reasonText.trim().length >= 5)

        AlertDialog(
            onDismissRequest = { if (!isMutating) actionDialogMode = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceOverlay,
            title = { Text(StatusFormatter.humanize(mode), color = colors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!error.isNullOrBlank()) {
                        InlineAlertBanner(message = error, tone = colors.danger)
                    }
                    when {
                        matchingCodes.isNotEmpty() -> {
                            Text(
                                text = "Reason Code (required)",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                            FilterChipRow(
                                options = matchingCodes.map { FilterOption(it.code, it.label) },
                                selected = selectedCode ?: "",
                                onSelect = {
                                    if (!isMutating) {
                                        val prevTpl = matchingCodes.find { c -> c.code == selectedCode }?.customerMessageTemplate
                                        selectedCode = it
                                        if (mode == "request_resubmission" && (messageText.isBlank() || messageText == prevTpl)) {
                                            messageText = matchingCodes.find { c -> c.code == it }?.customerMessageTemplate.orEmpty()
                                        }
                                    }
                                },
                            )
                        }
                        isLoadingReasonCodes -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                                Text("Loading reason codes...", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                        }
                        reasonCodesError != null -> {
                            InlineAlertBanner(message = reasonCodesError, tone = colors.danger)
                            HotelOutlinedButton(
                                text = "Retry Reason Codes",
                                enabled = !isMutating,
                                onClick = onRetryReasonCodes,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        else -> InlineAlertBanner(
                            message = if (hasReviewPerm) {
                                "No active reason codes available."
                            } else {
                                "Requires ekyc:review permission to load reason codes."
                            },
                            tone = colors.warning,
                        )
                    }
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        enabled = !isMutating,
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
                    if (requiresDetails && reasonText.trim().length < 5) {
                        Text(
                            text = "Enter at least 5 characters for this reason code (${reasonText.trim().length}/5).",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.warning.fg,
                        )
                    }
                    if (mode == "request_resubmission") {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            enabled = !isMutating,
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
                    text = if (isMutating) "Submitting..." else "Confirm",
                    isLoading = isMutating,
                    enabled = !isMutating && isConfirmValid,
                    containerColor = when (mode) {
                        "reject" -> colors.danger.fg
                        "approve" -> colors.success.fg
                        else -> colors.primaryMain
                    },
                    contentColor = colors.primaryContrastText,
                    onClick = {
                        onAction(
                            targetDetail.summary.id,
                            targetDetail.summary.version,
                            mode,
                            selectedCode,
                            reasonText,
                            messageText.takeIf { it.isNotBlank() },
                            if (mode == "approve") selfCheckinOnApprove else null,
                            submissionKey,
                        ) {
                            actionDialogMode = null
                        }
                    },
                )
            },
            dismissButton = {
                TextButton(enabled = !isMutating, onClick = { actionDialogMode = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }
}
