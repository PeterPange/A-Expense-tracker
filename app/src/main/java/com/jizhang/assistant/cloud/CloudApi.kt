package com.jizhang.assistant.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 云端服务客户端。
 * 注册/登录/数据同步。
 */
object CloudApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    const val DEFAULT_SERVER = com.jizhang.assistant.BuildConfig.CLOUD_SERVER

    private fun server(): String =
        com.jizhang.assistant.util.DataStore.cloudServer().ifBlank { DEFAULT_SERVER }.trimEnd('/')

    data class AuthResult(val ok: Boolean, val token: String?, val userId: String?, val username: String?, val error: String?)

    fun register(username: String, password: String): AuthResult {
        val body = JSONObject().put("username", username).put("password", password)
        return postAuth("${server()}/api/register", body)
    }

    fun login(username: String, password: String): AuthResult {
        val body = JSONObject().put("username", username).put("password", password)
        return postAuth("${server()}/api/login", body)
    }

    /** 请求邮箱验证码 */
    fun requestEmailCode(email: String): AuthResult {
        try {
            val body = JSONObject().put("email", email)
            val req = Request.Builder()
                .url("${server()}/api/email/code")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                val root = JSONObject(text)
                return AuthResult(root.optBoolean("ok"), null, null, null,
                    if (root.optBoolean("ok")) null else root.optString("error", "发送失败"))
            }
        } catch (e: Exception) {
            return AuthResult(false, null, null, null, "网络错误：${e.message?.take(60)}")
        }
    }

    /** 邮箱验证码登录（自动注册） */
    fun emailLogin(email: String, code: String): AuthResult {
        val body = JSONObject().put("email", email).put("code", code)
        return postAuth("${server()}/api/email/login", body)
    }

    private fun postAuth(url: String, body: JSONObject): AuthResult {
        try {
            val req = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                val root = JSONObject(text)
                if (root.optBoolean("ok")) {
                    return AuthResult(true, root.optString("token"), root.optString("userId"),
                        root.optString("username"), null)
                }
                return AuthResult(false, null, null, null, root.optString("error", "请求失败"))
            }
        } catch (e: Exception) {
            return AuthResult(false, null, null, null, "网络错误：${e.message?.take(60)}")
        }
    }

    fun logout() {
        val token = com.jizhang.assistant.util.DataStore.cloudToken()
        if (token.isBlank()) return
        try {
            val req = Request.Builder()
                .url("${server()}/api/logout")
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    data class SyncResult(val ok: Boolean, val data: JSONObject?, val version: Long, val error: String?)

    /** 上传全量数据快照 */
    fun upload(dataJson: JSONObject): SyncResult {
        val token = com.jizhang.assistant.util.DataStore.cloudToken()
        if (token.isBlank()) return SyncResult(false, null, 0, "未登录")
        try {
            val req = Request.Builder()
                .url("${server()}/api/sync")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(dataJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                val root = JSONObject(text)
                if (root.optBoolean("ok")) return SyncResult(true, null, root.optLong("version"), null)
                return SyncResult(false, null, 0, root.optString("error", "上传失败"))
            }
        } catch (e: Exception) {
            return SyncResult(false, null, 0, "网络错误：${e.message?.take(60)}")
        }
    }

    /** 下载云端数据 */
    fun download(): SyncResult {
        val token = com.jizhang.assistant.util.DataStore.cloudToken()
        if (token.isBlank()) return SyncResult(false, null, 0, "未登录")
        try {
            val req = Request.Builder()
                .url("${server()}/api/sync")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                val root = JSONObject(text)
                if (root.optBoolean("ok")) {
                    val data = root.optJSONObject("data")
                    return SyncResult(true, data, root.optLong("version"), null)
                }
                return SyncResult(false, null, 0, root.optString("error", "下载失败"))
            }
        } catch (e: Exception) {
            return SyncResult(false, null, 0, "网络错误：${e.message?.take(60)}")
        }
    }

    /** 健康检查（登录页检测服务器可达性） */
    fun ping(): Boolean {
        return try {
            val req = Request.Builder().url("${server()}/api/health").get().build()
            http.newCall(req).execute().use { resp ->
                resp.isSuccessful && (resp.body?.string() ?: "").contains("\"ok\":true")
            }
        } catch (_: Exception) {
            false
        }
    }
}
