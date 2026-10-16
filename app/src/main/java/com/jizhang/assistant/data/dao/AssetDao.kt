package com.jizhang.assistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jizhang.assistant.data.entity.AssetEntity
import com.jizhang.assistant.data.entity.AssetTransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Query("SELECT * FROM asset ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset WHERE id = :id")
    suspend fun findById(id: Long): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Delete
    suspend fun delete(asset: AssetEntity)

    /** 记账时调整余额：支出减、收入加 */
    @Query("UPDATE asset SET balance = balance + :delta WHERE id = :id")
    suspend fun adjustBalance(id: Long, delta: Double)

    @Query("SELECT COALESCE(SUM(balance), 0) FROM asset WHERE includeInTotal = 1")
    fun observeTotalBalance(): Flow<Double>

    // ── 转账 ──
    @Insert
    suspend fun insertTransfer(transfer: AssetTransferEntity): Long

    @Query("SELECT * FROM asset_transfer ORDER BY date DESC")
    fun observeTransfers(): Flow<List<AssetTransferEntity>>

    @Query("DELETE FROM asset_transfer WHERE id = :id")
    suspend fun deleteTransfer(id: Long)

    @Transaction
    suspend fun transfer(transfer: AssetTransferEntity) {
        adjustBalance(transfer.fromAssetId, -transfer.amount - transfer.fee)
        adjustBalance(transfer.toAssetId, transfer.amount)
        insertTransfer(transfer)
    }

    @Query("SELECT * FROM asset")
    suspend fun all(): List<AssetEntity>

    @Query("DELETE FROM asset")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<AssetEntity>)
}
