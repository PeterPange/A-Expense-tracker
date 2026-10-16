package com.jizhang.assistant

import android.app.Application
import com.jizhang.assistant.data.database.AppDatabase
import com.jizhang.assistant.data.repository.LedgerRepository
import com.jizhang.assistant.util.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JizhangApp : Application() {

    companion object {
        var instance: JizhangApp? = null
            private set
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        DataStore.init(this)
        // 启动时执行到期的周期账（房租/工资等自动入账）
        appScope.launch {
            try {
                repository.executeDueCycleBills()
            } catch (_: Exception) {
            }
        }
    }

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: LedgerRepository by lazy {
        LedgerRepository(
            database.recordDao(),
            database.categoryDao(),
            database.budgetDao(),
            database.bookDao(),
            database.assetDao(),
            database.templateDao(),
            database.labelDao(),
            database.reminderDao(),
            database.cycleBillDao(),
            database,
        )
    }

    val aiAgent: com.jizhang.assistant.ai.AiAgent by lazy {
        com.jizhang.assistant.ai.AiAgent(repository)
    }
}
