package com.jizhang.assistant.data.repository

import com.jizhang.assistant.data.dao.AssetDao
import com.jizhang.assistant.data.dao.BookDao
import com.jizhang.assistant.data.dao.BudgetDao
import com.jizhang.assistant.data.dao.CategoryDao
import com.jizhang.assistant.data.dao.CycleBillDao
import com.jizhang.assistant.data.dao.LabelDao
import androidx.room.withTransaction
import com.jizhang.assistant.data.dao.RecordDao
import com.jizhang.assistant.data.dao.ReminderDao
import com.jizhang.assistant.data.dao.TemplateDao
import com.jizhang.assistant.data.entity.AssetEntity
import com.jizhang.assistant.data.entity.AssetTransferEntity
import com.jizhang.assistant.data.entity.BookEntity
import com.jizhang.assistant.data.entity.BudgetEntity
import com.jizhang.assistant.data.entity.CategoryEntity
import com.jizhang.assistant.data.entity.CycleBillEntity
import com.jizhang.assistant.data.entity.LabelEntity
import com.jizhang.assistant.data.entity.RecordEntity
import com.jizhang.assistant.data.entity.RecordLabelCrossRef
import com.jizhang.assistant.data.entity.ReminderEntity
import com.jizhang.assistant.data.entity.TemplateEntity
import com.jizhang.assistant.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * 数据仓库层：统一对外提供领域查询，屏蔽 DAO 细节。
 */
