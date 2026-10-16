package com.jizhang.assistant.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记账流水实体。
 *
 * 设计要点：
 * - year/month/day/week 冗余维度列，加速按周期聚合查询
 * - 金额恒为正数，收支方向由分类的 type 决定，避免符号歧义
 * - bid: 所属账本；member: 家庭记账成员（本地简化版）；assetId: 关联资产账户
 */
@Entity(
    tableName = "record",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["year", "month"]),
        Index(value = ["date"]),
        Index(value = ["bid"]),
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,              // 金额（恒为正）
    val categoryId: String,          // 分类ID
    val remark: String = "",         // 备注
    val date: Long,                  // 记账时间（毫秒时间戳）
    val year: Int,                   // 冗余：年
    val month: Int,                  // 冗余：月 1-12
    val day: Int,                    // 冗余：日 1-31
    val week: Int,                   // 冗余：周（ISO week-of-year）
    val ctime: Long,                 // 创建时间
    @ColumnInfo(defaultValue = "default")
    val bid: String = BookEntity.DEFAULT_BID,   // 账本
    @ColumnInfo(defaultValue = "")
    val member: String = "",          // 成员（家庭记账，空=自己）
    @ColumnInfo(defaultValue = "0")
    val assetId: Long = 0,           // 关联资产账户（0=不关联）
    @ColumnInfo(defaultValue = "0")
    val autoTally: Boolean = false,  // 周期账自动入账标记
)
