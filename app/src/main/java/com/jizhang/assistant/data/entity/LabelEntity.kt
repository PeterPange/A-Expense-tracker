package com.jizhang.assistant.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 标签。
 */
@Entity(tableName = "label", indices = [Index(value = ["name"], unique = true)])
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** 流水-标签 多对多关联 */
@Entity(
    tableName = "record_label",
    primaryKeys = ["recordId", "labelId"],
    indices = [Index("recordId"), Index("labelId")],
)
data class RecordLabelCrossRef(
    val recordId: Long,
    val labelId: Long,
)
