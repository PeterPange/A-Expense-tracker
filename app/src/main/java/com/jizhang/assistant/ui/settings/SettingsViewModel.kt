package com.jizhang.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.assistant.data.repository.LedgerRepository
import com.jizhang.assistant.util.DataStore
import com.jizhang.assistant.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val budget: Double? = null,
    val hasLock: Boolean = false,
)

class SettingsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val currentMonth = TimeUtils.monthKey(
        TimeUtils.yearOf(System.currentTimeMillis()),
        TimeUtils.monthOf(System.currentTimeMillis()),
    )

    private val hasLock = MutableStateFlow(DataStore.lockPin() != null)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeBudgets(currentMonth), hasLock,
    ) { budgets, lock ->
        SettingsUiState(
            budget = budgets.firstOrNull { it.categoryId == LedgerRepository.TOTAL_BUDGET_CATEGORY }?.amount,
            hasLock = lock,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setBudget(amount: Double) {
        viewModelScope.launch {
            repository.setBudget(currentMonth, LedgerRepository.TOTAL_BUDGET_CATEGORY, amount)
        }
    }

    fun removeBudget() {
        viewModelScope.launch {
            repository.removeBudget(currentMonth, LedgerRepository.TOTAL_BUDGET_CATEGORY)
        }
    }
}
