package com.hotelapp.staff.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EkycDashboardMetricsDto(
    @SerialName("total_submitted") val totalSubmitted: Long = 0,
    @SerialName("pending_review") val pendingReview: Long = 0,
    @SerialName("under_manual_review") val underManualReview: Long = 0,
    val approved: Long = 0,
    val rejected: Long = 0,
    @SerialName("resubmission_required") val resubmissionRequired: Long = 0,
    @SerialName("escalated_high_risk") val escalatedHighRisk: Long = 0,
    @SerialName("average_processing_minutes") val averageProcessingMinutes: Double? = null,
    @SerialName("nearing_sla") val nearingSla: Long = 0,
    @SerialName("daily_trend") val dailyTrend: Long = 0,
    @SerialName("weekly_trend") val weeklyTrend: Long = 0,
    @SerialName("monthly_trend") val monthlyTrend: Long = 0,
)

@Serializable
data class EkycApplicationSummaryDto(
    val id: Long,
    @SerialName("application_id") val applicationId: String = "",
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("guest_id") val guestId: Long? = null,
    val status: String = "",
    @SerialName("assigned_reviewer_id") val assignedReviewerId: Long? = null,
    @SerialName("assigned_reviewer_name") val assignedReviewerName: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("email_masked") val emailMasked: String? = null,
    @SerialName("phone_masked") val phoneMasked: String? = null,
    @SerialName("id_type") val idType: String? = null,
    @SerialName("id_number_masked") val idNumberMasked: String? = null,
    val nationality: String? = null,
    val country: String? = null,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("provider_verification_result") val providerVerificationResult: String? = null,
    @SerialName("manual_review_required") val manualReviewRequired: Boolean = false,
    @SerialName("risk_level") val riskLevel: String = "low",
    @SerialName("risk_score") val riskScore: Int = 0,
    @SerialName("triggered_risk_rules") val triggeredRiskRules: List<String> = emptyList(),
    @SerialName("recommended_action") val recommendedAction: String? = null,
    @SerialName("potential_duplicate") val potentialDuplicate: Boolean = false,
    @SerialName("fraud_suspected") val fraudSuspected: Boolean = false,
    @SerialName("self_checkin_enabled") val selfCheckinEnabled: Boolean = false,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("nearing_sla") val nearingSla: Boolean = false,
    @SerialName("overdue_sla") val overdueSla: Boolean = false,
    @SerialName("next_arrival_date") val nextArrivalDate: String? = null,
    @SerialName("arrival_imminent") val arrivalImminent: Boolean = false,
    val version: Int = 1,
)

@Serializable
data class EkycAdminListResponseDto(
    val data: List<EkycApplicationSummaryDto> = emptyList(),
    val metrics: EkycDashboardMetricsDto = EkycDashboardMetricsDto(),
    val total: Long = 0,
    val page: Long = 1,
    @SerialName("page_size") val pageSize: Long = 20,
    @SerialName("total_pages") val totalPages: Long = 1,
)

@Serializable
data class EkycDocumentAvailabilityDto(
    @SerialName("id_front") val idFront: Boolean = false,
    @SerialName("id_back") val idBack: Boolean = false,
    val selfie: Boolean = false,
    @SerialName("proof_of_address") val proofOfAddress: Boolean = false,
)

@Serializable
data class EkycFieldComparisonDto(
    val field: String = "",
    @SerialName("submitted_value") val submittedValue: String? = null,
    @SerialName("extracted_value") val extractedValue: String? = null,
    val matches: Boolean = true,
)

@Serializable
data class EkycDecisionHistoryDto(
    val id: Long,
    @SerialName("application_id") val applicationId: Long = 0,
    @SerialName("actor_id") val actorId: Long? = null,
    @SerialName("actor_name") val actorName: String? = null,
    val action: String = "",
    @SerialName("from_status") val fromStatus: String? = null,
    @SerialName("to_status") val toStatus: String? = null,
    @SerialName("reason_code") val reasonCode: String? = null,
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class EkycNoteDto(
    val id: Long,
    @SerialName("application_id") val applicationId: Long = 0,
    @SerialName("note_type") val noteType: String = "",
    val body: String = "",
    @SerialName("customer_visible") val customerVisible: Boolean = false,
    @SerialName("created_by") val createdBy: Long = 0,
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class EkycApplicationDetailDto(
    val summary: EkycApplicationSummaryDto,
    @SerialName("date_of_birth_masked") val dateOfBirthMasked: String? = null,
    @SerialName("current_address_masked") val currentAddressMasked: String? = null,
    @SerialName("id_issuing_country") val idIssuingCountry: String? = null,
    @SerialName("id_issue_date") val idIssueDate: String? = null,
    @SerialName("id_expiry_date") val idExpiryDate: String? = null,
    @SerialName("document_authenticity_result") val documentAuthenticityResult: String? = null,
    @SerialName("face_match_score") val faceMatchScore: Double? = null,
    @SerialName("face_match_passed") val faceMatchPassed: Boolean? = null,
    @SerialName("liveness_score") val livenessScore: Double? = null,
    @SerialName("liveness_passed") val livenessPassed: Boolean? = null,
    @SerialName("duplicate_check_result") val duplicateCheckResult: String? = null,
    @SerialName("watchlist_result") val watchlistResult: String? = null,
    @SerialName("verification_notes") val verificationNotes: String? = null,
    @SerialName("customer_message") val customerMessage: String? = null,
    @SerialName("decision_reason_code") val decisionReasonCode: String? = null,
    @SerialName("decision_reason") val decisionReason: String? = null,
    val documents: EkycDocumentAvailabilityDto = EkycDocumentAvailabilityDto(),
    val differences: List<EkycFieldComparisonDto> = emptyList(),
    val history: List<EkycDecisionHistoryDto> = emptyList(),
    val notes: List<EkycNoteDto> = emptyList(),
)

@Serializable
data class EkycReviewActionRequestDto(
    val action: String,
    @SerialName("expected_version") val expectedVersion: Int,
    @SerialName("reason_code") val reasonCode: String? = null,
    val reason: String? = null,
    @SerialName("customer_message") val customerMessage: String? = null,
    val note: String? = null,
    @SerialName("self_checkin_enabled") val selfCheckinEnabled: Boolean? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
)

@Serializable
data class EkycReasonCodeDto(
    val code: String,
    val label: String,
    val category: String = "",
    @SerialName("requires_details") val requiresDetails: Boolean = false,
    @SerialName("customer_message_template") val customerMessageTemplate: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class EkycSensitiveRevealRequestDto(
    val field: String,
    val reason: String,
)

@Serializable
data class EkycSensitiveRevealResponseDto(
    val field: String,
    val value: String? = null,
)
