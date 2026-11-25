package com.hotelapp.staff.data.dto

import com.hotelapp.staff.core.network.HotelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class NewDtosSerializationTest {

    @Test
    fun `parses payment workflow summary with rust_decimal strings`() {
        val json = """
            {
              "booking_id": 42,
              "booking_status": "confirmed",
              "payment_status": "partial",
              "total_amount": "500.00",
              "total_paid": "200.00",
              "total_refunded": "0.00",
              "balance_due": "300.00",
              "deposit_collected": "100.00",
              "deposit_refunded": "0",
              "deposit_forfeited": "0",
              "has_failed_payment": false,
              "next_action": "collect_balance",
              "warnings": ["Deposit held"]
            }
        """.trimIndent()
        val decoded = HotelJson.decodeFromString<PaymentWorkflowSummaryDto>(json)
        assertEquals(42L, decoded.bookingId)
        assertEquals(BigDecimal("300.00"), decoded.balanceDue)
        assertEquals(listOf("Deposit held"), decoded.warnings)
    }

    @Test
    fun `parses global search response groups`() {
        val json = """
            {
              "query": "101",
              "groups": [
                {
                  "type": "rooms",
                  "label": "Rooms",
                  "results": [
                    { "id": 1, "title": "Room 101", "subtitle": "Deluxe", "route": "/rooms" }
                  ]
                }
              ]
            }
        """.trimIndent()
        val decoded = HotelJson.decodeFromString<GlobalSearchResponseDto>(json)
        assertEquals("101", decoded.query)
        assertEquals(1, decoded.groups.single().results.size)
    }

    @Test
    fun `parses night audit preview and guest relations overview`() {
        val auditJson = """
            {
              "audit_date": "2026-11-25",
              "can_run": true,
              "already_run": false,
              "total_unposted": 4,
              "estimated_revenue": "1840.50",
              "room_snapshot": { "total": 20, "available": 6, "occupied": 12, "reserved": 1, "maintenance": 0, "dirty": 1 }
            }
        """.trimIndent()
        val audit = HotelJson.decodeFromString<NightAuditPreviewDto>(auditJson)
        assertTrue(audit.canRun)
        assertEquals(BigDecimal("1840.50"), audit.estimatedRevenue)
        assertEquals(20, audit.roomSnapshot.total)

        val grJson = """
            {
              "arrivals": { "count": 5, "items": [] },
              "in_house": { "count": 10, "items": [] },
              "departures": { "count": 2, "items": [] },
              "vip_arrivals": { "count": 1, "items": [] },
              "support": { "open": 3, "waiting_for_staff": 2, "items": [] },
              "follow_ups": { "count": 4, "items": [] }
            }
        """.trimIndent()
        val gr = HotelJson.decodeFromString<GuestRelationsOverviewDto>(grJson)
        assertEquals(1L, gr.vipArrivals.count)
        assertEquals(2L, gr.support?.waitingForStaff)
        assertEquals(4L, gr.followUps.count)
    }

    @Test
    fun `parses invoice preview and passkey login start`() {
        val invJson = """
            {
              "invoice": {
                "id": 7,
                "uuid": "550e8400-e29b-41d4-a716-446655440000",
                "invoice_number": "INV-2026-0007",
                "booking_id": 42,
                "billing_name": "Amina Rahman",
                "issue_date": "2026-11-25",
                "subtotal": "450.00",
                "tax_amount": "50.00",
                "discount_amount": "0.00",
                "total_amount": "500.00",
                "paid_amount": "500.00",
                "balance_due": "0.00",
                "currency": "MYR",
                "status": "paid",
                "created_at": "2026-11-25T10:00:00Z",
                "updated_at": "2026-11-25T10:00:00Z"
              }
            }
        """.trimIndent()
        val inv = HotelJson.decodeFromString<InvoicePreviewDto>(invJson)
        assertEquals("INV-2026-0007", inv.invoice.invoiceNumber)
        assertEquals(BigDecimal("500.00"), inv.invoice.totalAmount)

        val pkJson = """
            {
              "challenge": "AQIDBA==",
              "allowCredentials": [{ "id": "AQID", "type": "public-key" }]
            }
        """.trimIndent()
        val pk = HotelJson.decodeFromString<PasskeyLoginStartResponseDto>(pkJson)
        assertEquals("AQIDBA==", pk.challenge)
        assertEquals("AQID", pk.allowCredentials.single().id)
    }
}
