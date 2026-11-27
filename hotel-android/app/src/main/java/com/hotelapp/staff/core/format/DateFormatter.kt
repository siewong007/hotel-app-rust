package com.hotelapp.staff.core.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

object DateFormatter {
    private val shortDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
    private val shortMonthDayFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.US)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.US)
    private val sameYearDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.US)
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val todayHeaderFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.US)
    private val pgOffsetSuffixRegex = Regex("""[+-]\d{2}$""")
    private val colonlessOffsetSuffixRegex = Regex("""([+-]\d{2})(\d{2})$""")

    private fun parseInstant(raw: String): Instant {
        val trimmed = raw.trim()
        runCatching { return Instant.parse(trimmed) }
        runCatching { return OffsetDateTime.parse(trimmed).toInstant() }
        val pgNormalized = trimmed
            .replace(' ', 'T')
            .let { s ->
                when {
                    pgOffsetSuffixRegex.containsMatchIn(s) -> "$s:00"
                    colonlessOffsetSuffixRegex.containsMatchIn(s) ->
                        colonlessOffsetSuffixRegex.replace(s, "$1:$2")
                    else -> s
                }
            }
        runCatching { return OffsetDateTime.parse(pgNormalized).toInstant() }
        return LocalDateTime.parse(pgNormalized).toInstant(ZoneOffset.UTC)
    }

    fun formatDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return runCatching {
            LocalDate.parse(raw.trim().take(10)).format(shortDateFormatter)
        }.getOrElse { raw }
    }

    fun nightsCount(checkIn: String?, checkOut: String?): Long? {
        if (checkIn.isNullOrBlank() || checkOut.isNullOrBlank()) return null
        return runCatching {
            val start = LocalDate.parse(checkIn.trim().take(10))
            val end = LocalDate.parse(checkOut.trim().take(10))
            ChronoUnit.DAYS.between(start, end).takeIf { it > 0 }
        }.getOrNull()
    }

    fun formatDateRange(checkIn: String?, checkOut: String?): String {
        if (checkIn.isNullOrBlank() || checkOut.isNullOrBlank()) return "—"
        return runCatching {
            val start = LocalDate.parse(checkIn.trim().take(10))
            val end = LocalDate.parse(checkOut.trim().take(10))
            if (start.year == end.year) {
                "${start.format(shortMonthDayFormatter)} – ${end.format(shortDateFormatter)}"
            } else {
                "${start.format(shortDateFormatter)} – ${end.format(shortDateFormatter)}"
            }
        }.getOrElse { "$checkIn – $checkOut" }
    }

    fun formatDateRangeWithNights(checkIn: String?, checkOut: String?): String {
        val range = formatDateRange(checkIn, checkOut)
        if (range == "—") return range
        val nights = nightsCount(checkIn, checkOut)
        return if (nights != null) "$range · ${nights}n" else range
    }

    fun formatTimestamp(raw: String?, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (raw.isNullOrBlank()) return "—"
        return runCatching {
            parseInstant(raw)
                .atZone(zoneId)
                .format(dateTimeFormatter)
        }.getOrElse { raw }
    }

    fun formatRelativeTimestamp(
        raw: String?,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (raw.isNullOrBlank()) return "—"
        return runCatching {
            val instant = parseInstant(raw)
            val duration = Duration.between(instant, now)
            val seconds = duration.seconds
            val minutes = duration.toMinutes()
            val hours = duration.toHours()
            val targetZoned = instant.atZone(zoneId)
            val nowZoned = now.atZone(zoneId)

            when {
                abs(seconds) < 60 -> "Just now"
                duration.isNegative -> if (targetZoned.year == nowZoned.year) {
                    targetZoned.format(sameYearDateTimeFormatter)
                } else {
                    targetZoned.format(dateTimeFormatter)
                }
                minutes < 60 -> "${minutes}m ago"
                hours < 24 && targetZoned.toLocalDate() == nowZoned.toLocalDate() -> "${hours}h ago"
                targetZoned.toLocalDate() == nowZoned.toLocalDate().minusDays(1) ->
                    "Yesterday, ${targetZoned.format(timeOnlyFormatter)}"
                targetZoned.year == nowZoned.year ->
                    targetZoned.format(sameYearDateTimeFormatter)
                else -> targetZoned.format(dateTimeFormatter)
            }
        }.getOrElse { raw }
    }

    fun greetingForHour(hourOfDay: Int): String = when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    fun formatTodayLabel(
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): String = now.atZone(zoneId).format(todayHeaderFormatter)
}
