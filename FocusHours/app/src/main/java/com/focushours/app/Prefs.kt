package com.focushours.app

import android.content.Context
import java.util.Calendar

/** A daily time window in minutes after midnight, e.g. 12:00–13:00 = 720..780. */
data class TimeWindow(val start: Int, val end: Int) {
    /** True if minute-of-day [m] falls inside the window. Handles windows that cross midnight. */
    fun contains(m: Int): Boolean =
        if (start <= end) m in start until end else (m >= start || m < end)

    fun label(): String = "${fmt(start)} – ${fmt(end)}"

    companion object {
        fun fmt(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

/** Everything the app remembers, stored on the phone only. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("focus_hours", Context.MODE_PRIVATE)

    /** Package names of the apps that are locked outside allowed hours. */
    var lockedApps: Set<String>
        get() = sp.getStringSet(KEY_LOCKED, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_LOCKED, v.toSet()).apply()

    /** Allowed windows. Default: lunch break and evening. */
    var windows: List<TimeWindow>
        get() = (sp.getString(KEY_WINDOWS, DEFAULT_WINDOWS) ?: "")
            .split(";")
            .mapNotNull { part ->
                val bits = part.split("-")
                if (bits.size == 2) {
                    val s = bits[0].toIntOrNull(); val e = bits[1].toIntOrNull()
                    if (s != null && e != null) TimeWindow(s, e) else null
                } else null
            }
            .sortedBy { it.start }
        set(v) = sp.edit().putString(KEY_WINDOWS, v.joinToString(";") { "${it.start}-${it.end}" }).apply()

    private fun minuteOfDay(cal: Calendar = Calendar.getInstance()) =
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    fun isAllowedNow(): Boolean {
        val m = minuteOfDay()
        return windows.any { it.contains(m) }
    }

    /** Text like "20:00" for the next time locked apps open, or null if no windows are set. */
    fun nextOpening(): String? {
        val ws = windows
        if (ws.isEmpty()) return null
        val now = minuteOfDay()
        val next = ws.minByOrNull { (it.start - now + 1440) % 1440 } ?: return null
        return TimeWindow.fmt(next.start)
    }

    /** When the current allowed window ends, e.g. "13:00", or null if we are outside all windows. */
    fun currentWindowEnd(): String? {
        val now = minuteOfDay()
        return windows.firstOrNull { it.contains(now) }?.let { TimeWindow.fmt(it.end) }
    }

    // ----- Emergency passes: a few short unlocks per day -----

    fun hasPass(pkg: String): Boolean = sp.getLong("pass_$pkg", 0L) > System.currentTimeMillis()

    fun passesLeftToday(): Int {
        val used = if (sp.getInt(KEY_PASS_DAY, 0) == today()) sp.getInt(KEY_PASS_COUNT, 0) else 0
        return (PASSES_PER_DAY - used).coerceAtLeast(0)
    }

    /** Grants [minutes] of access to [pkg]. Returns false if today's passes are used up. */
    fun usePass(pkg: String, minutes: Int = PASS_MINUTES): Boolean {
        if (passesLeftToday() <= 0) return false
        val used = PASSES_PER_DAY - passesLeftToday()
        sp.edit()
            .putInt(KEY_PASS_DAY, today())
            .putInt(KEY_PASS_COUNT, used + 1)
            .putLong("pass_$pkg", System.currentTimeMillis() + minutes * 60_000L)
            .apply()
        return true
    }

    private fun today(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        private const val KEY_LOCKED = "locked_apps"
        private const val KEY_WINDOWS = "windows"
        private const val KEY_PASS_DAY = "pass_day"
        private const val KEY_PASS_COUNT = "pass_count"
        private const val DEFAULT_WINDOWS = "720-780;1200-1260" // 12:00–13:00 and 20:00–21:00
        const val PASSES_PER_DAY = 3
        const val PASS_MINUTES = 5
    }
}
