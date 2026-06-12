package com.calmerge.app.ui

import java.net.URI
import java.time.LocalDate
import java.time.ZoneId

fun parseIsoDateOrNull(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun isoDateToEpochMillisOrNull(value: String?, zone: ZoneId): Long? =
    parseIsoDateOrNull(value)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

fun urlHostOrNull(url: String?): String? =
    url?.let { runCatching { URI(it).host }.getOrNull() }
