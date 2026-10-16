package com.jizhang.assistant.ai

import com.jizhang.assistant.data.repository.LedgerRepository
import com.jizhang.assistant.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 记账 AI Agent：JSON 动作协议 + 本地工具执行循环。
 *
 * 协议：模型回复中包含一个 JSON 对象（首个 {...} 块）即视为动作指令：
 *  {"action":"add_record","amount":25,"category":"餐饮","type":"expense",...}
 *  {"action":"query_month_summary","year":2026,"month":10}
 *  {"action":"set_budget","amount":3000}
 *  {"action":"list_categories"}
 *  {"action":"query_recent_records","limit":10}
 *  {"action":"reply","text":"已记：餐饮 -25 元"}
 *
 * App 解析并执行动作，把结果回传给模型继续，直到模型输出 reply 或超轮数。
 */
class AiAgent(private val repository: LedgerRepository) {

    private val client = AiClient()

    /** 会话历史（role to content） */
    private val history = mutableListOf<Pair<String, String>>()

    /** 工具执行状态回调 */
    var onToolEvent: ((ToolEvent) -> Unit)? = null

    sealed class ToolEvent {
        data class Executing(val name: String, val argsSummary: String) : ToolEvent()
        data class Result(val name: String, val ok: Boolean, val summary: String) : ToolEvent()
    }

    /** 发送用户消息，返回助手最终回复 */
    suspend fun send(userText: String): String = withContext(Dispatchers.IO) {
        if (history.isEmpty()) {
            history.add("system" to buildSystemPrompt())
        }
        history.add("user" to userText)

        var rounds = 0
        while (rounds < 4) {
            val reply = try {
                client.chat(history)
            } catch (e: Exception) {
                return@withContext "AI 请求失败：${e.message}"
            }
            history.add("assistant" to reply)

            val action = extractJson(reply)
            if (action == null) {
                // 纯文本回复，直接结束
                return@withContext reply
            }

            // reply 动作：直接结束并返回文本
            if (action.optString("action", "") == "reply") {
                return@withContext action.optString("text", reply).ifBlank { reply }
            }

            val result = executeAction(action)
            history.add("user" to "[系统] 动作执行结果：$result\n请根据结果继续（若已完成，用 {\"action\":\"reply\",\"text\":\"...\"} 总结回复用户）。")
            rounds++
        }
        "（动作轮数超限，请简化请求）"
    }

    /**
     * 解析 OCR 文字为记账信息。
     * 返回 JSON: {"amount":数字, "category":"分类名", "type":"expense/income", "remark":"备注"}
     * 无法解析返回 null。
     */
    suspend fun parseOcrText(ocrText: String): OcrParseResult? = withContext(Dispatchers.IO) {
        val categories = repository.expenseCategoriesOnce()
            .map { it.name } + repository.incomeCategoriesOnce().map { it.name }
        
        val prompt = """
你是记账信息提取器。从 OCR 识别的文字中提取记账信息。

OCR 文字：
$ocrText

可用分类：${categories.joinToString("、")}

规则：
- 提取金额（数字，不含货币符号）
- 判断收支方向（expense=支出 / income=收入）
- 选择最贴切的分类
- 提取备注（如有商品名或描述）
- 如果文字中没有明确的消费/收入信息，返回 {"error":"未找到记账信息"}

只输出一个 JSON 对象，不要其他文字：
{"amount":数字, "category":"分类名", "type":"expense或income", "remark":"备注"}
        """.trimIndent()

        try {
            val reply = client.chat(listOf("system" to "你是信息提取器，只输出JSON。", "user" to prompt))
            // 提取 JSON
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')
            if (start >= 0 && end > start) {
                val json = org.json.JSONObject(reply.substring(start, end + 1))
                if (json.has("error")) return@withContext null
                OcrParseResult(
                    amount = json.optDouble("amount", 0.0),
                    category = json.optString("category", ""),
                    type = json.optString("type", "expense"),
                    remark = json.optString("remark", ""),
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    data class OcrParseResult(
        val amount: Double,
        val category: String,
        val type: String,
        val remark: String,
    )

    /** 清空会话 */
    fun reset() = history.clear()

    // ── 系统提示 ─────────────────────────────────────────
    private suspend fun buildSystemPrompt(): String {
        val expense = repository.expenseCategoriesOnce().joinToString("、") { it.name }
        val income = repository.incomeCategoriesOnce().joinToString("、") { it.name }
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.CHINA).format(Date())
        val budget = repository.currentTotalBudget(
            TimeUtils.monthKey(
                TimeUtils.yearOf(System.currentTimeMillis()),
                TimeUtils.monthOf(System.currentTimeMillis()),
            )
        )
        return """
            你是「记账助手」App 内的 AI 记账管家。当前时间：$now。
            用户会用自然语言记账或查询，你需要输出【一个 JSON 动作对象】让 App 执行。

            可用动作（JSON 字段）：
            1. 记账：{"action":"add_record","amount":数字,"category":"分类名","type":"expense或income","remark":"备注(可选)","date":"yyyy-MM-dd(可选,默认今天)","member":"成员(可选)"}
            2. 查月度汇总：{"action":"query_month_summary","year":年(可选),"month":月(可选)}
            3. 查最近流水：{"action":"query_recent_records","limit":条数(可选,默认10)}
            4. 列分类：{"action":"list_categories"}
            5. 设预算：{"action":"set_budget","amount":数字}
            6. 纯文本回复：{"action":"reply","text":"要说的话"}

            规则：
            - 金额为正数，方向由 type 指定。
            - 分类必须从下面列表选最贴切的：
              支出：$expense
              收入：$income
            - 日期支持"今天/昨天/前天/N天前/M月d日"，换算成 yyyy-MM-dd。
            - 执行结果会以 [系统] 消息返回，你根据结果用 {"action":"reply","text":"..."} 给用户简短总结。
            - reply 的 text 要极简，如："已记：餐饮 -25 元（今天）"。
            - 除 JSON 外不要输出其他文字。
            当前月总预算：${budget ?: "未设置"}
        """.trimIndent()
    }

    // ── JSON 提取与执行 ─────────────────────────────────
    private fun extractJson(text: String): JSONObject? {
        // 找第一个 { 到配对的 }
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            when {
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return try {
                            JSONObject(text.substring(start, i + 1))
                        } catch (e: Exception) { null }
                    }
                }
            }
        }
        return null
    }

