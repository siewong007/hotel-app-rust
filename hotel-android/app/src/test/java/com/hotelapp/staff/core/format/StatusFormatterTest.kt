package com.hotelapp.staff.core.format

import com.hotelapp.staff.core.design.DarkHotelColors
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusFormatterTest {

    @Test
    fun humanize_mapsKnownAndUnknownTokens() {
        assertEquals("Auto Checked In", StatusFormatter.humanize("auto_checked_in"))
        assertEquals("Reserved (Dirty)", StatusFormatter.humanize("reserved_dirty"))
        assertEquals("HVAC", StatusFormatter.humanize("hvac"))
        assertEquals("Deposit Unpaid", StatusFormatter.humanize("unpaid_deposit"))
        assertEquals("Custom Field Name", StatusFormatter.humanize("custom_field_name"))
        assertEquals("Pending Review", StatusFormatter.humanize("pending_manual_review"))
        assertEquals("Resubmission Req.", StatusFormatter.humanize("additional_information_required"))
        assertEquals("PayPal", StatusFormatter.humanize("paypal"))
        assertEquals("PayPal", StatusFormatter.humanize("PayPal"))
        assertEquals("Bank Transfer", StatusFormatter.humanize("Bank Transfer"))
        assertEquals("Bank Transfer", StatusFormatter.humanize("bank_transfer"))
        assertEquals("DuitNow", StatusFormatter.humanize("duitnow"))
        assertEquals("Online Payment", StatusFormatter.humanize("online_payment"))
        assertEquals("Booking.com", StatusFormatter.humanize("Booking.com"))
        assertEquals("Unknown", StatusFormatter.humanize(null))
        assertEquals("Unknown", StatusFormatter.humanize("  "))
    }

    @Test
    fun tones_mapExpectedSemanticStatuses() {
        val c = DarkHotelColors
        assertEquals(c.success, StatusFormatter.bookingTone("checked_in", c))
        assertEquals(c.info, StatusFormatter.bookingTone("confirmed", c))
        assertEquals(c.danger, StatusFormatter.bookingTone("voided", c))

        assertEquals(c.success, StatusFormatter.roomTone("available", c))
        assertEquals(c.orange, StatusFormatter.roomTone("reserved_dirty", c))
        assertEquals(c.danger, StatusFormatter.roomTone("out_of_order", c))

        assertEquals(c.warning, StatusFormatter.taskTone("pending", c))
        assertEquals(c.success, StatusFormatter.taskTone("resolved", c))

        assertEquals(c.danger, StatusFormatter.priorityTone("critical", c))
        assertEquals(c.orange, StatusFormatter.priorityTone("high", c))

        assertEquals(c.success, StatusFormatter.paymentTone("paid", c))
        assertEquals(c.warning, StatusFormatter.paymentTone("pending", c))
        assertEquals(c.danger, StatusFormatter.paymentTone("failed", c))
        assertEquals(c.danger, StatusFormatter.paymentTone("unpaid", c))
        assertEquals(c.danger, StatusFormatter.bookingTone("comp_void", c))
        assertEquals(c.info, StatusFormatter.bookingTone("fully_complimentary", c))

        assertEquals(c.success, StatusFormatter.ekycTone("verified", c))
        assertEquals(c.info, StatusFormatter.ekycTone("in_review", c))
        assertEquals(c.info, StatusFormatter.ekycTone("under_review", c))
        assertEquals(c.warning, StatusFormatter.ekycTone("pending_manual_review", c))
        assertEquals(c.warning, StatusFormatter.ekycTone("additional_information_required", c))
        assertEquals(c.orange, StatusFormatter.ekycTone("on_hold", c))
        assertEquals(c.danger, StatusFormatter.ekycTone("escalated", c))
    }

    @Test
    fun humanize_handlesHyphensAndPreservesAcronyms_andTonesTrimWhitespace() {
        assertEquals("VIP", StatusFormatter.humanize("vip"))
        assertEquals("VIP Guest", StatusFormatter.humanize("vip_guest"))
        assertEquals("eKYC Review", StatusFormatter.humanize("ekyc-review"))
        assertEquals("OTA Channel", StatusFormatter.humanize("ota-channel"))
        assertEquals("Guest ID", StatusFormatter.humanize("guest_id"))
        assertEquals("QR Checkin", StatusFormatter.humanize("qr_checkin"))
        assertEquals("2FA Enabled", StatusFormatter.humanize("2fa_enabled"))

        val c = DarkHotelColors
        assertEquals(c.success, StatusFormatter.bookingTone(" checked_in ", c))
        assertEquals(c.danger, StatusFormatter.roomTone("  out_of_order ", c))
        assertEquals(c.info, StatusFormatter.taskTone(" in_progress ", c))
        assertEquals(c.danger, StatusFormatter.priorityTone(" critical ", c))
        assertEquals(c.success, StatusFormatter.paymentTone(" paid ", c))
        assertEquals(c.orange, StatusFormatter.ekycTone(" on_hold ", c))
    }
}
