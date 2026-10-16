package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 周期账（周期记账/自动记账）：房租、工资等固定收支，到期自动入账。
 * frequency: daily / weekly / monthly / yearly
 */
@Entity(tableName = "cycle_bill")
data class CycleBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                // 如 "房租"
    val categoryId: String,
    val isExpense: Boolean = true,
    val amount: Double,
    val remark: String = "",
    val frequency: String,           // daily/weekly/monthly/yearly
    val dayOfMonth: Int = 1,         // monthly: 每月几号；yearly: 几月几号中的"几号"
    val monthOfYear: Int = 1,        // yearly: 几月
    val dayOfWeek: Int = 1,          // weekly: 周几（1=周一…7=周日）
    val nextDate: Long,              // 下次执行日（00:00）
    val lastExecuted: Long = 0,
    val bid: String = BookEntity.DEFAULT_BID,
    val enabled: Boolean = true,
) {
    companion object {
        const val FREQ_DAILY = "daily"
        const val FREQ_WEEKLY = "weekly"
        const val FREQ_MONTHLY = "monthly"
        const val FREQ_YEARLY = "yearly"

        fun freqName(freq: String): String = when (freq) {
            FREQ_DAILY -> "每天"
            FREQ_WEEKLY -> "每周"
            FREQ_MONTHLY -> "每月"
            FREQ_YEARLY -> "每年"
            else -> freq
        }
    }
}
