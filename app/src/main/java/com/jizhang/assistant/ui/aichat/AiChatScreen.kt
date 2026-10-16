package com.jizhang.assistant.ui.aichat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jizhang.assistant.JizhangApp
import com.jizhang.assistant.ui.theme.Primary
import java.io.File

@Composable
fun AiChatScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JizhangApp
    val vm: AiChatViewModel = viewModel(
        key = "aichat",
        factory = AiChatViewModel.Factory(app.aiAgent, app),
    )
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.attachImage(it) } }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) vm.send(text)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        // ── 顶栏 ──
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "AI 记账管家",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
                IconButton(onClick = { vm.resetChat() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "新会话")
                }
            }
        }

        // ── 消息列表 ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp, vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
            if (state.busy) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("思考中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ── 输入区（豆包风格）──
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                // 图片预览（有待发送图片时显示）
                state.pendingImagePath?.let { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 图片缩略图
                        AsyncImage(
                            model = File(path),
                            contentDescription = "待发送图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "添加描述…（可选）",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        // 移除按钮
                        IconButton(
                            onClick = { vm.removePendingImage() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除图片",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // 输入行
                Row(
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // 📷 按钮
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = "上传图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 🎤 按钮
                    IconButton(onClick = {
                        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "说出你要记的账")
                        }
                        try { voiceLauncher.launch(intent) } catch (_: Exception) {}
                    }) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "语音输入",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 文字输入框
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = { vm.inputChange(it) },
                        placeholder = {
                            Text(
                                if (state.pendingImagePath != null) "描述这张图片…"
                                else "说点什么，或上传截图",
                                fontSize = 14.sp,
                            )
                        },
                        maxLines = 3,
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    // 发送按钮
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (vm.canSend()) Primary else Primary.copy(alpha = 0.3f),
                                CircleShape,
                            )
                            .clickable(enabled = vm.canSend()) { vm.send() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatMessage.Role.USER
    val isTool = msg.role == ChatMessage.Role.TOOL
    val isError = msg.role == ChatMessage.Role.ERROR

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            // 用户消息：图片 + 文字
            if (isUser && msg.imagePath != null) {
                AsyncImage(
                    model = File(msg.imagePath),
                    contentDescription = "用户图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
            if (msg.text.isNotBlank() || (isUser && msg.imagePath == null)) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    ),
                    color = when {
                        isUser -> Primary
                        isTool -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        isError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Text(
                        msg.text,
                        fontSize = 14.sp,
                        color = when {
                            isUser -> Color.White
                            isTool -> MaterialTheme.colorScheme.onSurfaceVariant
                            isError -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
