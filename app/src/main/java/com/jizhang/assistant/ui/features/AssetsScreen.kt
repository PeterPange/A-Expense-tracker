package com.jizhang.assistant.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyYuan
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.assistant.LocalRepository
import com.jizhang.assistant.data.entity.AssetEntity
import com.jizhang.assistant.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetsViewModel(private val repo: com.jizhang.assistant.data.repository.LedgerRepository) : ViewModel() {

    val state: StateFlow<Triple<List<AssetEntity>, Double, List<AssetEntity>>> =
        combine(repo.observeAssets(), repo.observeTotalBalance(), repo.observeAssets()) { a, t, _ ->
            Triple(a, t, a)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(emptyList(), 0.0, emptyList()))

    fun add(name: String, type: String, balance: Double) {
        viewModelScope.launch {
            repo.addAsset(AssetEntity(name = name, type = type, balance = balance))
        }
    }

    fun update(asset: AssetEntity) { viewModelScope.launch { repo.updateAsset(asset) } }
    fun delete(asset: AssetEntity) { viewModelScope.launch { repo.deleteAsset(asset) } }
    fun transfer(from: Long, to: Long, amount: Double, fee: Double) {
        viewModelScope.launch { repo.transfer(from, to, amount, fee, "") }
    }
}

@Composable
fun AssetsScreen() {
    val repo = LocalRepository.current
    val vm: AssetsViewModel = viewModel(
        key = "assets",
        factory = viewModelFactory { initializer { AssetsViewModel(repo) } },
    )
    val state by vm.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "资产",
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        // 总资产卡片
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("净资产（含信用卡/负债）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "¥ ${Money.formatSigned(state.second)}",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = if (state.second >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(
                    onClick = { if (state.first.size >= 2) showTransfer = true },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.width(16.dp))
                    Text(" 转账")
                }
            }
        }
        Spacer(Modifier.padding(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(state.first, key = { it.id }) { asset ->
                ListItem(
                    headlineContent = { Text(asset.name) },
                    supportingContent = {
                        Text(
                            AssetEntity.typeName(asset.type) +
                                if (asset.cardTail.isNotBlank()) " · 尾号${asset.cardTail}" else "",
                            fontSize = 12.sp,
                        )
                    },
                    leadingContent = { Icon(assetIcon(asset.type), contentDescription = null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "¥ ${Money.formatSigned(asset.balance)}",
                                fontWeight = FontWeight.SemiBold,
                                color = if (asset.balance >= 0) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { vm.delete(asset) }) {
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
            Text("  添加资产")
        }
    }

    if (showAdd) {
        AddAssetDialog(
            onConfirm = { name, type, balance ->
                vm.add(name, type, balance)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    if (showTransfer) {
        TransferDialog(
            assets = state.first,
            onConfirm = { from, to, amount, fee ->
                vm.transfer(from, to, amount, fee)
                showTransfer = false
            },
            onDismiss = { showTransfer = false },
        )
    }
}

private fun assetIcon(type: String) = when (type) {
    AssetEntity.TYPE_CASH -> Icons.Filled.CurrencyYuan
    AssetEntity.TYPE_BANK -> Icons.Filled.AccountBalance
    AssetEntity.TYPE_CREDIT -> Icons.Filled.CreditCard
    else -> Icons.Filled.AccountBalanceWallet
}

@Composable
private fun AddAssetDialog(
    onConfirm: (String, String, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AssetEntity.TYPE_BANK) }
    var balance by remember { mutableStateOf("") }
    val types = listOf(
        AssetEntity.TYPE_CASH to "现金",
        AssetEntity.TYPE_BANK to "储蓄卡",
        AssetEntity.TYPE_CREDIT to "信用卡",
        AssetEntity.TYPE_ALIPAY to "支付宝",
        AssetEntity.TYPE_WECHAT to "微信",
        AssetEntity.TYPE_DEBT to "负债",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加资产账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("名称，如 招商银行") }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.take(3).forEach { (t, label) ->
                        FilterChipLike(selected = type == t, label = label) { type = t }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.drop(3).forEach { (t, label) ->
                        FilterChipLike(selected = type == t, label = label) { type = t }
                    }
                }
                OutlinedTextField(
                    value = balance, onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    placeholder = { Text("当前余额（负债填负数）") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val b = balance.toDoubleOrNull() ?: 0.0
                if (name.isNotBlank()) onConfirm(name.trim(), type, b)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun FilterChipLike(selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TransferDialog(
    assets: List<AssetEntity>,
    onConfirm: (Long, Long, Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var from by remember { mutableStateOf(assets.firstOrNull()?.id ?: 0L) }
    var to by remember { mutableStateOf(assets.getOrNull(1)?.id ?: 0L) }
    var amount by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("资产转账") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("转出账户", fontSize = 12.sp)
                assets.forEach { a ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = from == a.id,
                            onClick = { if (a.id != to) from = a.id },
                        )
                        Text(a.name, fontSize = 14.sp)
                    }
                }
                Text("转入账户", fontSize = 12.sp)
                assets.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = to == a.id,
                            onClick = { if (a.id != from) to = a.id },
                        )
                        Text(a.name, fontSize = 14.sp)
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("转账金额") }, singleLine = true,
                )
                OutlinedTextField(
                    value = fee, onValueChange = { fee = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("手续费（可选）") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val a = amount.toDoubleOrNull() ?: 0.0
                if (a > 0 && from != to) onConfirm(from, to, a, fee.toDoubleOrNull() ?: 0.0)
            }) { Text("转账") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
