package com.jizhang.assistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jizhang.assistant.data.entity.RecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Delete
    suspend fun delete(record: RecordEntity)

    @Query("SELECT * FROM record WHERE id = :id")
    suspend fun findById(id: Long): RecordEntity?

    /** 某月全部流水（按时间倒序，可按账本过滤） */
    @Query(
        "SELECT * FROM record WHERE year = :year AND month = :month " +
            "AND (:bid IS NULL OR bid = :bid) " +
            "ORDER BY date DESC, id DESC"
    )
    fun observeMonth(year: Int, month: Int, bid: String?): Flow<List<RecordEntity>>

    /** 搜索：备注/成员/金额 模糊匹配 */
    @Query(
        "SELECT r.* FROM record r INNER JOIN category c ON r.categoryId = c.categoryId " +
            "WHERE r.remark LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%' " +
            "OR r.member LIKE '%' || :query || '%' OR CAST(r.amount AS TEXT) LIKE '%' || :query || '%' " +
            "ORDER BY r.date DESC LIMIT 500"
    )
    fun search(query: String): Flow<List<RecordEntity>>

    /** 某月收支合计（按分类方向，可按账本过滤） */
    @Query(
        "SELECT SUM(CASE WHEN c.type IN (0,1) THEN r.amount ELSE 0 END) AS expense, " +
            "SUM(CASE WHEN c.type IN (2,3) THEN r.amount ELSE 0 END) AS income " +
            "FROM record r INNER JOIN category c ON r.categoryId = c.categoryId " +
            "WHERE r.year = :year AND r.month = :month AND (:bid IS NULL OR r.bid = :bid)"
    )
    fun observeMonthTotal(year: Int, month: Int, bid: String?): Flow<MonthTotal?>

    /** 某月分类聚合 */
    @Query(
        "SELECT c.categoryId AS categoryId, c.name AS name, c.type AS type, " +
            "SUM(r.amount) AS total, COUNT(*) AS cnt " +
            "FROM record r INNER JOIN category c ON r.categoryId = c.categoryId " +
            "WHERE r.year = :year AND r.month = :month AND (:bid IS NULL OR r.bid = :bid) " +
            "GROUP BY c.categoryId, c.name, c.type ORDER BY total DESC"
    )
    fun observeMonthCategoryTotals(year: Int, month: Int, bid: String?): Flow<List<CategoryTotal>>

    /** 某月按天聚合（日历/趋势图） */
    @Query(
        "SELECT day, SUM(CASE WHEN c.type IN (0,1) THEN r.amount ELSE 0 END) AS expense, " +
            "SUM(CASE WHEN c.type IN (2,3) THEN r.amount ELSE 0 END) AS income " +
            "FROM record r INNER JOIN category c ON r.categoryId = c.categoryId " +
            "WHERE r.year = :year AND r.month = :month AND (:bid IS NULL OR r.bid = :bid) " +
            "GROUP BY day ORDER BY day"
    )
    fun observeMonthDailyTotals(year: Int, month: Int, bid: String?): Flow<List<DailyTotal>>

    /** 某月按成员聚合（家庭排行） */
    @Query(
        "SELECT r.member AS member, " +
            "SUM(CASE WHEN c.type IN (0,1) THEN r.amount ELSE 0 END) AS expense, " +
            "SUM(CASE WHEN c.type IN (2,3) THEN r.amount ELSE 0 END) AS income " +
            "FROM record r INNER JOIN category c ON r.categoryId = c.categoryId " +
            "WHERE r.year = :year AND r.month = :month AND (:bid IS NULL OR r.bid = :bid) " +
            "GROUP BY r.member ORDER BY expense DESC"
    )
    fun observeMonthMemberTotals(year: Int, month: Int, bid: String?): Flow<List<MemberTotal>>

    /** 有数据的月份列表 */
    @Query("SELECT DISTINCT year, month FROM record ORDER BY year DESC, month DESC")
    fun observeMonths(): Flow<List<YearMonth>>

    /** 某分类某月合计（分类预算进度） */
    @Query(
        "SELECT COALESCE(SUM(r.amount), 0) FROM record r " +
            "INNER JOIN category c ON r.categoryId = c.categoryId " +
            "WHERE r.year = :year AND r.month = :month AND c.categoryId = :categoryId"
    )
    suspend fun categoryMonthTotal(year: Int, month: Int, categoryId: String): Double

    /** 全部流水（导出/备份用） */
    @Query("SELECT * FROM record ORDER BY date DESC")
    suspend fun all(): List<RecordEntity>

    @Query("DELETE FROM record")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(records: List<RecordEntity>)

    /** 打卡统计：有记录的天数 */
    @Query("SELECT COUNT(DISTINCT year || '-' || month || '-' || day) FROM record")
    fun observeCheckInDays(): Flow<Int>

    /** 连续记账天数（从今天往回数） */
    @Query(
        "SELECT DISTINCT year, month, day FROM record ORDER BY year DESC, month DESC, day DESC LIMIT 400"
    )
    suspend fun recentDays(): List<YearMonthDay>

    data class MonthTotal(val expense: Double?, val income: Double?)

    data class CategoryTotal(
        val categoryId: String,
        val name: String,
        val type: Int,
        val total: Double,
        val cnt: Int,
    )

    data class DailyTotal(val day: Int, val expense: Double?, val income: Double?)

    data class MemberTotal(val member: String?, val expense: Double?, val income: Double?)

    data class YearMonth(val year: Int, val month: Int)

    data class YearMonthDay(val year: Int, val month: Int, val day: Int)
}
