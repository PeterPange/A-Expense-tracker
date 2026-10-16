package com.jizhang.assistant.cloud

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 应用更新：对接服务器 /api/app/check + /app/latest.apk。
 * 支持断点续传下载 + SHA256 校验 + 实时进度回调。
 */
object UpdateChecker {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val changelog: String,
        val apkUrl: String,
        val apkSize: Long,
        val apkSha256: String,
    )

    /** 检查更新。返回 null 表示无更新或检查失败 */
    fun check(currentVersionCode: Int): UpdateInfo? {
        return try {
            val server = com.jizhang.assistant.util.DataStore.cloudServer()
                .ifBlank { CloudApi.DEFAULT_SERVER }.trimEnd('/')
            val req = Request.Builder()
                .url("$server/api/app/check?versionCode=$currentVersionCode")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JSONObject(resp.body?.string() ?: "")
                if (!root.optBoolean("updateAvailable", false)) return null
                UpdateInfo(
                    versionCode = root.optInt("latestVersionCode", 0),
                    versionName = root.optString("latestVersionName", ""),
                    changelog = root.optString("changelog", ""),
                    apkUrl = root.optString("apkUrl", ""),
                    apkSize = root.optLong("apkSize", 0),
                    apkSha256 = root.optString("apkSha256", ""),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 下载 APK（支持断点续传 + 实时进度）。
     * @param onProgress (已下载字节, 总字节, 速度KB/s)
     * @return 下载的文件，失败返回 null
     */
    fun downloadApk(
        info: UpdateInfo,
        onProgress: (Long, Long, Double) -> Unit = { _, _, _ -> },
    ): File? {
        return try {
            val url = if (info.apkUrl.startsWith("http")) info.apkUrl
                else "${com.jizhang.assistant.util.DataStore.cloudServer().ifBlank { CloudApi.DEFAULT_SERVER }.trimEnd('/')}${info.apkUrl}"
            val cacheDir = com.jizhang.assistant.JizhangApp.instance?.cacheDir ?: return null
            val file = File(cacheDir, "jizhang_update.apk")

            // 断点续传：已有部分文件则从断点继续
            val existing = if (file.exists()) file.length() else 0L
            val reqBuilder = Request.Builder().url(url).get()
            if (existing > 0 && existing < info.apkSize) {
                reqBuilder.header("Range", "bytes=$existing-")
            }

            http.newCall(reqBuilder.build()).execute().use { resp ->
                when (resp.code) {
                    200 -> {
                        // 全量下载
                        val total = resp.body?.contentLength() ?: info.apkSize
                        var downloaded = 0L
                        val startTime = System.currentTimeMillis()
                        file.outputStream().use { out ->
                            val input = resp.body?.byteStream() ?: return null
                            val buf = ByteArray(8192)
                            var n: Int
                            var lastReport = 0L
                            while (input.read(buf).also { n = it } > 0) {
                                out.write(buf, 0, n)
                                downloaded += n
                                val now = System.currentTimeMillis()
                                // 每 200ms 报告一次进度
                                if (now - lastReport > 200 || downloaded >= total) {
                                    val elapsed = (now - startTime).coerceAtLeast(1)
                                    val speed = downloaded / 1024.0 / (elapsed / 1000.0)
                                    onProgress(downloaded, total, speed)
                                    lastReport = now
                                }
                            }
                        }
                    }
                    206 -> {
                        // 断点续传，追加
                        val total = info.apkSize
                        var downloaded = existing
                        val startTime = System.currentTimeMillis()
                        java.io.FileOutputStream(file, true).use { out ->
                            val input = resp.body?.byteStream() ?: return null
                            val buf = ByteArray(8192)
                            var n: Int
                            var lastReport = 0L
                            while (input.read(buf).also { n = it } > 0) {
                                out.write(buf, 0, n)
                                downloaded += n
                                val now = System.currentTimeMillis()
                                if (now - lastReport > 200 || downloaded >= total) {
                                    val elapsed = (now - startTime).coerceAtLeast(1)
                                    val speed = (downloaded - existing) / 1024.0 / (elapsed / 1000.0)
                                    onProgress(downloaded, total, speed)
                                    lastReport = now
                                }
                            }
                        }
                    }
                    else -> return null
                }
            }

            // SHA256 校验
            if (info.apkSha256.isNotBlank()) {
                val sha256 = file.inputStream().use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        digest.update(buf, 0, n)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                if (sha256 != info.apkSha256) {
                    file.delete()
                    return null
                }
            }

            if (file.length() > 1000000) file else null
        } catch (_: Exception) {
            null
        }
    }
}
