package com.jizhang.assistant.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jizhang.assistant.MainActivity
import com.jizhang.assistant.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "该记账啦～"
        val id = intent.getIntExtra("reminderId", 1000)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jizhang_reminder", "记账提醒", NotificationManager.IMPORTANCE_DEFAULT,
            )
            nm.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, "jizhang_reminder")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("记账助手")
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(id, notification)
    }
}
