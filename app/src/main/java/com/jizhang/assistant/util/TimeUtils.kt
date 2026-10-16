package com.jizhang.assistant.util

import java.util.Calendar
import java.util.Locale

object TimeUtils {

    fun calendarOf(millis: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = millis }

    fun yearOf(millis: Long) = calendarOf(millis).get(Calendar.YEAR)
    fun monthOf(millis: Long) = calendarOf(millis).get(Calendar.MONTH) + 1

    /** "2026-10" */
    fun monthKey(year: Int, month: Int): String = "%04d-%02d".format(year, month)

    /** "10月15日" */
    fun dayLabel(millis: Long): String {
        val c = calendarOf(millis)
        return "%d月%d日".format(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** 今天 00:00 的时间戳 */
    fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** "HH:mm" */
    fun timeLabel(millis: Long): String {
        val c = calendarOf(millis)
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun weekdayName(millis: Long): String {
        val names = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return names[calendarOf(millis).get(Calendar.DAY_OF_WEEK) - 1]
    }

    fun isToday(millis: Long): Boolean = millis >= startOfToday()

    /** 月份选择器数据：从有数据的最早月到当前月 */
    fun monthsRange(fromYear: Int, fromMonth: Int): List<Pair<Int, Int>> {
        val now = Calendar.getInstance()
        val result = mutableListOf<Pair<Int, Int>>()
        var y = fromYear; var m = fromMonth
        while (y < now.get(Calendar.YEAR) ||
            (y == now.get(Calendar.YEAR) && m <= now.get(Calendar.MONTH) + 1)
        ) {
            result.add(y to m)
            m++; if (m > 12) { m = 1; y++ }
        }
        return result.reversed()
    }

    fun monthName(month: Int): String = "%d月".format(month)
}