class LedgerRepository(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val bookDao: BookDao,
    private val assetDao: AssetDao,
    private val templateDao: TemplateDao,
    private val labelDao: LabelDao,
    private val reminderDao: ReminderDao,
    private val cycleBillDao: CycleBillDao,
    private val database: com.jizhang.assistant.data.database.AppDatabase? = null,
) {

    // ── 分类 ──────────────────────────────────────────────
    fun observeExpenseCategories(): Flow<List<CategoryEntity>> = categoryDao.observeExpense()
    fun observeIncomeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeIncome()
    fun observeAllCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
    suspend fun category(categoryId: String): CategoryEntity? = categoryDao.findById(categoryId)
    suspend fun expenseCategoriesOnce(): List<CategoryEntity> =
        categoryDao.observeExpense().firstOrNull().orEmpty()
    suspend fun incomeCategoriesOnce(): List<CategoryEntity> =
        categoryDao.observeIncome().firstOrNull().orEmpty()
    suspend fun findCategoryByName(name: String, isExpense: Boolean): CategoryEntity? {
        val cats = if (isExpense) expenseCategoriesOnce() else incomeCategoriesOnce()
        return cats.firstOrNull { it.name == name }
            ?: cats.firstOrNull { it.name.contains(name) || name.contains(it.name) }
    }

    suspend fun addCategory(name: String, icon: String, isExpense: Boolean): Long {
        val prefix = if (isExpense) "c_e_" else "c_i_"
        val id = prefix + System.currentTimeMillis()
        return categoryDao.insert(
            CategoryEntity(
                categoryId = id,
                name = name,
                icon = icon,
                type = if (isExpense) CategoryEntity.TYPE_EXPENSE_CUSTOM
                else CategoryEntity.TYPE_INCOME_CUSTOM,
                sortOrder = 1000,
            )
        )
    }

    suspend fun renameCategory(categoryId: String, newName: String) {
        categoryDao.findById(categoryId)?.let {
            categoryDao.update(it.copy(name = newName))
        }
    }

    suspend fun deleteCategory(categoryId: String) = categoryDao.delete(categoryId)

    // ── 账本 ──────────────────────────────────────────────
    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()
    suspend fun book(bid: String): BookEntity? = bookDao.findById(bid)
    suspend fun addBook(name: String, icon: String = "book"): Long {
        val bid = "b_" + System.currentTimeMillis()
        bookDao.insert(
            BookEntity(bid = bid, name = name, icon = icon, sortOrder = 100)
        )
        return 1
    }

    suspend fun renameBook(bid: String, name: String) {
        bookDao.findById(bid)?.let { bookDao.update(it.copy(name = name)) }
    }

    suspend fun deleteBook(bid: String) {
        bookDao.findById(bid)?.let {
            if (it.bid != BookEntity.DEFAULT_BID) bookDao.delete(it)
        }
    }

    // ── 流水 ──────────────────────────────────────────────
    fun observeMonth(year: Int, month: Int, bid: String? = null): Flow<List<RecordEntity>> =
        recordDao.observeMonth(year, month, bid)

    fun search(query: String): Flow<List<RecordEntity>> = recordDao.search(query)

    fun observeMonthTotal(year: Int, month: Int, bid: String? = null): Flow<RecordDao.MonthTotal?> =
        recordDao.observeMonthTotal(year, month, bid)

    fun observeMonthCategoryTotals(
        year: Int,
        month: Int,
        bid: String? = null,
    ): Flow<List<RecordDao.CategoryTotal>> =
        recordDao.observeMonthCategoryTotals(year, month, bid)

    fun observeMonthDailyTotals(year: Int, month: Int, bid: String? = null): Flow<List<RecordDao.DailyTotal>> =
        recordDao.observeMonthDailyTotals(year, month, bid)

    fun observeMonthMemberTotals(year: Int, month: Int, bid: String? = null): Flow<List<RecordDao.MemberTotal>> =
        recordDao.observeMonthMemberTotals(year, month, bid)

    fun observeMonths(): Flow<List<RecordDao.YearMonth>> = recordDao.observeMonths()
    fun observeCheckInDays(): Flow<Int> = recordDao.observeCheckInDays()

    suspend fun record(id: Long): RecordEntity? = recordDao.findById(id)

    /** 新增一笔。金额恒为正，方向由分类决定。关联资产时联动余额。 */
    suspend fun add(
        amount: Double,
        categoryId: String,
        remark: String,
        dateMillis: Long,
        bid: String = BookEntity.DEFAULT_BID,
        member: String = "",
        assetId: Long = 0,
        autoTally: Boolean = false,
    ): Long {
        val cal = TimeUtils.calendarOf(dateMillis)
        val category = categoryDao.findById(categoryId)
        val record = RecordEntity(
            amount = amount,
            categoryId = categoryId,
            remark = remark,
            date = dateMillis,
            year = cal.get(java.util.Calendar.YEAR),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            day = cal.get(java.util.Calendar.DAY_OF_MONTH),
            week = cal.get(java.util.Calendar.WEEK_OF_YEAR),
            ctime = System.currentTimeMillis(),
            bid = bid,
            member = member,
            assetId = assetId,
            autoTally = autoTally,
        )
        val id = recordDao.insert(record)
        // 资产联动：支出减余额，收入加余额
        if (assetId > 0 && category != null) {
            val delta = if (category.isIncome) amount else -amount
            assetDao.adjustBalance(assetId, delta)
        }
        return id
    }

    suspend fun update(record: RecordEntity) = recordDao.update(record)
    suspend fun delete(record: RecordEntity) = recordDao.delete(record)
    suspend fun allRecords(): List<RecordEntity> = recordDao.all()

    // ── AI 一次性查询 ─────────────────────────────────────
    suspend fun monthTotalOnce(year: Int, month: Int): RecordDao.MonthTotal? =
        recordDao.observeMonthTotal(year, month, null).firstOrNull()

    suspend fun monthCategoryTotalsOnce(year: Int, month: Int): List<RecordDao.CategoryTotal> =
        recordDao.observeMonthCategoryTotals(year, month, null).firstOrNull().orEmpty()

    /** 返回 (金额, 日期串, 备注) */
    suspend fun searchRecordsOnce(query: String, year: Int, month: Int): List<Triple<Double, String, String>> {
        val records = recordDao.observeMonth(year, month, null).firstOrNull().orEmpty()
        val cats = expenseCategoriesOnce() + incomeCategoriesOnce()
        return records.filter { r ->
            val catName = cats.firstOrNull { it.categoryId == r.categoryId }?.name ?: ""
            catName.contains(query) || r.remark.contains(query)
        }.map { r ->
            val dateStr = TimeUtils.dayLabel(r.date)
            Triple(r.amount, dateStr, r.remark)
        }
    }

    suspend fun recentRecordsOnce(limit: Int): List<Triple<Double, String, String>> {
        val records = recordDao.all().take(limit)
        val cats = expenseCategoriesOnce() + incomeCategoriesOnce()
        return records.map { r ->
            val catName = cats.firstOrNull { it.categoryId == r.categoryId }?.name ?: ""
            val dateStr = TimeUtils.dayLabel(r.date)
            Triple(r.amount, "$dateStr $catName", r.remark)
        }
    }

    suspend fun currentTotalBudget(monthKey: String): Double? =
        budgetDao.observeMonth(monthKey).firstOrNull()
            ?.firstOrNull { it.categoryId == TOTAL_BUDGET_CATEGORY }?.amount

    // ── 预算 ──────────────────────────────────────────────
    fun observeBudgets(month: String): Flow<List<BudgetEntity>> = budgetDao.observeMonth(month)

    suspend fun setBudget(month: String, categoryId: String, amount: Double) {
        val existing = budgetDao.find(month, categoryId)
        budgetDao.upsert(
            BudgetEntity(
                id = existing?.id ?: 0,
                budgetMonth = month,
                categoryId = categoryId,
                amount = amount,
            )
        )
    }

    suspend fun removeBudget(month: String, categoryId: String) =
        budgetDao.delete(month, categoryId)

    suspend fun categoryMonthSpent(year: Int, month: Int, categoryId: String): Double =
        recordDao.categoryMonthTotal(year, month, categoryId)

    // ── 资产 ──────────────────────────────────────────────
    fun observeAssets(): Flow<List<AssetEntity>> = assetDao.observeAll()
    fun observeTotalBalance(): Flow<Double> = assetDao.observeTotalBalance()
    fun observeTransfers(): Flow<List<AssetTransferEntity>> = assetDao.observeTransfers()
    suspend fun asset(id: Long): AssetEntity? = assetDao.findById(id)
    suspend fun addAsset(asset: AssetEntity): Long = assetDao.insert(asset)
    suspend fun updateAsset(asset: AssetEntity) = assetDao.update(asset)
    suspend fun deleteAsset(asset: AssetEntity) = assetDao.delete(asset)
    suspend fun transfer(from: Long, to: Long, amount: Double, fee: Double, remark: String) {
        assetDao.transfer(
            AssetTransferEntity(
                fromAssetId = from, toAssetId = to, amount = amount,
                fee = fee, remark = remark,
            )
        )
    }

    // ── 模板 ──────────────────────────────────────────────
    fun observeTemplates(): Flow<List<TemplateEntity>> = templateDao.observeAll()
    suspend fun addTemplate(template: TemplateEntity): Long = templateDao.insert(template)
    suspend fun updateTemplate(template: TemplateEntity) = templateDao.update(template)
    suspend fun deleteTemplate(template: TemplateEntity) = templateDao.delete(template)

    // ── 标签 ──────────────────────────────────────────────
    fun observeLabels(): Flow<List<LabelEntity>> = labelDao.observeAll()
    suspend fun addLabel(name: String): Long =
        labelDao.insert(LabelEntity(name = name))
    suspend fun deleteLabel(id: Long) = labelDao.delete(id)
    suspend fun setRecordLabels(recordId: Long, labelIds: List<Long>) {
        labelDao.clearRecordLabels(recordId)
        labelIds.forEach { labelDao.insertCrossRef(RecordLabelCrossRef(recordId, it)) }
    }

    suspend fun labelsOfRecord(recordId: Long): List<LabelEntity> =
        labelDao.labelsOfRecord(recordId)

    // ── 提醒 ──────────────────────────────────────────────
    fun observeReminders(): Flow<List<ReminderEntity>> = reminderDao.observeAll()
    suspend fun addReminder(reminder: ReminderEntity): Long = reminderDao.insert(reminder)
    suspend fun updateReminder(reminder: ReminderEntity) = reminderDao.update(reminder)
    suspend fun deleteReminder(id: Long) = reminderDao.delete(id)
    suspend fun enabledReminders(): List<ReminderEntity> = reminderDao.enabled()

    // ── 周期账 ────────────────────────────────────────────
    fun observeCycleBills(): Flow<List<CycleBillEntity>> = cycleBillDao.observeAll()
    suspend fun addCycleBill(bill: CycleBillEntity): Long = cycleBillDao.insert(bill)
    suspend fun updateCycleBill(bill: CycleBillEntity) = cycleBillDao.update(bill)
    suspend fun deleteCycleBill(bill: CycleBillEntity) = cycleBillDao.delete(bill)

    /** 执行到期的周期账，返回执行条数 */
    suspend fun executeDueCycleBills(): Int {
        val now = System.currentTimeMillis()
        val due = cycleBillDao.dueBills(now)
        var count = 0
        for (bill in due) {
            add(
                amount = bill.amount,
                categoryId = bill.categoryId,
                remark = bill.remark.ifEmpty { bill.name },
                dateMillis = bill.nextDate,
                bid = bill.bid,
                autoTally = true,
            )
            count++
            cycleBillDao.update(bill.copy(lastExecuted = bill.nextDate, nextDate = nextDate(bill)))
        }
        return count
    }

    private fun nextDate(bill: CycleBillEntity): Long {
        val cal = TimeUtils.calendarOf(bill.nextDate)
        when (bill.frequency) {
            CycleBillEntity.FREQ_DAILY -> cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            CycleBillEntity.FREQ_WEEKLY -> cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
            CycleBillEntity.FREQ_MONTHLY -> cal.add(java.util.Calendar.MONTH, 1)
            CycleBillEntity.FREQ_YEARLY -> cal.add(java.util.Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    // ── 打卡/成就统计 ─────────────────────────────────────
    suspend fun streakDays(): Int {
        val days = recordDao.recentDays()
        if (days.isEmpty()) return 0
        val set = days.map { "${it.year}-${it.month}-${it.day}" }.toSet()
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        // 今天没记不打断连续（从昨天起算也允许）
        var streak = 0
        if (!set.contains(keyOf(cal))) cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        while (set.contains(keyOf(cal))) {
            streak++
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }

    private fun keyOf(cal: java.util.Calendar) =
        "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"

    // ── 全量导出/导入（云同步用）──
    suspend fun allCategories(): List<CategoryEntity> = categoryDao.all()
    suspend fun allBooks(): List<BookEntity> = bookDao.all()
    suspend fun allBudgets(): List<BudgetEntity> = budgetDao.all()
    suspend fun allAssets(): List<AssetEntity> = assetDao.all()
    suspend fun allTemplates(): List<TemplateEntity> = templateDao.all()
    suspend fun allCycleBills(): List<CycleBillEntity> = cycleBillDao.all()
    suspend fun allReminders(): List<ReminderEntity> = reminderDao.all()

    /** 清空并导入全部数据（云恢复用） */
    suspend fun replaceAll(data: org.json.JSONObject) {
        val tx: suspend () -> Unit = {
            recordDao.clearAll()
            categoryDao.clearAll()
            bookDao.clearAll()
            budgetDao.clearAll()
            assetDao.clearAll()
            templateDao.clearAll()
            labelDao.clearAll()
            cycleBillDao.clearAll()
            reminderDao.clearAll()

                val records = data.optJSONArray("records")
                if (records != null) {
                    for (i in 0 until records.length()) {
                        val r = records.optJSONObject(i) ?: continue
                        recordDao.insert(
                            RecordEntity(
                                amount = r.optDouble("amount"),
                                categoryId = r.optString("categoryId"),
                                remark = r.optString("remark", ""),
                                date = r.optLong("date"),
                                year = r.optInt("year"), month = r.optInt("month"),
                                day = r.optInt("day"), week = r.optInt("week"),
                                ctime = r.optLong("ctime"),
                                bid = r.optString("bid", BookEntity.DEFAULT_BID),
                                member = r.optString("member", ""),
                                assetId = r.optLong("assetId", 0),
                                autoTally = r.optBoolean("autoTally", false),
                            )
                        )
                    }
                }
                val cats = data.optJSONArray("categories")
                if (cats != null) {
                    for (i in 0 until cats.length()) {
                        val c = cats.optJSONObject(i) ?: continue
                        categoryDao.insertAllEntities(
                            listOf(
                                CategoryEntity(
                                    categoryId = c.optString("categoryId"),
                                    name = c.optString("name"),
                                    icon = c.optString("icon", ""),
                                    type = c.optInt("type", 0),
                                    sortOrder = c.optInt("sortOrder", 0),
                                )
                            )
                        )
                    }
                }
                val books = data.optJSONArray("books")
                if (books != null) {
                    for (i in 0 until books.length()) {
                        val b = books.optJSONObject(i) ?: continue
                        bookDao.insert(
                            BookEntity(
                                bid = b.optString("bid"),
                                name = b.optString("name"),
                                icon = b.optString("icon", "book"),
                                sortOrder = b.optInt("sortOrder", 0),
                                ctime = b.optLong("ctime"),
                            )
                        )
                    }
                }
                val budgets = data.optJSONArray("budgets")
                if (budgets != null) {
                    for (i in 0 until budgets.length()) {
                        val b = budgets.optJSONObject(i) ?: continue
                        budgetDao.upsert(
                            BudgetEntity(
                                budgetMonth = b.optString("budgetMonth"),
                                categoryId = b.optString("categoryId"),
                                amount = b.optDouble("amount"),
                            )
                        )
                    }
                }
                val assets = data.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        assetDao.insert(
                            AssetEntity(
                                name = a.optString("name"),
                                type = a.optString("type", AssetEntity.TYPE_BANK),
                                balance = a.optDouble("balance", 0.0),
                                cardTail = a.optString("cardTail", ""),
                                icon = a.optString("icon", ""),
                                sortOrder = a.optInt("sortOrder", 0),
                                includeInTotal = a.optBoolean("includeInTotal", true),
                                ctime = a.optLong("ctime"),
                            )
                        )
                    }
                }
                val templates = data.optJSONArray("templates")
                if (templates != null) {
                    for (i in 0 until templates.length()) {
                        val t = templates.optJSONObject(i) ?: continue
                        templateDao.insert(
                            TemplateEntity(
                                name = t.optString("name"),
                                categoryId = t.optString("categoryId"),
                                isExpense = t.optBoolean("isExpense", true),
                                amount = t.optDouble("amount", 0.0),
                                remark = t.optString("remark", ""),
                                sortOrder = t.optInt("sortOrder", 0),
                                ctime = t.optLong("ctime"),
                            )
                        )
                    }
                }
                val bills = data.optJSONArray("cycleBills")
                if (bills != null) {
                    for (i in 0 until bills.length()) {
                        val b = bills.optJSONObject(i) ?: continue
                        cycleBillDao.insert(
                            CycleBillEntity(
                                name = b.optString("name"),
                                categoryId = b.optString("categoryId"),
                                isExpense = b.optBoolean("isExpense", true),
                                amount = b.optDouble("amount", 0.0),
                                remark = b.optString("remark", ""),
                                frequency = b.optString("frequency", CycleBillEntity.FREQ_MONTHLY),
                                dayOfMonth = b.optInt("dayOfMonth", 1),
                                monthOfYear = b.optInt("monthOfYear", 1),
                                dayOfWeek = b.optInt("dayOfWeek", 1),
                                nextDate = b.optLong("nextDate"),
                                lastExecuted = b.optLong("lastExecuted", 0),
                                bid = b.optString("bid", BookEntity.DEFAULT_BID),
                                enabled = b.optBoolean("enabled", true),
                            )
                        )
                    }
                }
                val reminders = data.optJSONArray("reminders")
                if (reminders != null) {
                    for (i in 0 until reminders.length()) {
                        val r = reminders.optJSONObject(i) ?: continue
                        reminderDao.insert(
                            ReminderEntity(
                                hour = r.optInt("hour", 21),
                                minute = r.optInt("minute", 0),
                                repeatDays = r.optInt("repeatDays", 0x7F),
                                text = r.optString("text", "该记账啦～"),
                                enabled = r.optBoolean("enabled", true),
                            )
                        )
                    }
                }
        }
        // 事务执行：有数据库实例则用 Room 事务，否则顺序执行
        if (database != null) {
            database!!.withTransaction { tx() }
        } else {
            tx()
        }
    }

    companion object {
        const val TOTAL_BUDGET_CATEGORY = ""
    }
}
