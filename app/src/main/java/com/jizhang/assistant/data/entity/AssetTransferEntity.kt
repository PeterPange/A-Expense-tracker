package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 资产转账记录：从 fromAssetId 转到 toAssetId。
 */
@Entity(tableName = "asset_transfer")
data class AssetTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAssetId: Long,
    val toAssetId: Long,
    val amount: Double,
    val fee: Double = 0.0,           // 手续费
    val remark: String = "",
    val date: Long = System.currentTimeMillis(),
)
