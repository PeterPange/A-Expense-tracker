package com.jizhang.assistant.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.assistant.util.DataStore

/**
 * AI 服务配置页。
 * 配置任意 OpenAI 兼容 API 即可启用 AI 功能。
 */
@Composable
fun AiSettingsScreen() {
    var endpoint by remember { mutableStateOf(DataStore.aiEndpoint()) }
    var apiKey by remember { mutableStateOf(DataStore.aiApiKey()) }
    var model by remember { mutableStateOf(DataStore.aiModel()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("AI 服务配置", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "配置任意 OpenAI 兼容 API 即可使用 AI 功能。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(20.dp))

        Text("API 配置", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("API 地址（如 https://api.deepseek.com/v1）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型名（如 deepseek-chat）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "常用服务参考：\n· DeepSeek: https://api.deepseek.com/v1（deepseek-chat）\n" +
                "· Kimi: https://api.moonshot.cn/v1（moonshot-v1-8k）\n" +
                "· 智谱: https://open.bigmodel.cn/api/paas/v4（glm-4-flash）\n" +
                "· 阿里百炼: https://dashscope.aliyuncs.com/compatible-mode/v1（qwen-turbo）",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Row {
            Button(onClick = {
                DataStore.setAiEndpoint(endpoint.trim())
                DataStore.setAiApiKey(apiKey.trim())
                DataStore.setAiModel(model.trim())
            }) { Text("保存配置") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = {
                endpoint = ""
                apiKey = ""
                model = ""
                DataStore.setAiEndpoint("")
                DataStore.setAiApiKey("")
                DataStore.setAiModel("")
            }) { Text("清空") }
        }
    }
}
