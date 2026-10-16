package com.jizhang.assistant.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jizhang.assistant.ui.theme.Themes
import com.jizhang.assistant.util.DataStore
import java.io.File

/**
 * 主题选择 + 用户档案编辑。
 */
@Composable
fun ThemeProfileScreen() {
    var themeMode by remember { mutableStateOf(com.jizhang.assistant.util.AppThemeState.mode.value) }
    var themeAccent by remember { mutableStateOf(com.jizhang.assistant.util.AppThemeState.accent.value) }
    var displayName by remember { mutableStateOf(com.jizhang.assistant.util.AppThemeState.displayName.value) }
    var avatarPath by remember { mutableStateOf(com.jizhang.assistant.util.AppThemeState.avatarPath.value) }

    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 复制到私有目录
            val context = com.jizhang.assistant.JizhangApp.instance ?: return@let
            val dest = File(context.filesDir, "avatar.jpg")
            context.contentResolver.openInputStream(it)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            avatarPath = dest.absolutePath
            com.jizhang.assistant.util.AppThemeState.updateProfile(displayName, dest.absolutePath)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("个性化设置", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // ── 用户档案 ──
        Text("用户档案", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                // 头像
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 头像选择
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { avatarLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarPath.isNotBlank() && File(avatarPath).exists()) {
                            AsyncImage(
                                model = File(avatarPath),
                                contentDescription = "头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(CircleShape),
                            )
                        } else {
                            Text(
                                (displayName.ifBlank { "记" }).firstOrNull()?.toString() ?: "记",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("点击头像更换", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("支持 JPG / PNG", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 显示名称
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── 主题模式 ──
        Text("外观模式", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                "auto" to "跟随系统",
                "light" to "浅色",
                "dark" to "深色",
            ).forEach { (mode, label) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (themeMode == mode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { themeMode = mode },
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        color = if (themeMode == mode) Color.White
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── 主题配色 ──
        Text("主题配色", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Themes.accents.forEach { (accent, label) ->
                val color = Themes.colors(accent).primary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            themeAccent = accent.name
                            com.jizhang.assistant.util.AppThemeState.updateTheme(accent.name, themeMode)
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (themeAccent == accent.name) 3.dp else 1.dp,
                                color = if (themeAccent == accent.name) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        fontSize = 12.sp,
                        color = if (themeAccent == accent.name) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── 保存按钮 ──
        Button(
            onClick = {
                com.jizhang.assistant.util.AppThemeState.updateTheme(themeAccent, themeMode)
                com.jizhang.assistant.util.AppThemeState.updateProfile(displayName.trim(), avatarPath)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text("保存设置", fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                displayName = ""
                avatarPath = ""
                com.jizhang.assistant.util.AppThemeState.updateProfile("", "")
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("重置档案", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
