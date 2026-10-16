package com.jizhang.assistant.ui.features

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.assistant.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupViewModel(private val repo: com.jizhang.assistant.data.repository.LedgerRepository) : ViewModel() {

    /** 导出全部数据为 JSON 备份文件 */
    suspend fun backup(context: android.content.Context): String? = withContext(Dispatchers.IO) {
        try {
            val records = repo.allRecords()
            val root = JSONObject().apply {
                put("version", 2)
                put("exportTime", System.currentTimeMillis())
                put("records", JSONArray().apply {
                    records.forEach { r ->
                        put(
                            JSONObject().apply {
                                put("amount", r.amount)
                                put("categoryId", r.categoryId)
                                put("remark", r.remark)
                                put("date", r.date)
                                put("bid", r.bid)
                                put("member", r.member)
                            }
                        )
                    }
                })
            }
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backup")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "jizhang_backup_${System.currentTimeMillis()}.json")
            file.writeText(root.toString())
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** 从备份 JSON 恢复（追加，不覆盖现有数据） */
    suspend fun restore(context: android.content.Context, uri: android.net.Uri): Int =
        withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: return@withContext 0
                val root = JSONObject(text)
                val records = root.optJSONArray("records") ?: return@withContext 0
                var count = 0
                for (i in 0 until records.length()) {
                    val r = records.optJSONObject(i) ?: continue
                    repo.add(
                        amount = r.optDouble("amount", 0.0),
                        categoryId = r.optString("categoryId", "e_other"),
                        remark = r.optString("remark", ""),
                        dateMillis = r.optLong("date", System.currentTimeMillis()),
                        bid = r.optString("bid", "default"),
                        member = r.optString("member", ""),
                    )
                    count++
                }
                count
            } catch (e: Exception) {
                -1
            }
        }
}

@Composable
fun BackupScreen() {
    val repo = LocalRepository.current
    val vm: BackupViewModel = viewModel(
        key = "backup",
        factory = viewModelFactory { initializer { BackupViewModel(repo) } },
    )
    val context = LocalContext.current
    var backupPath by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val n = vm.restore(context, it)
                Toast.makeText(
                    context,
                    if (n >= 0) "已恢复 $n 条记录" else "恢复失败",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "备份与恢复",
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("导出备份", fontWeight = FontWeight.SemiBold)
                Text(
                    "把全部流水导出为 JSON 文件（应用目录/backup），可分享保存。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        scope.launch {
                            val path = vm.backup(context)
                            backupPath = path
                            Toast.makeText(
                                context,
                                path?.let { "已备份：$it" } ?: "备份失败",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("立即备份") }

                Text(
                    "恢复数据",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    "从备份 JSON 文件恢复流水（追加模式，不会删除现有数据）。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { restoreLauncher.launch("application/json") },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("选择备份文件恢复") }
            }
        }
    }
}
