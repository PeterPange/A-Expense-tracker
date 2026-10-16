package com.jizhang.assistant.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OCR 客户端：识别图片中的文字。
 * 用于拍照记账：截图/拍照 → OCR 提取文字 → AI 解析为记账信息。
 */
object OcrClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    const val OCR_URL = "http://120.25.175.86:28080/ocr"

    data class OcrResult(val ok: Boolean, val text: String, val count: Int, val error: String?)

    /** 识别图片中的文字 */
    fun recognize(imageFile: File): OcrResult {
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    imageFile.name,
                    imageFile.asRequestBody("image/png".toMediaType()),
                )
                .build()

            val request = Request.Builder()
                .url(OCR_URL)
                .post(body)
                .build()

            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return OcrResult(false, "", 0, "OCR 请求失败 HTTP ${resp.code}")
                }
                val root = JSONObject(text)
                OcrResult(
                    ok = true,
                    text = root.optString("text", ""),
                    count = root.optInt("count", 0),
                    error = null,
                )
            }
        } catch (e: Exception) {
            OcrResult(false, "", 0, "网络错误：${e.message?.take(60)}")
        }
    }
}
