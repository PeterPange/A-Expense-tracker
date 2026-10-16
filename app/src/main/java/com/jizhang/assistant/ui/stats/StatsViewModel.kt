package com.jizhang.assistant.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.assistant.data.dao.RecordDao
import com.jizhang.assistant.data.repository.LedgerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class StatsUiState(
    val year: Int,
    val month: Int,
    val isExpense: Boolean = true,
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val categoryTotals: List<RecordDao.CategoryTotal> = emptyList(),
    val dailyTotals: List<RecordDao.DailyTotal> = emptyList(),
    val memberTotals: List<RecordDao.MemberTotal> = emptyList(),
    val availableMonths: List<Pair<Int, Int>> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    private val selectedMonth =
        MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR) to Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val isExpense = MutableStateFlow(true)

    val uiState: StateFlow<StatsUiState> = combine(selectedMonth, isExpense) { m, e -> m to e }
        .flatMapLatest { (monthPair, isExp) ->
            val (year, month) = monthPair
            combine(
                repository.observeMonthTotal(year, month),
                repository.observeMonthCategoryTotals(year, month),
                repository.observeMonthDailyTotals(year, month),
                repository.observeMonths(),
                repository.observeMonthMemberTotals(year, month),
            ) { total, cats, daily, months, members ->
                StatsUiState(
                    year = year,
                    month = month,
                    isExpense = isExp,
                    totalExpense = total?.expense ?: 0.0,
                    totalIncome = total?.income ?: 0.0,
                    categoryTotals = cats.filter { if (isExp) it.type in (0..1) else it.type in (2..3) },
                    dailyTotals = daily,
                    memberTotals = members,
                    availableMonths = months.map { it.year to it.month },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH) + 1,
        ))

    fun selectMonth(year: Int, month: Int) { selectedMonth.value = year to month }
    fun switchExpenseIncome(isExpense: Boolean) { this.isExpense.value = isExpense }
}
