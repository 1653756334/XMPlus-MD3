package com.xuleo.xmplus.md3.core.media

import android.content.Context

/**
 * The sleep timer length last started on this device. The next timer opens at that length, because people tend to set
 * the same one each time.
 */
class SleepTimerStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("sleep-timer", Context.MODE_PRIVATE)

    fun minutes(): Int = preferences.getInt(Minutes, DefaultMinutes).takeIf { it in MinMinutes..MaxMinutes } ?: DefaultMinutes

    fun setMinutes(minutes: Int) {
        preferences.edit().putInt(Minutes, minutes.coerceIn(MinMinutes, MaxMinutes)).apply()
    }

    private companion object {
        const val Minutes = "minutes"
        const val DefaultMinutes = 30
        const val MinMinutes = 1
        const val MaxMinutes = 120
    }
}
