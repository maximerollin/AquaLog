package com.maximerollin.aqualog

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.maximerollin.aqualog.shared.TaskOccurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

interface TaskReminderScheduler {
    fun schedule(occurrence: TaskOccurrence)

    fun cancel(occurrenceId: String)

    fun reconcile(occurrences: List<TaskOccurrence>)

    fun isBatteryOptimizationActive(): Boolean
}

class AndroidTaskReminderScheduler(
    private val context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : TaskReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(occurrence: TaskOccurrence) {
        if (occurrence.resolution != null) return
        val alarmAt = occurrence.alarmEpochMillis()
        val triggerAt = maxOf(alarmAt, nowMillis() + MINIMUM_DELAY_MILLIS)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(occurrence.id, occurrence.title),
        )
    }

    override fun cancel(occurrenceId: String) {
        alarmManager.cancel(pendingIntent(occurrenceId, ""))
    }

    override fun reconcile(occurrences: List<TaskOccurrence>) {
        occurrences.forEach(::schedule)
    }

    override fun isBatteryOptimizationActive(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun pendingIntent(occurrenceId: String, title: String): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java)
            .setAction("${context.packageName}.TASK_REMINDER.$occurrenceId")
            .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            .putExtra(EXTRA_TASK_TITLE, title)
        return PendingIntent.getBroadcast(
            context,
            occurrenceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun TaskOccurrence.alarmEpochMillis(): Long {
        val date = LocalDate.of(dueDate.year, dueDate.month, dueDate.day)
        val time = minuteOfDay?.let { LocalTime.of(it / 60, it % 60) } ?: LocalTime.of(9, 0)
        return date.atTime(time).atZone(ZoneId.of(timeZoneId)).toInstant().toEpochMilli()
    }

    companion object {
        internal const val EXTRA_OCCURRENCE_ID = "occurrence-id"
        internal const val EXTRA_TASK_TITLE = "task-title"
        private const val MINIMUM_DELAY_MILLIS = 1_000L
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val title = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_TASK_TITLE).orEmpty()
        val occurrenceId = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_OCCURRENCE_ID) ?: return
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.task_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            occurrenceId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.task_notification_text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        notifications.notify(occurrenceId.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "task-reminders"
    }
}

class TaskRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val application = context.applicationContext as? AquaLogApplication ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val occurrences = application.aquariumRepository.observePendingTaskOccurrences().first()
                application.taskReminderScheduler.reconcile(occurrences)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
