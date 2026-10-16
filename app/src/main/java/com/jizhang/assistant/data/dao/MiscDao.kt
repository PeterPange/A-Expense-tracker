package com.jizhang.assistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jizhang.assistant.data.entity.LabelEntity
import com.jizhang.assistant.data.entity.RecordLabelCrossRef
import com.jizhang.assistant.data.entity.ReminderEntity
import com.jizhang.assistant.data.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM template ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Delete
    suspend fun delete(template: TemplateEntity)

    @Query("SELECT * FROM template")
    suspend fun all(): List<TemplateEntity>

    @Query("DELETE FROM template")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<TemplateEntity>)
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM label ORDER BY id")
    fun observeAll(): Flow<List<LabelEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: LabelEntity): Long

    @Insert
    suspend fun insertCrossRef(ref: RecordLabelCrossRef)

    @Query(
        "SELECT label.* FROM label INNER JOIN record_label ON label.id = record_label.labelId " +
            "WHERE record_label.recordId = :recordId"
    )
    suspend fun labelsOfRecord(recordId: Long): List<LabelEntity>

    @Query("DELETE FROM record_label WHERE recordId = :recordId")
    suspend fun clearRecordLabels(recordId: Long)

    @Query("DELETE FROM label WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM label")
    suspend fun all(): List<LabelEntity>

    @Query("DELETE FROM label")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(labels: List<LabelEntity>)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY hour, minute")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE enabled = 1")
    suspend fun enabled(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM reminder")
    suspend fun all(): List<ReminderEntity>

    @Query("DELETE FROM reminder")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)
}
