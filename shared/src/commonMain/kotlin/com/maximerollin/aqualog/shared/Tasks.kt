package com.maximerollin.aqualog.shared

data class TaskDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12)
        require(day in 1..daysInMonth(year, month))
    }
}

enum class TaskRecurrence(val storageValue: String) {
    ONCE("once"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly");

    companion object {
        fun fromStorageValue(value: String) = entries.first { it.storageValue == value }
    }
}

enum class TaskResolution(val storageValue: String) {
    COMPLETED("completed"), POSTPONED("postponed"), IGNORED("ignored");

    companion object {
        fun fromStorageValue(value: String) = entries.first { it.storageValue == value }
    }
}

data class AquariumTask(
    val id: String,
    val aquariumId: String,
    val title: String,
    val recurrence: TaskRecurrence,
    val monthlyAnchorDay: Int,
)

data class TaskOccurrence(
    val id: String,
    val taskId: String,
    val aquariumId: String,
    val title: String,
    val dueDate: TaskDate,
    val minuteOfDay: Int?,
    val timeZoneId: String,
    val resolution: TaskResolution?,
    val resolvedAtEpochMillis: Long?,
)

data class TaskInput(
    val aquariumId: String,
    val title: String,
    val recurrence: TaskRecurrence,
    val firstDueDate: TaskDate,
    val minuteOfDay: Int?,
    val timeZoneId: String,
)

data class CreatedTask(val task: AquariumTask, val occurrence: TaskOccurrence)

data class TaskResolutionInput(
    val occurrenceId: String,
    val resolution: TaskResolution,
    val resolvedAtEpochMillis: Long,
    val postponedUntil: TaskDate? = null,
    val postponedMinuteOfDay: Int? = null,
    val postponedTimeZoneId: String? = null,
)

data class TaskResolutionResult(val resolved: TaskOccurrence, val nextOccurrence: TaskOccurrence?)

fun nextTaskDate(current: TaskDate, recurrence: TaskRecurrence, monthlyAnchorDay: Int): TaskDate =
    when (recurrence) {
        TaskRecurrence.ONCE -> current
        TaskRecurrence.DAILY -> current.plusDays(1)
        TaskRecurrence.WEEKLY -> current.plusDays(7)
        TaskRecurrence.MONTHLY -> {
            val nextMonth = if (current.month == 12) 1 else current.month + 1
            val nextYear = if (current.month == 12) current.year + 1 else current.year
            TaskDate(nextYear, nextMonth, minOf(monthlyAnchorDay, daysInMonth(nextYear, nextMonth)))
        }
    }

private fun TaskDate.plusDays(days: Int): TaskDate {
    var result = this
    repeat(days) {
        val lastDay = daysInMonth(result.year, result.month)
        result = when {
            result.day < lastDay -> TaskDate(result.year, result.month, result.day + 1)
            result.month < 12 -> TaskDate(result.year, result.month + 1, 1)
            else -> TaskDate(result.year + 1, 1, 1)
        }
    }
    return result
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
    else -> error("Invalid month")
}
