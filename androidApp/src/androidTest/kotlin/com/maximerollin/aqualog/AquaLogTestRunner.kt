package com.maximerollin.aqualog

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.maximerollin.aqualog.shared.AquaLogDatabase
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import com.maximerollin.aqualog.shared.TaskOccurrence

class AquaLogTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, TestAquaLogApplication::class.java.name, context)
}

class TestAquaLogApplication : AquaLogApplication() {
    private var database: AquaLogDatabase? = null
    private var repository: AquariumRepository? = null
    private var databaseSequence = 0
    val recordingTaskReminderScheduler = RecordingTaskReminderScheduler()

    override val taskReminderScheduler: TaskReminderScheduler
        get() = recordingTaskReminderScheduler

    override val aquariumRepository: AquariumRepository
        get() = requireNotNull(repository) { "resetRepository() must run before launching MainActivity" }

    fun resetRepository() {
        database?.close()
        val databaseName = "aqualog-test-${databaseSequence++}.db"
        deleteDatabase(databaseName)
        val newDatabase = createAquaLogDatabase(createAndroidDatabaseBuilder(this, databaseName))
        database = newDatabase
        repository = RoomAquariumRepository(
            database = newDatabase,
            generateId = { UUID.randomUUID().toString() },
            currentTimeMillis = System::currentTimeMillis,
        )
        recordingTaskReminderScheduler.reset()
    }

    override fun onTerminate() {
        database?.close()
        super.onTerminate()
    }
}

class RecordingTaskReminderScheduler : TaskReminderScheduler {
    val scheduledOccurrenceIds = CopyOnWriteArrayList<String>()
    val canceledOccurrenceIds = CopyOnWriteArrayList<String>()

    override fun schedule(occurrence: TaskOccurrence) {
        scheduledOccurrenceIds += occurrence.id
    }

    override fun cancel(occurrenceId: String) {
        canceledOccurrenceIds += occurrenceId
    }

    override fun reconcile(occurrences: List<TaskOccurrence>) {
        occurrences.forEach(::schedule)
    }

    override fun isBatteryOptimizationActive(): Boolean = false

    fun reset() {
        scheduledOccurrenceIds.clear()
        canceledOccurrenceIds.clear()
    }
}
