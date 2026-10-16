package com.jizhang.assistant.ui.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.jizhang.assistant.data.entity.CategoryEntity
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary
import com.jizhang.assistant.util.CategoryIcons
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryManageViewModel(
    private val repo: com.jizhang.assistant.data.repository.LedgerRepository,
) : ViewModel() {
    val categories: StateFlow<Pair<List<CategoryEntity>, List<CategoryEntity>>> =
        combine(repo.observeExpenseCategories(), repo.observeIncomeCategories()) { e, i -> e to i }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CategoryEntity>() to emptyList<CategoryEntity>())

    fun add(name: String, isExpense: Boolean) {
        viewModelScope.launch { repo.addCategory(name, "custom", isExpense) }
    }

    fun rename(categoryId: String, name: String) {
        viewModelScope.launch { repo.renameCategory(categoryId, name) }
    }

    fun delete(categoryId: String) {
        viewModelScope.launch { repo.deleteCategory(categoryId) }
    }
}

@Composable
fun CategoryManageScreen() {
    val repo = LocalRepository.current
    val vm: CategoryManageViewModel = viewModel(
        key = "catmgr",
        factory = viewModelFactory { initializer { CategoryManageViewModel(repo) } },
    )
    val cats by vm.categories.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CategoryEntity?>(null) }

    val list = if (tab == 0) cats.first else cats.second

    Column(Modifier.fillMaxSize()) {
        Text(
            "分类管理",
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("支出 ${cats.first.size}") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("收入 ${cats.second.size}") })
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.categoryId }) { cat ->
                val color = if (cat.isIncome) Primary else Expense
                ListItem(
                    headlineContent = { Text(cat.name) },
                    leadingContent = {
                        Icon(
                            CategoryIcons.iconOf(cat.categoryId, cat.type),
                            contentDescription = cat.name,
                            tint = color,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (cat.isCustom) "自定义" else "系统",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { renameTarget = cat }) { Text("改名") }
                            if (cat.isCustom) {
                                TextButton(onClick = { vm.delete(cat.categoryId) }) {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
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
            Text("  新建${if (tab == 0) "支出" else "收入"}分类")
        }
    }

    if (showAdd) {
        NameDialog(
            title = "新建${if (tab == 0) "支出" else "收入"}分类",
            onConfirm = { vm.add(it, tab == 0); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "重命名分类",
            initial = target.name,
            onConfirm = { vm.rename(target.categoryId, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}
