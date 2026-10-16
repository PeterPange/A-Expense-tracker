package com.jizhang.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI 客户端：直连任意 OpenAI 兼容 API。
 * 端点/密钥/模型从应用内配置读取（设置 → AI 服务配置）。
 */
class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * 一次对话调用。
     * @param messages [{role, content}, ...]
     * @return 模型回复文本
     */
    fun chat(messages: List<Pair<String, String>>): String {
        val arr = JSONArray()
        messages.forEach { (role, content) ->
            arr.put(JSONObject().put("role", role).put("content", content))
        }

        val endpoint = com.jizhang.assistant.util.DataStore.aiEndpoint().trimEnd('/')
        val apiKey = com.jizhang.assistant.util.DataStore.aiApiKey()
        val model = com.jizhang.assistant.util.DataStore.aiModel()

        if (endpoint.isBlank() || apiKey.isBlank()) {
            throw AiException("请先在「我的 → AI 服务」中配置 API")
        }
        return chatDirect(arr, endpoint, apiKey, model)
    }

    /** 直连 OpenAI 兼容 API */
    private fun chatDirect(messages: JSONArray, endpoint: String, apiKey: String, model: String): String {
        val url = if (endpoint.endsWith("/chat/completions")) endpoint else "$endpoint/chat/completions"
        val body = JSONObject().apply {
            put("model", model.ifBlank { "deepseek-chat" })
            put("messages", messages)
            put("max_tokens", 2048)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw AiException("API 请求失败 HTTP ${resp.code}: ${text.take(150)}")
            }
            val root = JSONObject(text)
            val choice = root.optJSONArray("choices")?.optJSONObject(0)
                ?: throw AiException("响应缺少 choices")
            return choice.optJSONObject("message")?.optString("content", "")
                ?: throw AiException("响应缺少 message.content")
        }
    }

    class AiException(message: String) : Exception(message)
}
