package com.jizhang.assistant.ui.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.jizhang.assistant.data.entity.BookEntity
import com.jizhang.assistant.util.DataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BooksViewModel(private val repo: com.jizhang.assistant.data.repository.LedgerRepository) : ViewModel() {
    val books: StateFlow<List<BookEntity>> = repo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String) { viewModelScope.launch { repo.addBook(name) } }
    fun rename(bid: String, name: String) { viewModelScope.launch { repo.renameBook(bid, name) } }
    fun delete(bid: String) { viewModelScope.launch { repo.deleteBook(bid) } }
}

@Composable
fun BooksScreen() {
    val repo = LocalRepository.current
    val vm: BooksViewModel = viewModel(
        key = "books",
        factory = viewModelFactory { initializer { BooksViewModel(repo) } },
    )
    val books by vm.books.collectAsState()
    val currentBid by DataStore.currentBid.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<BookEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "多账本",
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(books, key = { it.bid }) { book ->
                val selected = book.bid == currentBid
                ListItem(
                    headlineContent = { Text(book.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                    supportingContent = {
                        if (selected) Text("当前账本", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    },
                    leadingContent = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = { DataStore.setCurrentBid(book.bid) })
                            TextButton(onClick = { renameTarget = book }) { Text("改名") }
                            if (book.bid != BookEntity.DEFAULT_BID) {
                                TextButton(onClick = {
                                    if (!selected) vm.delete(book.bid)
                                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
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
            Spacer(Modifier.width(4.dp))
            Text("新建账本")
        }
    }

    if (showAdd) {
        NameDialog(
            title = "新建账本",
            onConfirm = { vm.add(it); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "重命名账本",
            initial = target.name,
            onConfirm = { vm.rename(target.bid, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    placeholder: String = "输入名称",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
