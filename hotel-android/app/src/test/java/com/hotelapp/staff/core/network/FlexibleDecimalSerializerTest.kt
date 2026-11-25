package com.hotelapp.staff.core.network

import com.hotelapp.staff.data.dto.BookingBoardSummaryDto
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.dto.HotelOccupancySummaryDto
import com.hotelapp.staff.data.dto.MaintenanceTicketDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class FlexibleDecimalSerializerTest {

    @Test
    fun `decodes string and numeric decimals inside BookingBoardSummaryDto`() {
        val raw = """
            {
              "arriving": 4,
              "ready_to_check_in": 3,
              "in_house": 12,
              "guests_in_house": 21,
              "departing": 2,
              "upcoming": 9,
              "normal_due": 2,
              "normal_due_amount": "345.50",
              "company_due": 1,
              "company_due_amount": 1200.0,
              "company_outstanding_months": 1
            }
        """.trimIndent()

        val dto = HotelJson.decodeFromString<BookingBoardSummaryDto>(raw)
        assertEquals(0, BigDecimal("345.50").compareTo(dto.normalDueAmount))
        assertEquals(0, BigDecimal("1200.0").compareTo(dto.companyDueAmount))
        assertEquals(4L, dto.arriving)
    }

    @Test
    fun `decodes BookingWithDetailsDto with price_per_night rename and nullable decimals`() {
        val raw = """
            {
              "id": 42,
              "booking_number": "BK-2026-0042",
              "guest_id": 7,
              "guest_name": "Aisha Rahman",
              "room_id": 101,
              "room_number": "101",
              "room_type": "Deluxe King",
              "check_in_date": "2026-11-25",
              "check_out_date": "2026-11-27",
              "price_per_night": "180.00",
              "total_amount": "360.00",
              "status": "confirmed",
              "deposit_amount": "-25.50",
              "total_paid": 0,
              "balance_due": "360.00",
              "tourism_tax_amount": null
            }
        """.trimIndent()

        val dto = HotelJson.decodeFromString<BookingWithDetailsDto>(raw)
        assertEquals(0, BigDecimal("180.00").compareTo(dto.roomRate))
        assertEquals(0, BigDecimal("360.00").compareTo(dto.totalAmount))
        assertEquals(0, BigDecimal("-25.50").compareTo(dto.depositAmount!!))
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.totalPaid!!))
        assertNull(dto.tourismTaxAmount)
    }

    @Test
    fun `decodes occupancy and maintenance decimal fields`() {
        val occRaw = """
            {
              "total_rooms": 20,
              "occupied_rooms": 15,
              "available_rooms": 5,
              "occupancy_rate": "75.00",
              "total_adults": 22,
              "total_children": 3,
              "total_infants": 0,
              "total_guests": 25,
              "total_capacity": 40,
              "guest_occupancy_rate": 62.5
            }
        """.trimIndent()

        val occ = HotelJson.decodeFromString<HotelOccupancySummaryDto>(occRaw)
        assertEquals(0, BigDecimal("75.00").compareTo(occ.occupancyRate!!))
        assertEquals(0, BigDecimal("62.5").compareTo(occ.guestOccupancyRate!!))

        val maintRaw = """
            {
              "id": 9,
              "ticket_number": "MT-009",
              "title": "AC leak",
              "category": "hvac",
              "priority": "high",
              "status": "open",
              "estimated_cost": "150.25",
              "actual_cost": null,
              "created_at": "2026-11-25T08:00:00Z",
              "updated_at": "2026-11-25T08:00:00Z"
            }
        """.trimIndent()
        val ticket = HotelJson.decodeFromString<MaintenanceTicketDto>(maintRaw)
        assertEquals(0, BigDecimal("150.25").compareTo(ticket.estimatedCost!!))
        assertNull(ticket.actualCost)
    }
}
