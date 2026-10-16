package com.jizhang.assistant.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jizhang.assistant.data.entity.AssetEntity
import com.jizhang.assistant.data.entity.CategoryEntity
import com.jizhang.assistant.data.repository.LedgerRepository
import com.jizhang.assistant.util.AmountCalculator
import com.jizhang.assistant.util.DataStore
import com.jizhang.assistant.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class AddUiState(
    val isExpense: Boolean = true,
    val expression: String = "",
    val preview: String = "",
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val remark: String = "",
    val member: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val dateLabel: String = "今天",
    val isEdit: Boolean = false,
    val canSave: Boolean = false,
    val assets: List<AssetEntity> = emptyList(),
    val selectedAssetId: Long = 0,
)

class AddRecordViewModel(
    private val repository: LedgerRepository,
    private val recordId: Long = -1L,
) : ViewModel() {

    private val calculator = AmountCalculator()
    private val isExpenseFlow = MutableStateFlow(true)
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val remark = MutableStateFlow("")
    private val member = MutableStateFlow("")
    private val dateMillis = MutableStateFlow(System.currentTimeMillis())
    private val isEdit = MutableStateFlow(false)
    private val selectedAssetId = MutableStateFlow(0L)
    private val refresh = MutableStateFlow(0)

    private val allCategories = combine(
        repository.observeExpenseCategories(),
        repository.observeIncomeCategories(),
    ) { e, i -> e to i }

    private val assets = repository.observeAssets()

    val uiState: StateFlow<AddUiState> = combine(
        isExpenseFlow, selectedCategory, remark, dateMillis, isEdit,
        allCategories, refresh, member, assets, selectedAssetId,
    ) { arr ->
        val (expenseCats, incomeCats) = arr[5] as Pair<List<CategoryEntity>, List<CategoryEntity>>
        val isExpense = arr[0] as Boolean
        AddUiState(
            isExpense = isExpense,
            expression = calculator.expression,
            preview = calculator.preview(),
            categories = if (isExpense) expenseCats else incomeCats,
            selectedCategoryId = arr[1] as String?,
            remark = arr[2] as String,
            member = arr[7] as String,
            dateMillis = arr[3] as Long,
            dateLabel = dateLabelOf(arr[3] as Long),
            isEdit = arr[4] as Boolean,
            canSave = calculator.result() != null && (arr[1] as String?) != null,
            assets = arr[8] as List<AssetEntity>,
            selectedAssetId = arr[9] as Long,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddUiState())

    init {
        if (recordId > 0) {
            viewModelScope.launch {
                val record = repository.record(recordId) ?: return@launch
                val category = repository.category(record.categoryId)
                isExpenseFlow.value = category?.isExpense ?: true
                selectedCategory.value = record.categoryId
                remark.value = record.remark
                member.value = record.member
                dateMillis.value = record.date
                selectedAssetId.value = record.assetId
                isEdit.value = true
                calculator.append(amountToTokens(record.amount))
                refresh.value++
            }
        } else {
            viewModelScope.launch {
                val cats = repository.observeExpenseCategories().firstOrNull().orEmpty()
                if (selectedCategory.value == null && cats.isNotEmpty()) {
                    selectedCategory.value = cats.first().categoryId
                }
            }
        }
    }

    fun switchExpenseIncome(isExpense: Boolean) {
        if (isExpenseFlow.value == isExpense) return
        isExpenseFlow.value = isExpense
        selectedCategory.value = null
        viewModelScope.launch {
            val flow = if (isExpense) repository.observeExpenseCategories()
            else repository.observeIncomeCategories()
            val cats = flow.firstOrNull().orEmpty()
            if (cats.isNotEmpty()) selectedCategory.value = cats.first().categoryId
            refresh.value++
        }
    }

    fun selectCategory(categoryId: String) { selectedCategory.value = categoryId }
    fun inputRemark(text: String) { remark.value = text }
    fun inputMember(text: String) { member.value = text }
    fun inputDate(millis: Long) { dateMillis.value = millis }
    fun selectAsset(assetId: Long) { selectedAssetId.value = assetId }

    fun press(token: String) { calculator.append(token); refresh.value++ }
    fun backspace() { calculator.backspace(); refresh.value++ }
    fun clear() { calculator.clear(); refresh.value++ }

    /** 保存。金额非法或未选分类时不动作 */
    fun save(onDone: () -> Unit) {
        val amount = calculator.result() ?: return
        val categoryId = selectedCategory.value ?: return
        viewModelScope.launch {
            if (isEdit.value && recordId > 0) {
                val existing = repository.record(recordId) ?: return@launch
                val cal = TimeUtils.calendarOf(dateMillis.value)
                repository.update(
                    existing.copy(
                        amount = amount,
                        categoryId = categoryId,
                        remark = remark.value,
                        member = member.value,
                        assetId = selectedAssetId.value,
                        date = dateMillis.value,
                        year = cal.get(Calendar.YEAR),
                        month = cal.get(Calendar.MONTH) + 1,
                        day = cal.get(Calendar.DAY_OF_MONTH),
                    )
                )
            } else {
                repository.add(
                    amount = amount,
                    categoryId = categoryId,
                    remark = remark.value,
                    dateMillis = dateMillis.value,
                    bid = DataStore.currentBid.value,
                    member = member.value,
                    assetId = selectedAssetId.value,
                )
            }
            onDone()
        }
    }

    private fun dateLabelOf(millis: Long): String =
        if (TimeUtils.isToday(millis)) "今天" else TimeUtils.dayLabel(millis)

    private fun amountToTokens(amount: Double): String {
        val rounded = kotlin.math.round(amount * 100.0) / 100.0
        return if (rounded.toLong().toDouble() == rounded) rounded.toLong().toString() else rounded.toString()
    }

    class Factory(
        private val repository: LedgerRepository,
        private val recordId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddRecordViewModel(repository, recordId) as T
    }
}
