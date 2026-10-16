package com.jizhang.assistant.util

import java.text.DecimalFormat

object Money {

    private val df = DecimalFormat("#,##0.##")

    /** 显示格式：1234.5 → "1,234.5" */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return df.format(kotlin.math.abs(value))
    }

    /** 带符号显示：-1234.5 → "-1,234.5" */
    fun formatSigned(value: Double): String =
        if (value < 0) "-" + format(value) else format(value)

    /** 金额输入框中间态显示（允许 "12." 这种） */
    fun formatInput(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '.' }
        val parts = cleaned.split('.')
        return when {
            parts.isEmpty() -> "0"
            parts.size == 1 -> parts[0].ifEmpty { "0" }
            else -> parts[0].ifEmpty { "0" } + "." + parts[1]
        }
    }

    /** 解析金额字符串，失败返回 null */
    fun parse(raw: String): Double? {
        val v = raw.toDoubleOrNull()
        return if (v == null || v.isNaN() || v <= 0.0) null else v
    }
}
