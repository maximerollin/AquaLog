package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.TaskOccurrence

interface TaskReminderScheduler {
    fun schedule(occurrence: TaskOccurrence)

    fun cancel(occurrenceId: String)

    fun reconcile(occurrences: List<TaskOccurrence>)

    fun isBatteryOptimizationActive(): Boolean
}
