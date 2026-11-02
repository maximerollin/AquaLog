package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence

interface TaskReminderScheduler {
    fun schedule(occurrence: TaskOccurrence)

    fun cancel(occurrenceId: String)

    fun reconcile(occurrences: List<TaskOccurrence>)

    /** One reminder for due tests at [nextDueTestReminder] of [at]; null cancels it. */
    fun scheduleDueTests(at: TaskDate?)

    /** The Sunday summary at [nextSundaySummary]; scheduling again replaces it. */
    fun scheduleWeeklySummary()

    fun isBatteryOptimizationActive(): Boolean
}
