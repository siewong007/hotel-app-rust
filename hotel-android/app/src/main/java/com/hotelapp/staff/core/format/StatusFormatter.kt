package com.hotelapp.staff.core.format

import com.hotelapp.staff.core.design.HotelExtendedColors
import com.hotelapp.staff.core.design.StatusTone
import java.util.Locale

object StatusFormatter {

    fun humanize(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown"
        return when (raw.lowercase(Locale.US)) {
            "auto_checked_in" -> "Auto Checked In"
            "checked_in" -> "Checked In"
            "checked_out" -> "Checked Out"
            "late_checkout" -> "Late Check-Out"
            "pending_payment" -> "Pending Payment"
            "pending_confirmation" -> "Pending Confirmation"
            "no_show" -> "No Show"
            "reserved_dirty" -> "Reserved (Dirty)"
            "out_of_order" -> "Out of Order"
            "checkout_clean" -> "Checkout Clean"
            "maintenance_followup" -> "Maintenance Follow-up"
            "pending_manual_review" -> "Pending Review"
            "automated_review" -> "Auto Review"
            "in_review" -> "In Review"
            "additional_information_required" -> "Resubmission Req."
            "in_progress" -> "In Progress"
            "on_hold" -> "On Hold"
            "unpaid_deposit" -> "Deposit Unpaid"
            "hvac" -> "HVAC"
            else -> raw.split('_')
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.lowercase(Locale.US).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                    }
                }
        }
    }

    fun bookingTone(status: String?, colors: HotelExtendedColors): StatusTone =
        when (status?.lowercase(Locale.US)) {
            "checked_in", "auto_checked_in" -> colors.success
            "late_checkout" -> colors.orange
            "confirmed" -> colors.info
            "pending", "pending_payment", "pending_confirmation" -> colors.warning
            "checked_out", "completed" -> colors.neutral
            "cancelled", "voided", "no_show" -> colors.danger
            else -> colors.neutral
        }

    fun roomTone(status: String?, colors: HotelExtendedColors): StatusTone =
        when (status?.lowercase(Locale.US)) {
            "available" -> colors.success
            "occupied" -> colors.info
            "reserved" -> colors.violet
            "cleaning" -> colors.warning
            "dirty", "reserved_dirty" -> colors.orange
            "maintenance", "out_of_order" -> colors.danger
            else -> colors.neutral
        }

    fun taskTone(status: String?, colors: HotelExtendedColors): StatusTone =
        when (status?.lowercase(Locale.US)) {
            "pending", "open" -> colors.warning
            "in_progress" -> colors.info
            "on_hold" -> colors.orange
            "completed", "resolved" -> colors.success
            "closed", "void" -> colors.neutral
            else -> colors.neutral
        }

    fun priorityTone(priority: String?, colors: HotelExtendedColors): StatusTone =
        when (priority?.lowercase(Locale.US)) {
            "urgent", "critical" -> colors.danger
            "high" -> colors.orange
            "medium", "normal" -> colors.info
            "low" -> colors.neutral
            else -> colors.neutral
        }

    fun paymentTone(paymentStatus: String?, colors: HotelExtendedColors): StatusTone =
        when (paymentStatus?.lowercase(Locale.US)) {
            "paid", "completed" -> colors.success
            "partial", "unpaid_deposit" -> colors.warning
            "unpaid" -> colors.danger
            "refunded", "voided" -> colors.neutral
            else -> colors.neutral
        }

    fun ekycTone(status: String?, colors: HotelExtendedColors): StatusTone =
        when (status?.lowercase(Locale.US)) {
            "approved", "verified" -> colors.success
            "in_review", "under_review" -> colors.info
            "submitted", "pending", "automated_review", "pending_manual_review", "additional_information_required" -> colors.warning
            "on_hold" -> colors.orange
            "escalated", "rejected", "expired", "void" -> colors.danger
            else -> colors.neutral
        }
}
