package com.jizhang.assistant.ui.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jizhang.assistant.data.entity.RecordEntity
import com.jizhang.assistant.ui.home.RecordWithCategory
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary
import com.jizhang.assistant.util.CategoryIcons
import com.jizhang.assistant.util.Money
import com.jizhang.assistant.util.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repo: com.jizhang.assistant.data.repository.LedgerRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    private val categories = combine(
        repo.observeExpenseCategories(),
        repo.observeIncomeCategories(),
    ) { e, i -> e + i }

    val results: StateFlow<List<RecordWithCategory>> =
        combine(query, categories) { q, c -> q to c }
            .flatMapLatest { (q, cats) ->
                if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
                else repo.search(q).combine(kotlinx.coroutines.flow.flowOf(cats)) { r, _ ->
                    r.map { RecordWithCategory(it, cats.firstOrNull { c2 -> c2.categoryId == it.categoryId }) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { query.value = q }
}

@Composable
fun SearchScreen() {
    val repo = LocalRepository.current
    val vm: SearchViewModel = viewModel(
        key = "search",
        factory = viewModelFactory { initializer { SearchViewModel(repo) } },
    )
    val results by vm.results.collectAsState()
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; vm.setQuery(it) },
            placeholder = { Text("搜索备注 / 分类 / 金额 / 成员") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        )
        if (text.isNotBlank()) {
            Text(
                "共 ${results.size} 条",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { it.record.id }) { item ->
                val record = item.record
                val isIncome = item.category?.isIncome == true
                ListItem(
                    headlineContent = { Text(item.category?.name ?: "未分类") },
                    supportingContent = {
                        Text(
                            "${TimeUtils.dayLabel(record.date)} ${TimeUtils.timeLabel(record.date)}" +
                                if (record.remark.isNotBlank()) " · ${record.remark}" else "",
                            fontSize = 12.sp,
                        )
                    },
                    leadingContent = {
                        androidx.compose.material3.Icon(
                            CategoryIcons.iconOf(record.categoryId, item.category?.type ?: -1),
                            contentDescription = null,
                            tint = if (isIncome) Primary else Expense,
                        )
                    },
                    trailingContent = {
                        Text(
                            (if (isIncome) "+" else "-") + Money.format(record.amount),
                            fontWeight = FontWeight.SemiBold,
                            color = if (isIncome) Primary else Expense,
                        )
                    },
                )
            }
        }
    }
}
