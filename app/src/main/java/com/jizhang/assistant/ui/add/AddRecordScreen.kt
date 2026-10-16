package com.jizhang.assistant.ui.add

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jizhang.assistant.LocalRepository
import com.jizhang.assistant.data.entity.CategoryEntity
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.jizhang.assistant.cloud.OcrClient
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jizhang.assistant.util.CategoryIcons
import com.jizhang.assistant.util.Money
import com.jizhang.assistant.util.TimeUtils

@Composable
fun AddRecordScreen(
    recordId: Long = -1L,
    onDone: () -> Unit,
) {
    val repo = LocalRepository.current
    val vm: AddRecordViewModel = viewModel(
        key = "add_$recordId",
        factory = AddRecordViewModel.Factory(repo, recordId),
    )
    val state by vm.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var ocrBusy by remember { mutableStateOf(false) }
    var ocrResult by remember { mutableStateOf<com.jizhang.assistant.ai.AiAgent.OcrParseResult?>(null) }
    val context = LocalContext.current
    val ocrScope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            ocrBusy = true
            ocrResult = null
            ocrScope.launch(Dispatchers.IO) {
                try {
                    // 复制图片到缓存
                    val cacheFile = java.io.File(context.cacheDir, "ocr_temp.png")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    // OCR 识别
                    val ocr = OcrClient.recognize(cacheFile)
                    if (!ocr.ok || ocr.text.isBlank()) {
                        withContext(Dispatchers.Main) {
                            ocrBusy = false
                            Toast.makeText(context, "OCR 识别失败：${ocr.error ?: "未识别到文字"}", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    // AI 解析
                    val app = context.applicationContext as com.jizhang.assistant.JizhangApp
                    val parse = app.aiAgent.parseOcrText(ocr.text)
                    withContext(Dispatchers.Main) {
                        ocrBusy = false
                        if (parse != null && parse.amount > 0) {
                            ocrResult = parse
                            // 自动填入
                            vm.press(parse.amount.toString())
                            if (parse.remark.isNotBlank()) vm.inputRemark(parse.remark)
                        } else {
                            Toast.makeText(context, "未能从图片中提取记账信息", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        ocrBusy = false
                        Toast.makeText(context, "处理失败：${e.message?.take(50)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶栏：支出/收入切换 + 关闭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
            ExpenseIncomeTabs(
                isExpense = state.isExpense,
                onSwitch = { vm.switchExpenseIncome(it) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }

        // 拍照记账按钮 + 金额展示区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 拍照记账
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (ocrBusy) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        androidx.compose.foundation.shape.CircleShape,
                    )
                    .clickable(enabled = !ocrBusy) { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                if (ocrBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "拍照记账",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // OCR 结果提示
            if (ocrResult != null) {
                Text(
                    "✓ 已识别: ${ocrResult!!.category} ¥${ocrResult!!.amount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    if (ocrBusy) "正在识别…" else "拍照/截图自动记账",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        AmountDisplay(
            expression = state.expression,
            preview = state.preview,
            isExpense = state.isExpense,
        )

        // 分类宫格
        CategoryGrid(
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            onSelect = { vm.selectCategory(it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )

        // 备注 + 成员 + 资产 + 日期
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.remark,
                onValueChange = { vm.inputRemark(it) },
                placeholder = { Text("备注") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.weight(1.6f),
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = state.member,
                onValueChange = { vm.inputMember(it) },
                placeholder = { Text("成员") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            if (state.assets.isNotEmpty()) {
                var assetMenu by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                val currentAsset = state.assets.firstOrNull { it.id == state.selectedAssetId }
                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.clickable { assetMenu = true },
                    ) {
                        Text(
                            currentAsset?.name ?: "资产",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = assetMenu,
                        onDismissRequest = { assetMenu = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("不关联资产") },
                            onClick = { vm.selectAsset(0); assetMenu = false },
                        )
                        state.assets.forEach { asset ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${asset.name}（${Money.formatSigned(asset.balance)}）") },
                                onClick = { vm.selectAsset(asset.id); assetMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.clickable { showDatePicker = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = "日期",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(state.dateLabel, fontSize = 13.sp)
                }
            }
        }

        // 计算器键盘
        CalculatorKeypad(
            onDigit = { vm.press(it) },
            onBackspace = { vm.backspace() },
            onClear = { vm.clear() },
            onDone = { vm.save(onDone) },
            doneEnabled = state.canSave,
        )
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = state.dateMillis,
            onConfirm = { vm.inputDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun ExpenseIncomeTabs(
    isExpense: Boolean,
    onSwitch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
    ) {
        TabChip("支出", isExpense, Expense, onSwitch, true)
        Spacer(Modifier.width(8.dp))
        TabChip("收入", !isExpense, Primary, onSwitch, false)
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onSwitch: (Boolean) -> Unit,
    isExpenseTab: Boolean,
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) color.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(20.dp),
            )
            .border(
                1.dp,
                if (selected) color else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(20.dp),
            )
            .clickable { onSwitch(isExpenseTab) }
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun AmountDisplay(expression: String, preview: String, isExpense: Boolean) {
    val display = expression.ifEmpty { "0" }
    // 长表达式自动缩小字号，避免溢出
    val fontSize = when {
        display.length <= 8 -> 38.sp
        display.length <= 12 -> 30.sp
        display.length <= 18 -> 24.sp
        else -> 19.sp
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "¥",
                fontSize = (fontSize.value * 0.63f).sp,
                color = if (isExpense) Expense else Primary,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Text(
                display,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) Expense else Primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
        if (preview.isNotEmpty() && preview != expression) {
            Text(
                "= $preview",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(categories, key = { it.categoryId }) { category ->
            val selected = category.categoryId == selectedId
            Column(
                modifier = Modifier
                    .clickable { onSelect(category.categoryId) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (selected) {
                                (if (category.isExpense) Expense else Primary).copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            CircleShape,
                        )
                        .border(
                            1.dp,
                            if (selected) {
                                if (category.isExpense) Expense else Primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        CategoryIcons.iconOf(category.categoryId, category.type),
                        contentDescription = category.name,
                        tint = if (selected) {
                            if (category.isExpense) Expense else Primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    category.name,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: Int = 22,
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = label) { onClick() }
            .heightIn(min = 52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = labelColor,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CalculatorKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    doneEnabled: Boolean,
) {
    val operatorColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KeypadKey("7", { onDigit("7") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("8", { onDigit("8") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("9", { onDigit("9") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("⌫", { onBackspace() }, Modifier.weight(1f).heightIn(min = 52.dp), operatorColor)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KeypadKey("4", { onDigit("4") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("5", { onDigit("5") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("6", { onDigit("6") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("÷", { onDigit("÷") }, Modifier.weight(1f).heightIn(min = 52.dp), operatorColor)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KeypadKey("1", { onDigit("1") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("2", { onDigit("2") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("3", { onDigit("3") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("×", { onDigit("×") }, Modifier.weight(1f).heightIn(min = 52.dp), operatorColor)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KeypadKey(".", { onDigit(".") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("0", { onDigit("0") }, Modifier.weight(1f).heightIn(min = 52.dp))
            KeypadKey("+", { onDigit("+") }, Modifier.weight(1f).heightIn(min = 52.dp), operatorColor)
            KeypadKey("-", { onDigit("-") }, Modifier.weight(1f).heightIn(min = 52.dp), operatorColor)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            KeypadKey("C", { onClear() }, Modifier.weight(1f).heightIn(min = 48.dp), operatorColor, fontSize = 18)
            Box(
                modifier = Modifier
                    .weight(3f)
                    .heightIn(min = 48.dp)
                    .padding(3.dp)
                    .background(
                        if (doneEnabled) Primary else Primary.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = doneEnabled) { onDone() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "完成",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let(onConfirm) ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = datePickerState)
    }
}
