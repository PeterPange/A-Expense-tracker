package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记账提醒：定时通知用户记账。
 * repeatDays: 7 位 bitmask（bit0=周一 … bit6=周日），0x7F = 每天。
 */
@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 0x7F,      // 每天重复
    val text: String = "该记账啦～",
    val enabled: Boolean = true,
)
