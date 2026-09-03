package com.dailymemory.app.ui

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.time.ZoneId

fun lunarDateLabel(date: LocalDate): String {
    val calendar = ChineseCalendar().apply {
        timeInMillis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH)
    if (day == 1) {
        val months = listOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
        return (if (calendar.get(Calendar.IS_LEAP_MONTH) == 1) "闰" else "") + months[month]
    }
    val numerals = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
    return when {
        day < 10 -> "初" + numerals[day - 1]
        day == 10 -> "初十"
        day < 20 -> "十" + numerals[day - 11]
        day == 20 -> "二十"
        day < 30 -> "廿" + numerals[day - 21]
        else -> "三十"
    }
}
