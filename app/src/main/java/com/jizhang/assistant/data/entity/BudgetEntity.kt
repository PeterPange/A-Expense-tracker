package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 月度预算。budgetMonth 格式 "2026-10"。
 * categoryId 为空串表示总预算，否则为分类预算。
 */
@Entity(tableName = "budget")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val budgetMonth: String,
    val categoryId: String,          // "" = 总预算
    val amount: Double,
)
