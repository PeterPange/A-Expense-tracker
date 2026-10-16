package com.jizhang.assistant.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jizhang.assistant.data.dao.AssetDao
import com.jizhang.assistant.data.dao.BookDao
import com.jizhang.assistant.data.dao.BudgetDao
import com.jizhang.assistant.data.dao.CategoryDao
import com.jizhang.assistant.data.dao.CycleBillDao
import com.jizhang.assistant.data.dao.LabelDao
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        RecordEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        BookEntity::class,
        AssetEntity::class,
        AssetTransferEntity::class,
        TemplateEntity::class,
        LabelEntity::class,
        RecordLabelCrossRef::class,
        CycleBillEntity::class,
        ReminderEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun bookDao(): BookDao
    abstract fun assetDao(): AssetDao
    abstract fun templateDao(): TemplateDao
    abstract fun labelDao(): LabelDao
    abstract fun reminderDao(): ReminderDao
    abstract fun cycleBillDao(): CycleBillDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2：record 加列 + 8 张新表 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE record ADD COLUMN bid TEXT NOT NULL DEFAULT 'default'"
                )
                db.execSQL(
                    "ALTER TABLE record ADD COLUMN member TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE record ADD COLUMN assetId INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE record ADD COLUMN autoTally INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_record_bid ON record(bid)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS book (
                        bid TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        ctime INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS asset (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        balance REAL NOT NULL,
                        cardTail TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        includeInTotal INTEGER NOT NULL,
                        ctime INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS asset_transfer (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fromAssetId INTEGER NOT NULL,
                        toAssetId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        fee REAL NOT NULL,
                        remark TEXT NOT NULL,
                        date INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS template (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        isExpense INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        remark TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        ctime INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS label (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_label_name ON label(name)"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS record_label (
                        recordId INTEGER NOT NULL,
                        labelId INTEGER NOT NULL,
                        PRIMARY KEY(recordId, labelId)
                    )""".trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_record_label_recordId ON record_label(recordId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_record_label_labelId ON record_label(labelId)"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS cycle_bill (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        isExpense INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        remark TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        dayOfMonth INTEGER NOT NULL,
                        monthOfYear INTEGER NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        nextDate INTEGER NOT NULL,
                        lastExecuted INTEGER NOT NULL,
                        bid TEXT NOT NULL,
                        enabled INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS reminder (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        repeatDays INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        enabled INTEGER NOT NULL
                    )""".trimIndent()
                )
                // 迁移路径补种默认账本
                db.execSQL(
                    "INSERT OR IGNORE INTO book (bid, name, icon, sortOrder, ctime) " +
                        "VALUES ('default', '默认账本', 'book', 0, ${System.currentTimeMillis()})"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return Room.databaseBuilder(context, AppDatabase::class.java, "jizhang.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch { seedDefaults(get(context)) }
                    }
                })
                .build()
        }

        /** 默认分类（34 支出 + 5 收入）+ 默认账本 */
        private suspend fun seedDefaults(db: AppDatabase) {
            val categoryDao = db.categoryDao()
            if (categoryDao.count() == 0) {
                val e = CategoryEntity.TYPE_EXPENSE_SYSTEM
                val i = CategoryEntity.TYPE_INCOME_SYSTEM
                val defaults = listOf(
                    "e_catering" to "餐饮", "e_vegetable" to "买菜", "e_fruit" to "水果",
                    "e_snack" to "零食", "e_smoke" to "烟酒", "e_beauty" to "美妆",
                    "e_dress" to "服饰", "e_home" to "居家", "e_commodity" to "日用品",
                    "e_communicate" to "通讯", "e_traffic" to "交通", "e_car" to "汽车",
                    "e_house" to "住房", "e_medical" to "医疗", "e_elder" to "长辈",
                    "e_child" to "孩子", "e_friend" to "亲友", "e_money" to "人情",
                    "e_gift" to "礼物", "e_donate" to "捐赠", "e_lottery" to "彩票",
                    "e_shopping" to "购物", "e_digital" to "数码", "e_entertainment" to "娱乐",
                    "e_sport" to "运动", "e_travel" to "旅游", "e_pet" to "宠物",
                    "e_study" to "学习", "e_books" to "书籍", "e_office" to "办公",
                    "e_express" to "快递", "e_repair" to "维修", "e_social" to "社交",
                    "i_wage" to "工资", "i_parttime" to "兼职", "i_finance" to "理财",
                    "i_bonus" to "红包", "i_other" to "其他",
                ).mapIndexed { idx, (id, name) ->
                    val isIncome = id.startsWith("i_")
                    CategoryEntity(
                        categoryId = id,
                        name = name,
                        icon = id,
                        type = if (isIncome) i else e,
                        sortOrder = idx,
                    )
                }
                categoryDao.insertAll(defaults)
            }
            if (db.bookDao().count() == 0) {
                db.bookDao().insert(
                    BookEntity(
                        bid = BookEntity.DEFAULT_BID,
                        name = BookEntity.DEFAULT_NAME,
                        sortOrder = 0,
                    )
                )
            }
        }
    }
}
