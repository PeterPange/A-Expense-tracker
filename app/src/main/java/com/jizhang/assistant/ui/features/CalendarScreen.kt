package com.jizhang.assistant.ui.features

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.assistant.LocalRepository
import com.jizhang.assistant.data.dao.RecordDao
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary
import com.jizhang.assistant.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class CalendarUiState(
    val year: Int,
    val month: Int,
    val daily: Map<Int, RecordDao.DailyTotal> = emptyMap(),
    val monthExpense: Double = 0.0,
    val monthIncome: Double = 0.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repo: com.jizhang.assistant.data.repository.LedgerRepository) : ViewModel() {

    private val month = MutableStateFlow(
        Calendar.getInstance().get(Calendar.YEAR) to Calendar.getInstance().get(Calendar.MONTH) + 1
    )

    val uiState: StateFlow<CalendarUiState> = month.flatMapLatest { (y, m) ->
        combine(
            repo.observeMonthDailyTotals(y, m),
            repo.observeMonthTotal(y, m),
        ) { daily, total ->
            CalendarUiState(
                year = y, month = m,
                daily = daily.associateBy { it.day },
                monthExpense = total?.expense ?: 0.0,
                monthIncome = total?.income ?: 0.0,
            )
        }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        CalendarUiState(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH) + 1,
        ),
    )

    fun shift(delta: Int) {
        val (y, m) = month.value
        val cal = Calendar.getInstance().apply { set(y, m - 1, 1) }
        cal.add(Calendar.MONTH, delta)
        month.value = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH) + 1
    }
}

@Composable
fun CalendarScreen() {
    val repo = LocalRepository.current
    val vm: CalendarViewModel = viewModel(
        key = "calendar",
        factory = viewModelFactory { initializer { CalendarViewModel(repo) } },
    )
    val state by vm.uiState.collectAsState()

    val cal = Calendar.getInstance().apply { set(state.year, state.month - 1, 1) }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // 该月 1 号是周几（周日=1）
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val leadingBlanks = firstDayOfWeek - 1

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Filled.ChevronLeft, "上个月") }
            Text("${state.year}年${state.month}月", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { vm.shift(1) }) { Icon(Icons.Filled.ChevronRight, "下个月") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("支出 ${Money.format(state.monthExpense)}", color = Expense, fontWeight = FontWeight.SemiBold)
            Text("收入 ${Money.format(state.monthIncome)}", color = Primary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))

        // 星期表头
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(
                    it, fontSize = 12.sp, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // 日历格子
        Column(Modifier.padding(horizontal = 8.dp)) {
            var day = 1 - leadingBlanks
            val rows = (leadingBlanks + daysInMonth + 6) / 7
            repeat(rows) {
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) {
                        val d = day
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .padding(2.dp)
                                .background(
                                    if (d in 1..daysInMonth) MaterialTheme.colorScheme.surface
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if (d in 1..daysInMonth) {
                                val dayData = state.daily[d]
                                val hasData = dayData != null &&
                                    ((dayData.expense ?: 0.0) > 0 || (dayData.income ?: 0.0) > 0)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(top = 6.dp),
                                ) {
                                    Text(
                                        "$d",
                                        fontSize = 14.sp,
                                        fontWeight = if (hasData) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (hasData) {
                                        val exp = dayData.expense ?: 0.0
                                        val inc = dayData.income ?: 0.0
                                        if (exp > 0) {
                                            Text(
                                                "-${Money.format(exp)}",
                                                fontSize = 9.sp, color = Expense,
                                                maxLines = 1,
                                            )
                                        }
                                        if (inc > 0) {
                                            Text(
                                                "+${Money.format(inc)}",
                                                fontSize = 9.sp, color = Primary,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        day++
                    }
                }
            }
        }
    }
}
