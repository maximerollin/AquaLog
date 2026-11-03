package com.maximerollin.aqualog.shared

data class TaskDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12)
        require(day in 1..daysInMonth(year, month))
    }

    companion object {
        /** Inverse of [toEpochDay] (days since 1970-01-01, proleptic Gregorian). */
        fun fromEpochDay(epochDay: Long): TaskDate {
            val z = epochDay + 719468
            val era = z.floorDiv(146097L)
            val dayOfEra = z - era * 146097
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shiftedMonth = (5 * dayOfYear + 2) / 153
            val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
            val month = (if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9).toInt()
            val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
            return TaskDate(year, month, day)
        }
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
    val maintenanceActionType: MaintenanceActionType? = null,
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
    val maintenanceActionType: MaintenanceActionType? = null,
)

data class TaskInput(
    val aquariumId: String,
    val title: String,
    val recurrence: TaskRecurrence,
    val firstDueDate: TaskDate,
    val minuteOfDay: Int?,
    val timeZoneId: String,
    val maintenanceActionType: MaintenanceActionType? = null,
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

fun TaskDate.toEpochDay(): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = y.floorDiv(400L)
    val yearOfEra = y - era * 400
    val shiftedMonth = if (month > 2) month - 3 else month + 9
    val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097 + dayOfEra - 719468
}

/** [days] may be negative. */
fun TaskDate.plusDays(days: Int): TaskDate = TaskDate.fromEpochDay(toEpochDay() + days)

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
    else -> error("Invalid month")
}
