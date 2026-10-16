package com.jizhang.assistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jizhang.assistant.data.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budget WHERE budgetMonth = :month")
    fun observeMonth(month: String): Flow<List<BudgetEntity>>

    @Query(
        "SELECT * FROM budget WHERE budgetMonth = :month AND categoryId = :categoryId"
    )
    suspend fun find(month: String, categoryId: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budget WHERE budgetMonth = :month AND categoryId = :categoryId")
    suspend fun delete(month: String, categoryId: String)

    @Query("SELECT * FROM budget")
    suspend fun all(): List<BudgetEntity>

    @Query("DELETE FROM budget")
    suspend fun clearAll()
}
