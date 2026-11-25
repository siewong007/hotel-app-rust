package com.hotelapp.staff.widget

import com.hotelapp.staff.core.network.HotelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsWidgetSnapshotTest {

    @Test
    fun `serializes and deserializes widget snapshot accurately`() {
        val snapshot = OpsWidgetSnapshot(
            hotelName = "Salim Inn",
            occupancyPercent = "82%",
            occupiedRooms = 41,
            totalRooms = 50,
            arrivingToday = 9,
            dirtyRooms = 4,
            openMaintenance = 2,
            updatedAtMs = 1_764_000_000_000L,
            isAuthenticated = true,
        )
        val encoded = HotelJson.encodeToString(OpsWidgetSnapshot.serializer(), snapshot)
        val decoded = HotelJson.decodeFromString(OpsWidgetSnapshot.serializer(), encoded)

        assertEquals(snapshot, decoded)
        assertTrue(decoded.isAuthenticated)
    }
}