    private suspend fun executeAction(action: JSONObject): String {
        val name = action.optString("action", "")
        onToolEvent?.invoke(ToolEvent.Executing(name, action.toString().take(80)))
        return try {
            val result = when (name) {
                "add_record" -> toolAddRecord(action)
                "query_month_summary" -> toolMonthSummary(action)
                "query_recent_records" -> toolRecentRecords(action)
                "list_categories" -> toolListCategories()
                "set_budget" -> toolSetBudget(action)
                "reply" -> "REPLY_DONE"  // 特殊标记：直接结束
                else -> "未知动作：$name"
            }
            onToolEvent?.invoke(ToolEvent.Result(name, true, result.take(80)))
            result
        } catch (e: Exception) {
            val msg = "动作执行失败：${e.message}"
            onToolEvent?.invoke(ToolEvent.Result(name, false, msg))
            msg
        }
    }

    private suspend fun toolAddRecord(args: JSONObject): String {
        val amount = args.optDouble("amount", -1.0)
        if (amount <= 0) return "金额非法"
        val categoryName = args.optString("category", "")
        val isExpense = args.optString("type", "expense") == "expense"
        val category = repository.findCategoryByName(categoryName, isExpense)
            ?: return "找不到分类「$categoryName」，可用分类见 list_categories"
        val date = parseDate(args.optString("date", "")) ?: System.currentTimeMillis()
        val remark = args.optString("remark", "")
        val member = args.optString("member", "")
        val id = repository.add(
            amount = amount,
            categoryId = category.categoryId,
            remark = remark,
            dateMillis = date,
            member = member,
        )
        val dateStr = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(date))
        return "已记账 #$id：${category.name} ${if (isExpense) "-" else "+"}$amount（$dateStr）" +
            if (remark.isNotBlank()) " 备注：$remark" else ""
    }

    private suspend fun toolMonthSummary(args: JSONObject): String {
        val cal = Calendar.getInstance()
        val year = if (args.has("year")) args.getInt("year") else cal.get(Calendar.YEAR)
        val month = if (args.has("month")) args.getInt("month") else cal.get(Calendar.MONTH) + 1
        val total = repository.monthTotalOnce(year, month)
        val cats = repository.monthCategoryTotalsOnce(year, month)
        val sb = StringBuilder()
        sb.append("${year}年${month}月：支出 ${total?.expense ?: 0.0}，收入 ${total?.income ?: 0.0}\n")
        sb.append("分类排行（前8）：\n")
        cats.take(8).forEachIndexed { i, c ->
            sb.append("${i + 1}. ${c.name} ${c.total}（${c.cnt}笔）\n")
        }
        return sb.toString().trim()
    }

    private suspend fun toolRecentRecords(args: JSONObject): String {
        val limit = if (args.has("limit")) args.getInt("limit").coerceIn(1, 30) else 10
        val records = repository.recentRecordsOnce(limit)
        if (records.isEmpty()) return "暂无记录"
        val sb = StringBuilder("最近 $limit 笔：\n")
        records.forEach { r ->
            sb.append("- ${r.second} ${r.first} 元 ${r.third}\n")
        }
        return sb.toString().trim()
    }

    private suspend fun toolListCategories(): String {
        val e = repository.expenseCategoriesOnce().joinToString("、") { it.name }
        val i = repository.incomeCategoriesOnce().joinToString("、") { it.name }
        return "支出：$e\n收入：$i"
    }

    private suspend fun toolSetBudget(args: JSONObject): String {
        val amount = args.optDouble("amount", -1.0)
        if (amount <= 0) return "预算金额非法"
        val monthKey = TimeUtils.monthKey(
            TimeUtils.yearOf(System.currentTimeMillis()),
            TimeUtils.monthOf(System.currentTimeMillis()),
        )
        repository.setBudget(monthKey, LedgerRepository.TOTAL_BUDGET_CATEGORY, amount)
        return "本月总预算已设为 $amount 元"
    }

    private fun parseDate(raw: String): Long? {
        if (raw.isBlank()) return null
        val cal = Calendar.getInstance()
        when (raw.trim()) {
            "今天", "today" -> return cal.timeInMillis
            "昨天", "yesterday" -> {
                cal.add(Calendar.DAY_OF_MONTH, -1); return cal.timeInMillis
            }
            "前天" -> {
                cal.add(Calendar.DAY_OF_MONTH, -2); return cal.timeInMillis
            }
        }
        Regex("(\\d+)天前").find(raw)?.let {
            val n = it.groupValues[1].toInt()
            cal.add(Calendar.DAY_OF_MONTH, -n)
            return cal.timeInMillis
        }
        try {
            return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(raw)?.time
        } catch (e: Exception) { /* ignore */ }
        try {
            val fmt = SimpleDateFormat("M月d日", Locale.CHINA)
            val d = fmt.parse(raw) ?: return null
            val c2 = Calendar.getInstance().apply { time = d }
            return Calendar.getInstance().apply {
                set(Calendar.MONTH, c2.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, c2.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        } catch (e: Exception) { /* ignore */ }
        return null
    }
}
