package com.jizhang.assistant.ui.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.assistant.LocalRepository
import com.jizhang.assistant.data.entity.CycleBillEntity
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary
import com.jizhang.assistant.util.CategoryIcons
import com.jizhang.assistant.util.Money
import com.jizhang.assistant.util.TimeUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CycleBillsViewModel(private val repo: com.jizhang.assistant.data.repository.LedgerRepository) : ViewModel() {

    val state: StateFlow<Pair<List<CycleBillEntity>, List<com.jizhang.assistant.data.entity.CategoryEntity>>> =
        combine(
            repo.observeCycleBills(),
            combine(repo.observeExpenseCategories(), repo.observeIncomeCategories()) { e, i -> e + i },
        ) { b, c -> b to c }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CycleBillEntity>() to emptyList())

    fun add(bill: CycleBillEntity) { viewModelScope.launch { repo.addCycleBill(bill) } }
    fun update(bill: CycleBillEntity) { viewModelScope.launch { repo.updateCycleBill(bill) } }
    fun delete(bill: CycleBillEntity) { viewModelScope.launch { repo.deleteCycleBill(bill) } }
    fun executeNow() { viewModelScope.launch { repo.executeDueCycleBills() } }
}

@Composable
fun CycleBillsScreen() {
    val repo = LocalRepository.current
    val vm: CycleBillsViewModel = viewModel(
        key = "cyclebills",
        factory = viewModelFactory { initializer { CycleBillsViewModel(repo) } },
    )
    val state by vm.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "周期账",
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            "房租、工资等固定收支，打开 App 时自动入账",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(state.first, key = { it.id }) { bill ->
                val cat = state.second.firstOrNull { it.categoryId == bill.categoryId }
                ListItem(
                    headlineContent = {
                        Text("${bill.name} · ${CycleBillEntity.freqName(bill.frequency)} ¥${Money.format(bill.amount)}")
                    },
                    supportingContent = {
                        val next = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(bill.nextDate))
                        Text("分类 ${cat?.name ?: "?"} · 下次 $next", fontSize = 12.sp)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.EventRepeat,
                            contentDescription = null,
                            tint = if (bill.isExpense) Expense else Primary,
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = bill.enabled,
                                onCheckedChange = { vm.update(bill.copy(enabled = it)) },
                            )
                            TextButton(onClick = { vm.delete(bill) }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  新建周期账")
        }
    }

    if (showAdd) {
        AddCycleBillDialog(
            categories = state.second,
            onConfirm = { vm.add(it); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun AddCycleBillDialog(
    categories: List<com.jizhang.assistant.data.entity.CategoryEntity>,
    onConfirm: (CycleBillEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf(CycleBillEntity.FREQ_MONTHLY) }
    var dayOfMonth by remember { mutableStateOf("1") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull { it.isExpense }) }

    val freqs = listOf(
        CycleBillEntity.FREQ_DAILY to "每天",
        CycleBillEntity.FREQ_WEEKLY to "每周",
        CycleBillEntity.FREQ_MONTHLY to "每月",
        CycleBillEntity.FREQ_YEARLY to "每年",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建周期账") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("名称，如 房租") }, singleLine = true,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("金额") }, singleLine = true,
                )
                Row {
                    freqs.forEach { (f, label) ->
                        FilterChipLike(selected = freq == f, label = label) { freq = f }
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp))
                    }
                }
                if (freq == CycleBillEntity.FREQ_MONTHLY || freq == CycleBillEntity.FREQ_YEARLY) {
                    OutlinedTextField(
                        value = dayOfMonth,
                        onValueChange = { dayOfMonth = it.filter { c -> c.isDigit() }.take(2) },
                        placeholder = { Text(if (freq == CycleBillEntity.FREQ_MONTHLY) "每月几号（1-28）" else "几号（1-28）") },
                        singleLine = true,
                    )
                }
                Text("选择分类：", fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(modifier = Modifier.fillMaxSize(0.35f)) {
                    items(categories, key = { it.categoryId }) { cat ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedCat?.categoryId == cat.categoryId,
                                onClick = { selectedCat = cat },
                            )
                            Text("${cat.name}${if (cat.isIncome) "（收入）" else ""}", fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cat = selectedCat ?: return@Button
                val amt = amount.toDoubleOrNull() ?: return@Button
                if (name.isBlank() || amt <= 0) return@Button
                // 计算下次执行日
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                when (freq) {
                    CycleBillEntity.FREQ_MONTHLY -> {
                        cal.set(Calendar.DAY_OF_MONTH, (dayOfMonth.toIntOrNull() ?: 1).coerceIn(1, 28))
                        if (cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.MONTH, 1)
                    }
                    CycleBillEntity.FREQ_YEARLY -> {
                        cal.set(Calendar.DAY_OF_MONTH, (dayOfMonth.toIntOrNull() ?: 1).coerceIn(1, 28))
                        if (cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.YEAR, 1)
                    }
                    else -> cal.add(Calendar.DAY_OF_MONTH, 1)
                }
                onConfirm(
                    CycleBillEntity(
                        name = name.trim(),
                        categoryId = cat.categoryId,
                        isExpense = cat.isExpense,
                        amount = amt,
                        frequency = freq,
                        dayOfMonth = (dayOfMonth.toIntOrNull() ?: 1).coerceIn(1, 28),
                        nextDate = cal.timeInMillis,
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
