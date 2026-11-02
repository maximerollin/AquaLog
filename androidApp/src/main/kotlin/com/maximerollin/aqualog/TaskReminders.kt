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
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

    override fun scheduleDueTests(at: TaskDate?) {
        val intent = PendingIntent.getBroadcast(
            context,
            DUE_TESTS_REQUEST_CODE,
            Intent(context, DueTestReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = at?.let { nextDueTestReminder(listOf(it), ZoneId.systemDefault(), nowMillis()) }
        if (triggerAt == null) {
            alarmManager.cancel(intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
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
        private const val DUE_TESTS_REQUEST_CODE = 0x7e57
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (cannotNotify(context)) return
        val title = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_TASK_TITLE).orEmpty()
        val occurrenceId = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_OCCURRENCE_ID) ?: return
        postReminder(context, occurrenceId.hashCode(), title, context.getString(R.string.task_notification_text))
    }
}

private fun cannotNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun localToday(): TaskDate = LocalDate.now().let { TaskDate(it.year, it.monthValue, it.dayOfMonth) }

/** Posts on the « task-reminders » channel; tapping opens the app. */
private fun postReminder(context: Context, notificationId: Int, title: String, text: String?) {
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
        notificationId,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        android.app.Notification.Builder(context, CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        android.app.Notification.Builder(context)
    }.setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()
    notifications.notify(notificationId, notification)
}

private const val CHANNEL_ID = "task-reminders"
private const val DUE_TESTS_NOTIFICATION_ID = 0x7e57

/** Fires at 09:00 while tests are due: lists them across Aquariums, then schedules the next reminder. */
class DueTestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (cannotNotify(context)) return
        val application = context.applicationContext as? AquaLogApplication ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val summary = application.aquariumRepository.dueTestSummary(localToday(), ZoneId.systemDefault().id)
                if (summary.dueToday.isNotEmpty()) {
                    val text = context.getString(R.string.due_tests_notification_text, dueTestsLabels(summary.dueToday))
                    postReminder(context, DUE_TESTS_NOTIFICATION_ID, text, null)
                }
                application.taskReminderScheduler.scheduleDueTests(summary.nextDueDate)
            } finally {
                pending.finish()
            }
        }
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
                application.taskReminderScheduler.scheduleDueTests(
                    application.aquariumRepository.dueTestSummary(localToday(), ZoneId.systemDefault().id).nextDueDate,
                )
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
