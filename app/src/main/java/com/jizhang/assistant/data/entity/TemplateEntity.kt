package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记账模板：一键复记常用支出。
 */
@Entity(tableName = "template")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                // 模板名（如"午饭"）
    val categoryId: String,
    val isExpense: Boolean = true,
    val amount: Double = 0.0,        // 0 = 每次手动输入
    val remark: String = "",
    val sortOrder: Int = 0,
    val ctime: Long = System.currentTimeMillis(),
)
