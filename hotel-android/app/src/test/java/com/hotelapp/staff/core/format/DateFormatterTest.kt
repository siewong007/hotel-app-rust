package com.hotelapp.staff.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DateFormatterTest {

    @Test
    fun formatDate_formatsIsoDateOrFallsBack() {
        assertEquals("25 Nov 2026", DateFormatter.formatDate("2026-11-25T10:15:00Z"))
        assertEquals("—", DateFormatter.formatDate(null))
        assertEquals("—", DateFormatter.formatDate("   "))
        assertEquals("invalid-date", DateFormatter.formatDate("invalid-date"))
    }

    @Test
    fun nightsCount_returnsPositiveNightsOrNull() {
        assertEquals(2L, DateFormatter.nightsCount("2026-11-25", "2026-11-27"))
        assertEquals(1L, DateFormatter.nightsCount("2026-11-25T14:00:00Z", "2026-11-26T12:00:00Z"))
        assertNull(DateFormatter.nightsCount("2026-11-25", "2026-11-25"))
        assertNull(DateFormatter.nightsCount("2026-11-27", "2026-11-25"))
        assertNull(DateFormatter.nightsCount(null, "2026-11-27"))
    }

    @Test
    fun formatDateRangeWithNights_compactsSameYearAndAppendsNights() {
        assertEquals(
            "25 Nov – 27 Nov 2026 · 2n",
            DateFormatter.formatDateRangeWithNights("2026-11-25", "2026-11-27"),
        )
        assertEquals(
            "30 Dec 2026 – 02 Jan 2027 · 3n",
            DateFormatter.formatDateRangeWithNights("2026-12-30", "2027-01-02"),
        )
        assertEquals("—", DateFormatter.formatDateRangeWithNights(null, "2026-11-27"))
    }

    @Test
    fun formatRelativeTimestamp_formatsBucketsCleanly() {
        val zone = ZoneOffset.UTC
        val now = Instant.parse("2026-11-25T15:30:00Z")

        assertEquals("—", DateFormatter.formatRelativeTimestamp(null, now, zone))
        assertEquals("Just now", DateFormatter.formatRelativeTimestamp("2026-11-25T15:29:30Z", now, zone))
        assertEquals("12m ago", DateFormatter.formatRelativeTimestamp("2026-11-25T15:18:00Z", now, zone))
        assertEquals("3h ago", DateFormatter.formatRelativeTimestamp("2026-11-25T12:30:00Z", now, zone))
        assertEquals("Yesterday, 22:15", DateFormatter.formatRelativeTimestamp("2026-11-24T22:15:00Z", now, zone))
        assertEquals("10 Nov, 09:05", DateFormatter.formatRelativeTimestamp("2026-11-10T09:05:00Z", now, zone))
        assertEquals("10 Nov 2025, 09:05", DateFormatter.formatRelativeTimestamp("2025-11-10T09:05:00Z", now, zone))
        assertEquals("not-a-timestamp", DateFormatter.formatRelativeTimestamp("not-a-timestamp", now, zone))
    }

    @Test
    fun greetingForHour_coversDayParts() {
        assertEquals("Good morning", DateFormatter.greetingForHour(5))
        assertEquals("Good morning", DateFormatter.greetingForHour(11))
        assertEquals("Good afternoon", DateFormatter.greetingForHour(12))
        assertEquals("Good afternoon", DateFormatter.greetingForHour(16))
        assertEquals("Good evening", DateFormatter.greetingForHour(17))
        assertEquals("Good evening", DateFormatter.greetingForHour(2))
    }

    @Test
    fun formatTodayLabel_formatsExpectedDate() {
        val now = Instant.parse("2026-11-25T08:00:00Z")
        assertEquals("Wed, 25 Nov 2026", DateFormatter.formatTodayLabel(ZoneOffset.UTC, now))
    }
}
