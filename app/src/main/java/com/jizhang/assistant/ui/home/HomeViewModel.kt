package com.jizhang.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.assistant.data.dao.RecordDao
import com.jizhang.assistant.data.entity.BookEntity
import com.jizhang.assistant.data.entity.BudgetEntity
import com.jizhang.assistant.data.entity.CategoryEntity
import com.jizhang.assistant.data.entity.RecordEntity
import com.jizhang.assistant.data.repository.LedgerRepository
import com.jizhang.assistant.util.DataStore
import com.jizhang.assistant.util.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** 按日分组的流水条目 */
data class DayGroup(
    val dayLabel: String,
    val weekday: String,
    val dayExpense: Double,
    val dayIncome: Double,
    val items: List<RecordWithCategory>,
)

data class RecordWithCategory(
    val record: RecordEntity,
    val category: CategoryEntity?,
)

data class HomeUiState(
    val year: Int,
    val month: Int,
    val bookName: String = BookEntity.DEFAULT_NAME,
    val monthExpense: Double = 0.0,
    val monthIncome: Double = 0.0,
    val groups: List<DayGroup> = emptyList(),
    val availableMonths: List<Pair<Int, Int>> = emptyList(),
    val budget: Double? = null,
    val budgetUsed: Double = 0.0,
    val streakDays: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: LedgerRepository,
    private val initialYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    private val initialMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(initialYear to initialMonth)
    private val streak = MutableStateFlow(0)

    /** 分类缓存 */
    private val categoryCache = mutableMapOf<String, CategoryEntity>()

    private val categoriesFlow = combine(
        repository.observeExpenseCategories(),
        repository.observeIncomeCategories(),
    ) { e, i -> e + i }

    val uiState: StateFlow<HomeUiState> = combine(
        selectedMonth, DataStore.currentBid, streak,
    ) { m, bid, s -> Triple(m, bid, s) }
        .flatMapLatest { (m, bid, s) ->
            val (year, month) = m
            val bookNameFlow = kotlinx.coroutines.flow.flow {
                emit(repository.book(bid)?.name ?: BookEntity.DEFAULT_NAME)
            }
            combine(
                repository.observeMonth(year, month, bid),
                repository.observeMonthTotal(year, month, bid),
                repository.observeMonths(),
                repository.observeBudgets(TimeUtils.monthKey(year, month)),
                bookNameFlow,
            ) { records, total, months, budgets, bookName ->
                buildState(year, month, bookName, records, total, months, budgets, s)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState(initialYear, initialMonth))

    init {
        viewModelScope.launch {
            categoriesFlow.collect { list ->
                categoryCache.clear()
                list.forEach { categoryCache[it.categoryId] = it }
            }
        }
        viewModelScope.launch {
            streak.value = repository.streakDays()
        }
    }

    private fun buildState(
        year: Int,
        month: Int,
        bookName: String,
        records: List<RecordEntity>,
        total: RecordDao.MonthTotal?,
        months: List<RecordDao.YearMonth>,
        budgets: List<BudgetEntity>,
        streakDays: Int,
    ): HomeUiState {
        val items = records.map { RecordWithCategory(it, categoryCache[it.categoryId]) }
        val groups = items.groupBy { it.record.day }.map { (day, dayItems) ->
            val sample = dayItems.first().record
            DayGroup(
                dayLabel = TimeUtils.dayLabel(sample.date),
                weekday = TimeUtils.weekdayName(sample.date),
                dayExpense = dayItems.filter { it.category?.isExpense ?: true }
                    .sumOf { it.record.amount },
                dayIncome = dayItems.filter { it.category?.isIncome ?: false }
                    .sumOf { it.record.amount },
                items = dayItems,
            )
        }.sortedByDescending { g -> g.items.first().record.date }

        val totalBudget = budgets.firstOrNull { it.categoryId == LedgerRepository.TOTAL_BUDGET_CATEGORY }
        return HomeUiState(
            year = year,
            month = month,
            bookName = bookName,
            monthExpense = total?.expense ?: 0.0,
            monthIncome = total?.income ?: 0.0,
            groups = groups,
            availableMonths = if (months.isEmpty()) listOf(year to month) else months.map { it.year to it.month },
            budget = totalBudget?.amount,
            budgetUsed = total?.expense ?: 0.0,
            streakDays = streakDays,
        )
    }

    fun selectMonth(year: Int, month: Int) {
        selectedMonth.value = year to month
        viewModelScope.launch { streak.value = repository.streakDays() }
    }

    fun deleteRecord(record: RecordEntity) {
        viewModelScope.launch { repository.delete(record) }
    }
}
