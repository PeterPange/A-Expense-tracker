package com.jizhang.assistant.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.assistant.LocalRepository
import com.jizhang.assistant.data.entity.RecordEntity
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary
import com.jizhang.assistant.util.CategoryIcons
import com.jizhang.assistant.util.Money
import com.jizhang.assistant.util.TimeUtils
import java.util.Calendar

@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val repo = LocalRepository.current
    val vm: HomeViewModel = viewModel(
        key = "home",
        factory = viewModelFactory { initializer { HomeViewModel(repo) } },
    )
    val state by vm.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<RecordEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MonthSummaryCard(state, onSelectMonth = { y, m -> vm.selectMonth(y, m) })
        Spacer(Modifier.height(8.dp))
        if (state.groups.isEmpty()) {
            EmptyHint()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header_${group.dayLabel}") { DayHeader(group) }
                    items(group.items, key = { it.record.id }) { item ->
                        RecordRow(
                            item = item,
                            onClick = { onEdit(item.record.id) },
                            onLongClick = { pendingDelete = item.record },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔记录？") },
            text = { Text("金额 ${Money.format(target.amount)}，删除后不可恢复") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecord(target)
                    pendingDelete = null
                }) { Text("删除", color = Expense) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MonthSummaryCard(state: HomeUiState, onSelectMonth: (Int, Int) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = {
                    shiftMonth(state.year, state.month, -1, state.availableMonths)
                        ?.let { (y, m) -> onSelectMonth(y, m) }
                }) { Icon(Icons.Filled.ChevronLeft, "上个月") }
                Text(
                    "${state.year}年${state.month}月",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = {
                    shiftMonth(state.year, state.month, +1, state.availableMonths)
                        ?.let { (y, m) -> onSelectMonth(y, m) }
                }) { Icon(Icons.Filled.ChevronRight, "下个月") }
            }
            // 账本名 + 连续打卡
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "📖 ${state.bookName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.streakDays > 0) {
                    Text(
                        "🔥 连续记账 ${state.streakDays} 天",
                        fontSize = 12.sp,
                        color = Expense,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryItem("本月支出", Money.format(state.monthExpense), Expense)
                SummaryItem("本月收入", Money.format(state.monthIncome), Primary)
                SummaryItem(
                    "结余",
                    Money.formatSigned(state.monthIncome - state.monthExpense),
                    MaterialTheme.colorScheme.onSurface,
                )
            }
            state.budget?.let { budget ->
                Spacer(Modifier.height(10.dp))
                BudgetBar(used = state.budgetUsed, budget = budget)
            }
        }
    }
}

@Composable
private fun BudgetBar(used: Double, budget: Double) {
    val ratio = if (budget > 0) (used / budget).coerceIn(0.0, 1.0) else 0.0
    val over = used > budget
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("月预算", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (over) "已超支 ${Money.format(used - budget)}"
                else "剩余 ${Money.format(budget - used)}",
                fontSize = 12.sp,
                color = if (over) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.toFloat())
                    .height(6.dp)
                    .background(if (over) Expense else Primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

private fun shiftMonth(
    year: Int,
    month: Int,
    delta: Int,
    available: List<Pair<Int, Int>>,
): Pair<Int, Int>? {
    val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
    cal.add(Calendar.MONTH, delta)
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    return if (available.any { it.first == y && it.second == m }) y to m else null
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${group.dayLabel} ${group.weekday}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            buildString {
                if (group.dayExpense > 0) append("支 ${Money.format(group.dayExpense)}")
                if (group.dayIncome > 0) {
                    if (isNotEmpty()) append("  ")
                    append("收 ${Money.format(group.dayIncome)}")
                }
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(
    item: RecordWithCategory,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val record = item.record
    val category = item.category
    val isIncome = category?.isIncome == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                CategoryIcons.iconOf(record.categoryId, category?.type ?: -1),
                contentDescription = category?.name,
                tint = if (isIncome) Primary else Expense,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                category?.name ?: "未分类",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (record.remark.isNotBlank()) {
                Text(
                    record.remark,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (isIncome) "+" else "-") + Money.format(record.amount),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isIncome) Primary else Expense,
            )
            Text(
                TimeUtils.timeLabel(record.date),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("本月还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text("点击右下角按钮记一笔", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}
