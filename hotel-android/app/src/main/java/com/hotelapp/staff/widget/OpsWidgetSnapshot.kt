package com.hotelapp.staff.widget

import kotlinx.serialization.Serializable

@Serializable
data class OpsWidgetSnapshot(
    val hotelName: String = "Hotel Staff",
    val occupancyPercent: String = "0%",
    val occupiedRooms: Long = 0,
    val totalRooms: Long = 0,
    val arrivingToday: Long = 0,
    val dirtyRooms: Int = 0,
    val openMaintenance: Int = 0,
    val updatedAtMs: Long = 0L,
    val isAuthenticated: Boolean = false,
) {
    companion object {
        const val CACHE_KEY = "ops_widget_snapshot"
    }
}
