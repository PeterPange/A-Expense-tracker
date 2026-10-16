package com.jizhang.assistant.cloud

import com.jizhang.assistant.data.repository.LedgerRepository
import com.jizhang.assistant.util.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 云同步管理器：全量快照上传/下载。
 *
 * 策略（简单可靠）：
 * - 上传：导出全部本地数据 → 覆盖云端（带版本冲突检测）
 * - 恢复：下载云端快照 → 清空本地 → 全量导入
 */
class SyncManager(private val repository: LedgerRepository) {

    /** 导出全部本地数据为 JSON */
    suspend fun exportAll(): JSONObject = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "jizhang-assistant")
        root.put("version", DataStore.cloudVersion().coerceAtLeast(System.currentTimeMillis()))
        root.put("exportedAt", System.currentTimeMillis())

        root.put("records", JSONArray().apply {
            repository.allRecords().forEach { r ->
                put(JSONObject().apply {
                    put("amount", r.amount); put("categoryId", r.categoryId)
                    put("remark", r.remark); put("date", r.date)
                    put("year", r.year); put("month", r.month); put("day", r.day)
                    put("week", r.week); put("ctime", r.ctime)
                    put("bid", r.bid); put("member", r.member)
                    put("assetId", r.assetId); put("autoTally", r.autoTally)
                })
            }
        })
        root.put("categories", JSONArray().apply {
            repository.allCategories().forEach { c ->
                put(JSONObject().apply {
                    put("categoryId", c.categoryId); put("name", c.name)
                    put("icon", c.icon); put("type", c.type); put("sortOrder", c.sortOrder)
                })
            }
        })
        root.put("books", JSONArray().apply {
            repository.allBooks().forEach { b ->
                put(JSONObject().apply {
                    put("bid", b.bid); put("name", b.name)
                    put("icon", b.icon); put("sortOrder", b.sortOrder); put("ctime", b.ctime)
                })
            }
        })
        root.put("budgets", JSONArray().apply {
            repository.allBudgets().forEach { b ->
                put(JSONObject().apply {
                    put("budgetMonth", b.budgetMonth); put("categoryId", b.categoryId)
                    put("amount", b.amount)
                })
            }
        })
        root.put("assets", JSONArray().apply {
            repository.allAssets().forEach { a ->
                put(JSONObject().apply {
                    put("name", a.name); put("type", a.type); put("balance", a.balance)
                    put("cardTail", a.cardTail); put("icon", a.icon)
                    put("sortOrder", a.sortOrder); put("includeInTotal", a.includeInTotal)
                    put("ctime", a.ctime)
                })
            }
        })
        root.put("templates", JSONArray().apply {
            repository.allTemplates().forEach { t ->
                put(JSONObject().apply {
                    put("name", t.name); put("categoryId", t.categoryId)
                    put("isExpense", t.isExpense); put("amount", t.amount)
                    put("remark", t.remark); put("sortOrder", t.sortOrder); put("ctime", t.ctime)
                })
            }
        })
        root.put("cycleBills", JSONArray().apply {
            repository.allCycleBills().forEach { b ->
                put(JSONObject().apply {
                    put("name", b.name); put("categoryId", b.categoryId)
                    put("isExpense", b.isExpense); put("amount", b.amount)
                    put("remark", b.remark); put("frequency", b.frequency)
                    put("dayOfMonth", b.dayOfMonth); put("monthOfYear", b.monthOfYear)
                    put("dayOfWeek", b.dayOfWeek); put("nextDate", b.nextDate)
                    put("lastExecuted", b.lastExecuted); put("bid", b.bid); put("enabled", b.enabled)
                })
            }
        })
        root.put("reminders", JSONArray().apply {
            repository.allReminders().forEach { r ->
                put(JSONObject().apply {
                    put("hour", r.hour); put("minute", r.minute)
                    put("repeatDays", r.repeatDays); put("text", r.text); put("enabled", r.enabled)
                })
            }
        })
        root
    }

    /** 导入云端快照（清空本地后全量写入） */
    suspend fun importAll(data: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            repository.replaceAll(data)
            DataStore.setCloudVersion(data.optLong("version", System.currentTimeMillis()))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 上传本地数据到云端 */
    suspend fun push(): Result = withContext(Dispatchers.IO) {
        if (!DataStore.isLoggedIn()) return@withContext Result(false, "未登录")
        val data = exportAll()
        val r = CloudApi.upload(data)
        if (r.ok) {
            DataStore.setCloudVersion(r.version)
            DataStore.setLastSyncTime(System.currentTimeMillis())
            Result(true, "已同步 ${data.optJSONArray("records")?.length() ?: 0} 条记录")
        } else {
            Result(false, r.error ?: "上传失败")
        }
    }

    /** 从云端恢复（覆盖本地） */
    suspend fun pull(): Result = withContext(Dispatchers.IO) {
        if (!DataStore.isLoggedIn()) return@withContext Result(false, "未登录")
        val r = CloudApi.download()
        if (!r.ok) return@withContext Result(false, r.error ?: "下载失败")
        val data = r.data ?: return@withContext Result(false, "云端暂无数据")
        val ok = importAll(data)
        if (ok) {
            DataStore.setLastSyncTime(System.currentTimeMillis())
            Result(true, "已恢复 ${data.optJSONArray("records")?.length() ?: 0} 条记录")
        } else {
            Result(false, "数据导入失败")
        }
    }

    /** 登录后的智能同步：本地空→拉取；云端空→上传；都有→版本新者优先 */
    suspend fun smartSync(): Result = withContext(Dispatchers.IO) {
        if (!DataStore.isLoggedIn()) return@withContext Result(false, "未登录")
        val localCount = repository.allRecords().size
        val remote = CloudApi.download()
        if (!remote.ok) return@withContext Result(false, remote.error ?: "连接失败")
        val remoteData = remote.data
        val remoteCount = remoteData?.optJSONArray("records")?.length() ?: 0

        when {
            localCount == 0 && remoteCount > 0 -> {
                importAll(remoteData!!)
                Result(true, "已从云端恢复 $remoteCount 条记录")
            }
            localCount > 0 && remoteCount == 0 -> push()
            localCount == 0 && remoteCount == 0 -> Result(true, "云端和本地均为空")
            else -> {
                // 都有数据：云端版本更新则提示恢复，否则保持本地
                val remoteVersion = remote.version
                val localVersion = DataStore.cloudVersion()
                if (remoteVersion > localVersion) {
                    Result(false, "云端有新数据（$remoteCount 条），建议「从云端恢复」")
                } else {
                    push()
                }
            }
        }
    }

    data class Result(val ok: Boolean, val message: String)
}
