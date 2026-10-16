package com.jizhang.assistant.ui.aichat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jizhang.assistant.ai.AiAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 聊天消息模型 */
data class ChatMessage(
    val id: Long = System.nanoTime(),
    val role: Role,
    val text: String,
    val imagePath: String? = null,  // 本地图片路径（用户发送的图片）
    val toolStatus: String? = null,
) {
    enum class Role { USER, ASSISTANT, TOOL, ERROR }
}

data class AiChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val pendingImagePath: String? = null,  // 待发送的图片（预览中）
)

class AiChatViewModel(
    private val agent: AiAgent,
    private val application: Application,
) : ViewModel() {

    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val input = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val pendingImagePath = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AiChatUiState> = kotlinx.coroutines.flow.combine(
        messages, input, busy, pendingImagePath,
    ) { m, i, b, p -> AiChatUiState(m, i, b, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiChatUiState())

    init {
        agent.onToolEvent = { event ->
            viewModelScope.launch {
                when (event) {
                    is AiAgent.ToolEvent.Executing -> appendTool("⚙️ ${event.name}：${event.argsSummary}")
                    is AiAgent.ToolEvent.Result ->
                        appendTool(if (event.ok) "✅ ${event.summary}" else "❌ ${event.summary}")
                }
            }
        }
        messages.value = listOf(
            ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                text = "你好！我是 AI 记账管家\n\n试试对我说：\n· 午饭花了 25\n· 昨天打车 18.5\n· 这个月花了多少\n· 每月预算设 3000\n\n也可以点 📷 上传消费截图，我来帮你识别记账",
            )
        )
    }

    fun inputChange(text: String) { input.value = text }

    /** 选择图片（预览，不发送） */
    fun attachImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheFile = File(application.cacheDir, "chat_img_${System.currentTimeMillis()}.jpg")
                application.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    pendingImagePath.value = cacheFile.absolutePath
                }
            } catch (e: Exception) {
                append(ChatMessage(role = ChatMessage.Role.ERROR, text = "图片读取失败：${e.message?.take(50)}"))
            }
        }
    }

    /** 移除待发送的图片 */
    fun removePendingImage() { pendingImagePath.value = null }

    /** 是否可以发送 */
    fun canSend(): Boolean = !busy.value && (input.value.isNotBlank() || pendingImagePath.value != null)

    /** 发送消息（文字 + 可选图片） */
    fun send(text: String? = null) {
        if (busy.value) return
        val content = (text ?: input.value).trim()
        val imagePath = pendingImagePath.value
        if (content.isEmpty() && imagePath == null) return

        input.value = ""
        pendingImagePath.value = null

        // 显示用户消息（含图片缩略图）
        append(ChatMessage(
            role = ChatMessage.Role.USER,
            text = content,
            imagePath = imagePath,
        ))

        if (imagePath != null) {
            // 有图片：OCR → AI
            sendWithImage(imagePath, content)
        } else {
            // 纯文字：直接发 AI
            sendToAi(content)
        }
    }

    /** 图片 + 文字 → OCR → AI */
    private fun sendWithImage(imagePath: String, userText: String) {
        busy.value = true
        viewModelScope.launch {
            try {
                appendTool("🔍 正在识别图片…")
                val ocr = withContext(Dispatchers.IO) {
                    com.jizhang.assistant.cloud.OcrClient.recognize(File(imagePath))
                }
                if (!ocr.ok || ocr.text.isBlank()) {
                    append(ChatMessage(role = ChatMessage.Role.ERROR, text = "OCR 识别失败：${ocr.error ?: "未识别到文字"}"))
                    busy.value = false
                    return@launch
                }
                appendTool("📝 ${ocr.text.take(80)}${if (ocr.text.length > 80) "…" else ""}")

                // 组合 OCR 文字 + 用户补充文字
                val combined = buildString {
                    append("我从图片中识别到以下内容：\n${ocr.text}")
                    if (userText.isNotBlank()) {
                        append("\n\n用户补充说明：$userText")
                    }
                    append("\n\n请帮我记账。")
                }
                sendToAi(combined)
            } catch (e: Exception) {
                append(ChatMessage(role = ChatMessage.Role.ERROR, text = "图片处理失败：${e.message?.take(50)}"))
                busy.value = false
            }
        }
    }

    /** 发送到 AI */
    private fun sendToAi(content: String) {
        busy.value = true
        viewModelScope.launch {
            try {
                val reply = agent.send(content)
                append(ChatMessage(role = ChatMessage.Role.ASSISTANT, text = reply))
            } catch (e: Exception) {
                append(ChatMessage(role = ChatMessage.Role.ERROR, text = "出错了：${e.message}"))
            } finally {
                busy.value = false
            }
        }
    }

    fun resetChat() {
        agent.reset()
        messages.value = listOf(
            ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "会话已清空，重新开始吧！")
        )
    }

    private fun append(msg: ChatMessage) {
        messages.value = messages.value + msg
    }

    private fun appendTool(text: String) {
        messages.value = messages.value + ChatMessage(role = ChatMessage.Role.TOOL, text = text)
    }

    class Factory(
        private val agent: AiAgent,
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AiChatViewModel(agent, application) as T
    }
}
