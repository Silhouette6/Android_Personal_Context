package com.fish.personalcontext.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFmt {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dateHeader = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.getDefault())
    private val dateTime = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.getDefault())

    fun time(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(time)

    fun dateHeader(date: LocalDate): String = date.format(dateHeader)

    fun dateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(dateTime)

    fun duration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            totalMinutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    fun fileSize(bytes: Long): String = when {
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
        bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
