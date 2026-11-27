package com.hotelapp.staff.data.dto

import com.hotelapp.staff.core.network.HotelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SettlementLockStateTest {

    private fun sampleBooking(
        source: String? = "walk_in",
        channelId: Long? = null,
        channelName: String? = null,
        channelType: String? = null,
        methods: List<String>? = null,
        payoutRecorded: Boolean? = null,
    ) = BookingWithDetailsDto(
        id = 1L,
        bookingNumber = "BK-1",
        guestId = 1L,
        guestName = "Test Guest",
        roomId = 1L,
        roomNumber = "101",
        roomType = "Standard",
        checkInDate = "2026-11-26",
        checkOutDate = "2026-11-27",
        status = "confirmed",
        source = source,
        bookingChannelId = channelId,
        bookingChannelName = channelName,
        bookingChannelType = channelType,
        bookingChannelSettlementMethods = methods,
        channelPayoutRecorded = payoutRecorded,
    )

    @Test
    fun `settlementLockState distinguishes open, restricted, and blocked states`() {
        assertEquals(SettlementLockState.Open, sampleBooking(source = "walk_in").settlementLockState(allowDeskRemainder = true))
        assertEquals(
            SettlementLockState.Open,
            sampleBooking(source = "online", channelType = "ota", methods = emptyList())
                .settlementLockState(allowDeskRemainder = false, paymentType = "deposit"),
        )
        assertEquals(
            SettlementLockState.Open,
            sampleBooking(source = "online", channelType = "ota", methods = emptyList())
                .settlementLockState(allowDeskRemainder = true, paymentType = "damage"),
        )

        val websiteFallback = sampleBooking(source = "website", methods = null)
            .settlementLockState(allowDeskRemainder = true)
        assertEquals(SettlementLockState.Restricted(listOf("bank_transfer", "paypal")), websiteFallback)

        val otaRestrictedNoDesk = sampleBooking(
            source = "online",
            channelType = "ota",
            channelId = 7L,
            channelName = "Agoda",
            methods = listOf("Agoda Collect"),
            payoutRecorded = true,
        ).settlementLockState(allowDeskRemainder = false, paymentType = "booking")
        assertEquals(SettlementLockState.Restricted(listOf("Agoda Collect")), otaRestrictedNoDesk)

        val otaOpenAfterPayout = sampleBooking(
            source = "online",
            channelType = "ota",
            channelId = 7L,
            channelName = "Agoda",
            methods = listOf("Agoda Collect"),
            payoutRecorded = true,
        ).settlementLockState(allowDeskRemainder = true, paymentType = "booking")
        assertEquals(SettlementLockState.Open, otaOpenAfterPayout)

        val missingChannel = sampleBooking(source = "online", payoutRecorded = true)
            .settlementLockState(allowDeskRemainder = true)
        assertTrue(missingChannel is SettlementLockState.Blocked)

        val unconfiguredChannel = sampleBooking(
            source = "online",
            channelId = 3L,
            channelName = "Expedia",
            channelType = "ota",
            methods = emptyList(),
            payoutRecorded = true,
        ).settlementLockState(allowDeskRemainder = true)
        assertTrue(unconfiguredChannel is SettlementLockState.Blocked)
    }

    @Test
    fun `guest displayName and sensitive profile deserialize cleanly`() {
        assertEquals("Amina Rahman", GuestDto(id = 1L, nickName = "Ami", firstName = "Amina", lastName = "Rahman").displayName())
        assertEquals("Amina", GuestDto(id = 2L, nickName = "Ami", firstName = "Amina", lastName = null).displayName())
        assertEquals("Rahman", GuestDto(id = 3L, nickName = "Ami", firstName = "  ", lastName = "Rahman").displayName())
        assertEquals("Ami", GuestDto(id = 4L, nickName = "Ami", firstName = null, lastName = "").displayName())
        assertEquals("Guest #5", GuestDto(id = 5L, nickName = "   ").displayName())

        val withSensitiveJson = """
            {
              "guest": { "id": 10, "nick_name": "Nora", "first_name": "Nora", "last_name": "Ali" },
              "summary": {},
              "ekyc_summary": { "status": "verified" },
              "reservations": [],
              "sensitive": {
                "date_of_birth": "1992-05-14",
                "id_type": "passport",
                "id_number": "A12345678",
                "id_expiry": "2030-08-01",
                "id_country": "MY"
              }
            }
        """.trimIndent()
        val profile = HotelJson.decodeFromString<GuestProfileDto>(withSensitiveJson)
        assertEquals("1992-05-14", profile.sensitive?.dateOfBirth)
        assertEquals("passport", profile.sensitive?.idType)
        assertEquals("A12345678", profile.sensitive?.idNumber)
        assertEquals("2030-08-01", profile.sensitive?.idExpiry)
        assertEquals("MY", profile.sensitive?.idCountry)

        val withoutSensitiveJson = """
            {
              "guest": { "id": 11, "nick_name": "Sam" },
              "summary": {},
              "ekyc_summary": { "status": "pending" },
              "reservations": []
            }
        """.trimIndent()
        val noSensProfile = HotelJson.decodeFromString<GuestProfileDto>(withoutSensitiveJson)
        assertNull(noSensProfile.sensitive)
        assertEquals(BigDecimal.ZERO, noSensProfile.summary.totalRoomRevenue)
    }
}
