package com.jizhang.assistant.ui.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.jizhang.assistant.data.entity.TemplateEntity
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary
import com.jizhang.assistant.util.CategoryIcons
import com.jizhang.assistant.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(private val repo: com.jizhang.assistant.data.repository.LedgerRepository) : ViewModel() {

    val state: StateFlow<Pair<List<TemplateEntity>, List<com.jizhang.assistant.data.entity.CategoryEntity>>> =
        combine(
            repo.observeTemplates(),
            combine(repo.observeExpenseCategories(), repo.observeIncomeCategories()) { e, i -> e + i },
        ) { t, c -> t to c }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<TemplateEntity>() to emptyList())

    fun add(template: TemplateEntity) { viewModelScope.launch { repo.addTemplate(template) } }
    fun delete(template: TemplateEntity) { viewModelScope.launch { repo.deleteTemplate(template) } }

    /** 用模板立即记一笔（今天） */
    fun useNow(template: TemplateEntity) {
        viewModelScope.launch {
            repo.add(
                amount = template.amount,
                categoryId = template.categoryId,
                remark = template.remark,
                dateMillis = System.currentTimeMillis(),
            )
        }
    }
}

@Composable
fun TemplatesScreen(onUse: () -> Unit) {
    val repo = LocalRepository.current
    val vm: TemplatesViewModel = viewModel(
        key = "templates",
        factory = viewModelFactory { initializer { TemplatesViewModel(repo) } },
    )
    val state by vm.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "模板记账",
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (state.first.isEmpty()) {
            Text(
                "还没有模板。把常用的账（如每天午饭）建成模板，一键复记。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.first, key = { it.id }) { tpl ->
                val cat = state.second.firstOrNull { it.categoryId == tpl.categoryId }
                ListItem(
                    headlineContent = { Text(tpl.name) },
                    supportingContent = {
                        Text(
                            "${cat?.name ?: "?"} · ${if (tpl.amount > 0) "¥${Money.format(tpl.amount)}" else "金额手动输入"}",
                            fontSize = 12.sp,
                        )
                    },
                    leadingContent = {
                        Icon(
                            CategoryIcons.iconOf(tpl.categoryId, cat?.type ?: -1),
                            contentDescription = null,
                            tint = if (tpl.isExpense) Expense else Primary,
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = {
                                if (tpl.amount > 0) {
                                    vm.useNow(tpl)
                                } else {
                                    onUse()
                                }
                            }) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.padding(0.dp))
                                Text(" 记一笔")
                            }
                            TextButton(onClick = { vm.delete(tpl) }) {
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
            Text("  新建模板")
        }
    }

    if (showAdd) {
        AddTemplateDialog(
            categories = state.second,
            onConfirm = { tpl ->
                vm.add(tpl)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun AddTemplateDialog(
    categories: List<com.jizhang.assistant.data.entity.CategoryEntity>,
    onConfirm: (TemplateEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull { it.isExpense }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建模板") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("模板名，如 午饭") }, singleLine = true,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("金额（留空则每次输入）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = remark, onValueChange = { remark = it },
                    placeholder = { Text("备注（可选）") }, singleLine = true,
                )
                Text("选择分类：", fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(modifier = Modifier.fillMaxSize(0.4f)) {
                    items(categories, key = { it.categoryId }) { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
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
                if (name.isNotBlank()) {
                    onConfirm(
                        TemplateEntity(
                            name = name.trim(),
                            categoryId = cat.categoryId,
                            isExpense = cat.isExpense,
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            remark = remark.trim(),
                        )
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
