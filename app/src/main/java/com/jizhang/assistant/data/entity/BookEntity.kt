package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账本。默认账本 bid = "default"。
 */
@Entity(tableName = "book")
data class BookEntity(
    @PrimaryKey val bid: String,
    val name: String,
    val icon: String = "book",
    val sortOrder: Int = 0,
    val ctime: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_BID = "default"
        const val DEFAULT_NAME = "默认账本"
    }
}
