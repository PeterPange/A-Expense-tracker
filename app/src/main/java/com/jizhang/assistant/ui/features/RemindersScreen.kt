package com.jizhang.assistant.ui.features

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.assistant.LocalRepository
import com.jizhang.assistant.data.entity.ReminderEntity
import com.jizhang.assistant.reminder.ReminderReceiver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class RemindersViewModel(private val repo: com.jizhang.assistant.data.repository.LedgerRepository) : ViewModel() {
    val reminders: StateFlow<List<ReminderEntity>> = repo.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(reminder: ReminderEntity) { viewModelScope.launch { repo.addReminder(reminder) } }
    fun update(reminder: ReminderEntity) { viewModelScope.launch { repo.updateReminder(reminder) } }
    fun delete(id: Long) { viewModelScope.launch { repo.deleteReminder(id) } }
}

@Composable
fun RemindersScreen() {
    val repo = LocalRepository.current
    val vm: RemindersViewModel = viewModel(
        key = "reminders",
        factory = viewModelFactory { initializer { RemindersViewModel(repo) } },
    )
    val reminders by vm.reminders.collectAsState()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "记账提醒",
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(reminders, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text("%02d:%02d".format(r.hour, r.minute)) },
                    supportingContent = { Text(r.text, fontSize = 12.sp) },
                    leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = r.enabled,
                                onCheckedChange = { enabled ->
                                    vm.update(r.copy(enabled = enabled))
                                    if (enabled) scheduleReminder(context, r)
                                    else cancelReminder(context, r)
                                },
                            )
                            TextButton(onClick = { vm.delete(r.id); cancelReminder(context, r) }) {
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
            Text("  新建提醒")
        }
    }

    if (showAdd) {
        AddReminderDialog(
            onConfirm = { reminder ->
                vm.add(reminder)
                scheduleReminder(context, reminder)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

/** 用 AlarmManager 注册每日提醒 */
fun scheduleReminder(context: Context, reminder: ReminderEntity) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("text", reminder.text)
        putExtra("reminderId", reminder.id.toInt())
    }
    val pi = PendingIntent.getBroadcast(
        context, reminder.id.toInt(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, reminder.hour)
        set(Calendar.MINUTE, reminder.minute)
        set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
    }
    am.setInexactRepeating(
        AlarmManager.RTC_WAKEUP, cal.timeInMillis,
        AlarmManager.INTERVAL_DAY, pi,
    )
}

fun cancelReminder(context: Context, reminder: ReminderEntity) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java)
    val pi = PendingIntent.getBroadcast(
        context, reminder.id.toInt(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    am.cancel(pi)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    onConfirm: (ReminderEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = 21, initialMinute = 0, is24Hour = true,
    )
    var text by remember { mutableStateOf("该记账啦～") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建提醒") },
        text = {
            Column {
                TimePicker(state = timeState)
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("提醒内容") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    ReminderEntity(
                        hour = timeState.hour,
                        minute = timeState.minute,
                        text = text.ifBlank { "该记账啦～" },
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
