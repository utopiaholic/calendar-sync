package com.calmerge.app.ui

enum class CalendarProvider(private val hostMarkers: List<String>) {
    MICROSOFT(listOf("outlook", "office365", "microsoft")),
    GOOGLE(listOf("google", "googleapis"));

    fun matchesAny(hosts: List<String>): Boolean =
        hosts.any { host -> hostMarkers.any { marker -> host.contains(marker, ignoreCase = true) } }
}
