package com.jizhang.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.staticCompositionLocalOf
import com.jizhang.assistant.cloud.SyncManager
import com.jizhang.assistant.data.repository.LedgerRepository
import com.jizhang.assistant.ui.JizhangAppRoot
import com.jizhang.assistant.ui.login.LoginScreen
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.jizhang.assistant.ui.theme.JizhangTheme
import com.jizhang.assistant.util.DataStore

val LocalRepository = staticCompositionLocalOf<LedgerRepository> {
    error("LedgerRepository not provided")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as JizhangApp
        setContent {
            JizhangTheme {
                CompositionLocalProvider(LocalRepository provides app.repository) {
                    val pin = DataStore.lockPin()
                    var loggedIn by remember { mutableStateOf<Boolean?>(null) }
                    var showUpdateAnnouncement by remember { mutableStateOf(false) }
                    var pendingUpdate by remember { mutableStateOf<com.jizhang.assistant.cloud.UpdateChecker.UpdateInfo?>(null) }

                    // 初始登录状态判定
                    LaunchedEffect(Unit) {
                        loggedIn = DataStore.isLoggedIn() || DataStore.isLoginSkipped()
                    }

                    // 更新公告：版本升级后首次启动显示
                    LaunchedEffect(Unit) {
                        val currentVersion = com.jizhang.assistant.BuildConfig.VERSION_CODE
                        val lastAnnounced = DataStore.lastAnnouncedVersion()
                        if (lastAnnounced < currentVersion) {
                            showUpdateAnnouncement = true
                        }
                        DataStore.setLastAnnouncedVersion(currentVersion)
                    }

                    // 自动检查更新（后台静默）
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000) // 延迟3秒，不阻塞启动
                        val info: com.jizhang.assistant.cloud.UpdateChecker.UpdateInfo? =
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.jizhang.assistant.cloud.UpdateChecker.check(
                                    com.jizhang.assistant.BuildConfig.VERSION_CODE
                                )
                            }
                        if (info != null) {
                            pendingUpdate = info
                        }
                    }

                    // 登录状态就绪后：智能云同步（首次登录/启动时都会触发）
                    LaunchedEffect(loggedIn) {
                        if (loggedIn == true && DataStore.isLoggedIn()) {
                            val sync = SyncManager(app.repository)
                            val result = sync.smartSync()
                            if (!result.ok && result.message.isNotBlank()) {
                                android.util.Log.d("JizhangSync", "同步: ${result.message}")
                            }
                        }
                    }

                    val content: @Composable () -> Unit = {
                        if (loggedIn == null) {
                            // 判断中：空白
                        } else if (loggedIn == false) {
                            LoginScreen(onFinished = { loggedIn = true })
                        } else {
                            JizhangAppRoot()
                        }
                    }

                    if (pin == null) {
                        content()
                    } else {
                        LockGate(correctPin = pin, content = content)
                    }

                    // 更新公告对话框
                    if (showUpdateAnnouncement) {
                        val currentName = com.jizhang.assistant.BuildConfig.VERSION_NAME
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showUpdateAnnouncement = false },
                            title = { Text("🎉 已更新到 v$currentName") },
                            text = {
                                Text(
                                    com.jizhang.assistant.BuildConfig.UPDATE_NOTES,
                                    fontSize = 14.sp,
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { showUpdateAnnouncement = false }
                                ) { Text("知道了") }
                            },
                        )
                    }

                    // 自动检查到新版本
                    pendingUpdate?.let { info ->
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { pendingUpdate = null },
                            title = { Text("发现新版本 v${info.versionName}") },
                            text = {
                                Column {
                                    Text(info.changelog, fontSize = 14.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "大小: ${info.apkSize / 1024 / 1024}MB",
                                        fontSize = 12.sp,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.Button(onClick = {
                                    pendingUpdate = null
                                    val context = this@MainActivity
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        val apk = com.jizhang.assistant.cloud.UpdateChecker.downloadApk(info)
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (apk != null) {
                                                // 触发安装
                                                try {
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        "\${context.packageName}.fileprovider",
                                                        apk,
                                                    )
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    }
                                }) { Text("立即更新") }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { pendingUpdate = null }
                                ) { Text("稍后再说") }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 隐私锁门禁 */
@Composable
private fun LockGate(correctPin: String, content: @Composable () -> Unit) {
    var input by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    if (unlocked) {
        content()
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = 48.sp)
        Text(
            "输入密码解锁记账助手",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        OutlinedTextField(
            value = input,
            onValueChange = { t ->
                val filtered = t.filter { it.isDigit() }.take(4)
                input = filtered
                if (filtered.length == 4) {
                    unlocked = filtered == correctPin
                }
            },
            placeholder = { Text("4 位数字密码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = input.length == 4 && input != correctPin,
            singleLine = true,
        )
        if (input.length == 4 && input != correctPin) {
            Text(
                "密码错误",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = { input = "" }, modifier = Modifier.padding(top = 8.dp)) {
            Text("清空重输")
        }
    }
}
