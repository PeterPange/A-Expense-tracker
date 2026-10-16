package com.jizhang.assistant.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.assistant.LocalRepository
import com.jizhang.assistant.cloud.CloudApi
import com.jizhang.assistant.cloud.UpdateChecker
import com.jizhang.assistant.cloud.SyncManager
import com.jizhang.assistant.ui.Routes
import com.jizhang.assistant.util.DataStore
import com.jizhang.assistant.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的」页面 v2 —— 分组分区布局：
 * ① 用户卡片（登录状态/云同步） ② 记账统计 ③ 记账管理 ④ 资产 ⑤ 数据与同步 ⑥ 偏好设置 ⑦ 关于
 */
@Composable
fun SettingsScreen(onOpen: (String) -> Unit, onLoggedOut: () -> Unit = {}) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loggedIn = DataStore.isLoggedIn()
    var syncBusy by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadSpeed by remember { mutableStateOf(0.0) }
    var downloadBytes by remember { mutableStateOf(0L) }
    var downloadTotal by remember { mutableStateOf(0L) }

    // 统计数据
    var checkInDays by remember { mutableStateOf(0) }
    var streakDays by remember { mutableStateOf(0) }
    var recordCount by remember { mutableStateOf(0) }
    LaunchedEffectStat(repo) { ci, sd, rc -> checkInDays = ci; streakDays = sd; recordCount = rc }

    val syncManager = remember { SyncManager(repo) }
    var syncTick by remember { mutableStateOf(0) }
    val lastSync = remember(syncTick) { DataStore.lastSyncTime() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── 标题 ──
        item {
            Text(
                "我的",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }

        // ── ① 用户卡片 ──
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val avatarPath by com.jizhang.assistant.util.AppThemeState.avatarPath.collectAsState()
                        val displayName by com.jizhang.assistant.util.AppThemeState.displayName.collectAsState()
                        if (avatarPath.isNotBlank() && java.io.File(avatarPath).exists()) {
                            coil.compose.AsyncImage(
                                model = java.io.File(avatarPath),
                                contentDescription = "头像",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        if (loggedIn) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    displayName.ifBlank {
                                        if (loggedIn) DataStore.cloudUsername() else ""
                                    }.firstOrNull()?.toString() ?: "👤",
                                    color = if (loggedIn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                DataStore.displayName().ifBlank {
                                    DataStore.cloudUsername().ifBlank { "未登录" }
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (loggedIn) {
                                    if (lastSync > 0)
                                        "上次同步 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(lastSync))
                                    else "已登录，尚未同步"
                                } else "登录后数据云端备份，换机不丢",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (loggedIn) {
                            TextButton(onClick = { showLogoutConfirm = true }) {
                                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" 退出", fontSize = 13.sp)
                            }
                        }
                    }
                    if (loggedIn) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (syncBusy) return@Button
                                    syncBusy = true
                                    scope.launch(Dispatchers.IO) {
                                        val r = syncManager.push()
                                        withContext(Dispatchers.Main) {
                                            syncBusy = false
                                            syncTick++
                                            Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !syncBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (syncBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(" 同步到云端", fontSize = 13.sp, maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = { showRestoreConfirm = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" 从云端恢复", fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // ── ② 记账统计 ──
        item {
            Spacer(Modifier.height(14.dp))
            StatBar(checkInDays = checkInDays, streakDays = streakDays, recordCount = recordCount)
        }

        // ── ③ 记账管理 ──
        item {
            SectionTitle("记账管理")
            SectionCard {
                SectionRow("多账本", "生活/工作分开记", Icons.Filled.MenuBook) { onOpen(Routes.BOOKS) }
                SectionDivider()
                SectionRow("分类管理", "自定义收支分类", Icons.Filled.Category) { onOpen(Routes.CATEGORY_MANAGE) }
                SectionDivider()
                SectionRow("模板记账", "常用账一键复记", Icons.Filled.Star) { onOpen(Routes.TEMPLATES) }
                SectionDivider()
                SectionRow("周期账", "房租工资自动记", Icons.Filled.EventRepeat) { onOpen(Routes.CYCLE_BILLS) }
            }
        }

        // ── ④ 资产 ──
        item {
            SectionTitle("资产")
            SectionCard {
                SectionRow("资产账户", "余额·转账·信用卡", Icons.Filled.Wallet) { onOpen(Routes.ASSETS) }
            }
        }

        // ── ⑤ 数据 ──
        item {
            SectionTitle("数据")
            SectionCard {
                SectionRow("备份与恢复", "JSON 全量备份到本地", Icons.Filled.Savings) { onOpen(Routes.BACKUP) }
                SectionDivider()
                SectionRow("导出 CSV", "导出流水到文档目录", Icons.Filled.CloudDownload) {
                    // 直接导出
                    scope.launch(Dispatchers.IO) {
                        val records = repo.allRecords()
                        if (records.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "暂无数据", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                            ?: context.filesDir
                        val file = java.io.File(dir, "jizhang_export_${System.currentTimeMillis()}.csv")
                        file.writeText(buildString {
                            append("\uFEFF")
                            appendLine("日期,时间,类型,分类,金额,备注,成员")
                            records.forEach { r ->
                                val category = repo.category(r.categoryId)
                                val type = if (category?.isIncome == true) "收入" else "支出"
                                val date = com.jizhang.assistant.util.TimeUtils.dayLabel(r.date)
                                val time = com.jizhang.assistant.util.TimeUtils.timeLabel(r.date)
                                val remark = r.remark.replace("\"", "\"\"").replace("\n", " ")
                                appendLine("$date,$time,$type,${category?.name ?: ""},${r.amount},\"$remark\",${r.member}")
                            }
                        })
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已导出：${file.absolutePath}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        // ── ⑥ 偏好设置 ──
        item {
            SectionTitle("偏好设置")
            SectionCard {
                SectionRow("本月预算", budgetSubtitle(), Icons.Filled.Savings) { showBudgetDialog = true }
                SectionDivider()
                SectionRow("记账提醒", "定时通知你记账", Icons.Filled.Notifications) { onOpen(Routes.REMINDERS) }
                SectionDivider()
                SectionRow("隐私锁", lockSubtitle(), Icons.Filled.Lock) { showLockDialog = true }
                SectionDivider()
                SectionRow("AI 服务", "配置 API 后可用", Icons.Filled.AutoAwesome) { onOpen(Routes.AI_SETTINGS) }
                SectionDivider()
                SectionRow("个性化设置", "主题配色 · 用户档案", Icons.Filled.Palette) { onOpen(Routes.THEME_PROFILE) }
            }
        }

        // ── ⑦ 关于 ──
        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle("关于")
            SectionCard {
                SectionRow(
                    "检测更新",
                    if (updateBusy) "正在检查…" else "当前 v${com.jizhang.assistant.BuildConfig.VERSION_NAME}",
                    Icons.Filled.CloudDownload,
                ) {
                    if (updateBusy) return@SectionRow
                    updateBusy = true
                    updateError = null
                    scope.launch(Dispatchers.IO) {
                        val info = UpdateChecker.check(com.jizhang.assistant.BuildConfig.VERSION_CODE)
                        withContext(Dispatchers.Main) {
                            updateBusy = false
                            if (info != null) {
                                updateInfo = info
                            } else {
                                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                SectionDivider()
                SectionRow(
                    "版本",
                    "v${com.jizhang.assistant.BuildConfig.VERSION_NAME}",
                    null,
                ) {}
            }
            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.height(100.dp))
        }
    }

    // ── 对话框 ──
    if (showBudgetDialog) {
        BudgetDialogSimple(
            onConfirm = { /* 由 SettingsViewModel 处理 */ showBudgetDialog = false },
            onDismiss = { showBudgetDialog = false },
        )
    }
    if (showLockDialog) {
        LockDialogSimple(
            onSet = { DataStore.setLockPin(it); showLockDialog = false },
            onRemove = { DataStore.setLockPin(null); showLockDialog = false },
            onDismiss = { showLockDialog = false },
        )
    }
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("从云端恢复？") },
            text = { Text("本地数据将被云端数据覆盖，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    syncBusy = true
                    scope.launch(Dispatchers.IO) {
                        val r = syncManager.pull()
                        withContext(Dispatchers.Main) {
                            syncBusy = false
                            syncTick++
                            Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") } },
        )
    }
    // ── 更新对话框 ──
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!downloading) updateInfo = null },
            title = { Text("发现新版本 v${info.versionName}") },
            text = {
                Column {
                    Text(info.changelog, fontSize = 14.sp)
                    if (downloading) {
                        Spacer(Modifier.height(16.dp))
                        // 进度条
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        // 速度 + 大小
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                formatBytes(downloadBytes) + " / " + formatBytes(downloadTotal),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatSpeed(downloadSpeed),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "大小: " + formatBytes(info.apkSize),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                if (downloading) {
                    Text(
                        "下载中…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Button(onClick = {
                        downloading = true
                        downloadProgress = 0f
                        scope.launch(Dispatchers.IO) {
                            val apk = UpdateChecker.downloadApk(info) { bytes, total, speed ->
                                downloadProgress = if (total > 0) bytes.toFloat() / total else 0f
                                downloadBytes = bytes
                                downloadTotal = total
                                downloadSpeed = speed
                            }
                            withContext(Dispatchers.Main) {
                                downloading = false
                                updateInfo = null
                                if (apk != null) {
                                    installApk(context, apk)
                                } else {
                                    Toast.makeText(context, "下载失败，请检查网络", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) { Text("立即更新") }
                }
            },
            dismissButton = {
                if (!downloading) {
                    TextButton(onClick = { updateInfo = null }) { Text("稍后再说") }
                }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录？") },
            text = { Text("本地数据保留，仅断开云同步。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    scope.launch(Dispatchers.IO) {
                        CloudApi.logout()
                        DataStore.clearCloudAccount()
                        withContext(Dispatchers.Main) { onLoggedOut() }
                    }
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LaunchedEffectStat(
    repo: com.jizhang.assistant.data.repository.LedgerRepository,
    onUpdate: (Int, Int, Int) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var streak = 0
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            streak = repo.streakDays()
        }
        repo.observeCheckInDays().collect { days ->
            onUpdate(days, streak, 0)
        }
    }
}

@Composable
private fun budgetSubtitle(): String {
    val repo = LocalRepository.current
    val vm: SettingsViewModel = viewModel(
        key = "settings",
        factory = viewModelFactory { initializer { SettingsViewModel(repo) } },
    )
    val state by vm.uiState.collectAsState()
    return state.budget?.let { "已设置 ¥${Money.format(it)}" } ?: "未设置"
}

@Composable
private fun lockSubtitle(): String = if (DataStore.lockPin() != null) "已开启" else "未开启"

// ── 组件 ──────────────────────────────────────────────────────

@Composable
private fun StatBar(checkInDays: Int, streakDays: Int, recordCount: Int) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem("📅 $checkInDays", "记账天数")
            StatItem("🔥 $streakDays", "连续天数")
            StatItem("📈 $recordCount", "记账月数")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun SectionRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    )
}

// ── 简化对话框 ────────────────────────────────────────────────

@Composable
private fun BudgetDialogSimple(onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    val repo = LocalRepository.current
    val vm: SettingsViewModel = viewModel(
        key = "settings",
        factory = viewModelFactory { initializer { SettingsViewModel(repo) } },
    )
    val state by vm.uiState.collectAsState()
    var text by remember { mutableStateOf(state.budget?.let { Money.format(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置本月总预算") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t.filter { it.isDigit() || it == '.' } },
                    placeholder = { Text("例如 3000") },
                    singleLine = true,
                )
                if (state.budget != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        vm.removeBudget()
                        onDismiss()
                    }) { Text("取消预算", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                text.toDoubleOrNull()?.let {
                    vm.setBudget(it)
                    onDismiss()
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun LockDialogSimple(
    onSet: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val hasLock = DataStore.lockPin() != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasLock) "隐私锁" else "设置隐私锁") },
        text = {
            Column {
                Text(
                    if (hasLock) "输入新密码更换，或关闭隐私锁" else "设置 4 位数字密码，打开 App 时需验证",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t.filter { it.isDigit() }.take(4) },
                    placeholder = { Text("4 位数字") },
                    singleLine = true,
                )
                if (hasLock) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRemove) { Text("关闭隐私锁", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.length == 4) onSet(text) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


/** 触发 APK 安装 */
private fun installApk(context: android.content.Context, apkFile: java.io.File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Android 8+ 需要用户开启"安装未知应用"权限
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}"),
            )
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}


/** 格式化字节数 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1fGB", bytes / 1024.0 / 1024 / 1024)
        bytes >= 1024 * 1024 -> String.format("%.1fMB", bytes / 1024.0 / 1024)
        bytes >= 1024 -> String.format("%.1fKB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

/** 格式化下载速度 */
private fun formatSpeed(speedKBps: Double): String {
    return when {
        speedKBps >= 1024 -> String.format("%.1f MB/s", speedKBps / 1024)
        speedKBps >= 1 -> String.format("%.0f KB/s", speedKBps)
        else -> "计算中…"
    }
}
