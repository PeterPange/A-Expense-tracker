package com.jizhang.assistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jizhang.assistant.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY type, sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE type IN (0, 1) ORDER BY sortOrder")
    fun observeExpense(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE type IN (2, 3) ORDER BY sortOrder")
    fun observeIncome(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE categoryId = :categoryId")
    suspend fun findById(categoryId: String): CategoryEntity?

    @Query("SELECT * FROM category WHERE categoryId = :categoryId")
    fun observeById(categoryId: String): Flow<CategoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM category WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: String)

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT * FROM category")
    suspend fun all(): List<CategoryEntity>

    @Query("DELETE FROM category")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllEntities(categories: List<CategoryEntity>)
}
