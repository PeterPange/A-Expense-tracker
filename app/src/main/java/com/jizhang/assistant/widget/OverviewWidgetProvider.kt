package com.jizhang.assistant.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.jizhang.assistant.MainActivity
import com.jizhang.assistant.R
import com.jizhang.assistant.data.database.AppDatabase
import com.jizhang.assistant.data.repository.LedgerRepository
import com.jizhang.assistant.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 收支概览桌面小部件：显示本月支出/收入，点击打开 App。
 */
class OverviewWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val db = AppDatabase.get(context)
        val repo = LedgerRepository(
            db.recordDao(), db.categoryDao(), db.budgetDao(), db.bookDao(),
            db.assetDao(), db.templateDao(), db.labelDao(), db.reminderDao(),
            db.cycleBillDao(),
        )
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        scope.launch {
            val total = repo.monthTotalOnce(year, month)
            val views = RemoteViews(context.packageName, R.layout.widget_overview).apply {
                setTextViewText(
                    R.id.widget_expense,
                    "支出 ¥${Money.format(total?.expense ?: 0.0)}",
                )
                setTextViewText(
                    R.id.widget_income,
                    "收入 ¥${Money.format(total?.income ?: 0.0)}",
                )
            }
            appWidgetIds.forEach { id ->
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }
}
