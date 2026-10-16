package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 资产账户（现金/储蓄卡/信用卡/支付宝/微信/负债等）。
 * balance 可为负（信用卡欠款/负债）。
 */
@Entity(tableName = "asset")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,                // cash/bank/credit/alipay/wechat/debt
    val balance: Double = 0.0,
    val cardTail: String = "",       // 卡号后四位
    val icon: String = "",
    val sortOrder: Int = 0,
    val includeInTotal: Boolean = true,
    val ctime: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_CASH = "cash"
        const val TYPE_BANK = "bank"
        const val TYPE_CREDIT = "credit"
        const val TYPE_ALIPAY = "alipay"
        const val TYPE_WECHAT = "wechat"
        const val TYPE_DEBT = "debt"

        fun typeName(type: String): String = when (type) {
            TYPE_CASH -> "现金"
            TYPE_BANK -> "储蓄卡"
            TYPE_CREDIT -> "信用卡"
            TYPE_ALIPAY -> "支付宝"
            TYPE_WECHAT -> "微信"
            TYPE_DEBT -> "负债"
            else -> "其他"
        }
    }
}
