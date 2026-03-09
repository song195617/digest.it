package com.digestit.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val shortDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())

fun formatDateTime(instant: Instant?): String {
    if (instant == null) return "--"
    return shortDateTimeFormatter.format(instant)
}
