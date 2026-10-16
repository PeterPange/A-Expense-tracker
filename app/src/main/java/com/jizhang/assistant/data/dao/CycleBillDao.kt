package com.jizhang.assistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jizhang.assistant.data.entity.CycleBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleBillDao {

    @Query("SELECT * FROM cycle_bill ORDER BY nextDate")
    fun observeAll(): Flow<List<CycleBillEntity>>

    @Query("SELECT * FROM cycle_bill WHERE enabled = 1 AND nextDate <= :now")
    suspend fun dueBills(now: Long): List<CycleBillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: CycleBillEntity): Long

    @Update
    suspend fun update(bill: CycleBillEntity)

    @Delete
    suspend fun delete(bill: CycleBillEntity)

    @Query("SELECT * FROM cycle_bill")
    suspend fun all(): List<CycleBillEntity>

    @Query("DELETE FROM cycle_bill")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<CycleBillEntity>)
}
