package com.jizhang.assistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jizhang.assistant.data.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM book ORDER BY sortOrder, ctime")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM book WHERE bid = :bid")
    suspend fun findById(bid: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT COUNT(*) FROM book")
    suspend fun count(): Int

    @Query("SELECT * FROM book")
    suspend fun all(): List<BookEntity>

    @Query("DELETE FROM book")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)
}
