package be.dimsumfamily.podcast.ui

import android.text.format.DateUtils

/** Small formatting helpers for durations and dates. */
object Format {

    /** "1:02:03" or "4:05". Empty string for unknown/zero. */
    fun clock(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    /** Human duration for episode rows, e.g. "1 hr 2 min" or "45 min". */
    fun durationLabel(ms: Long): String {
        if (ms <= 0) return ""
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h} hr ${m} min"
            h > 0 -> "${h} hr"
            else -> "${m} min"
        }
    }

    fun relativeDate(epochMs: Long): String {
        if (epochMs <= 0) return ""
        return DateUtils.getRelativeTimeSpanString(
            epochMs,
            System.currentTimeMillis(),
            DateUtils.DAY_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_ALL,
        ).toString()
    }
}
