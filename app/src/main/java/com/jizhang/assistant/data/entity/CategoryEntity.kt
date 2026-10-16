package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类实体。
 *
 * type: 0=支出系统 1=支出自定义 2=收入系统 3=收入自定义
 */
@Entity(
    tableName = "category",
    indices = [Index(value = ["categoryId"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: String,          // 稳定ID（如 "e_catering"），用于流水外键关联
    val name: String,                // 显示名
    val icon: String,                // 图标标识（Material icon 名）
    val type: Int,                   // 0/1/2/3
    val sortOrder: Int,              // 排序
) {
    val isExpense: Boolean get() = type == 0 || type == 1
    val isIncome: Boolean get() = type == 2 || type == 3
    val isCustom: Boolean get() = type == 1 || type == 3

    companion object {
        const val TYPE_EXPENSE_SYSTEM = 0
        const val TYPE_EXPENSE_CUSTOM = 1
        const val TYPE_INCOME_SYSTEM = 2
        const val TYPE_INCOME_CUSTOM = 3
    }
}
