package com.digestit.ui.common

fun formatDuration(durationSeconds: Int): String {
    if (durationSeconds <= 0) return "时长未知"
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}
