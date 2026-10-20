package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.PortableDataService
import com.maximerollin.aqualog.shared.PortableImportResult
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in phases are invoked in separate instrumented processes, with adb force-stop between them. */
class InstallationQualificationTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test
    fun portableCopy_survivesProcessDeath_andCrossesInstallationSandboxes(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Run only through the #19 qualification protocol", arguments.getString("runInstallationQualification") == "true")
        val phase = arguments.getString("qualificationPhase") ?: error("Missing qualificationPhase")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = createAquaLogDatabase(createAndroidDatabaseBuilder(context, "aqualog-qualification-19.db"))
        val repository = RoomAquariumRepository(database, { UUID.randomUUID().toString() }, System::currentTimeMillis)
        val portable = PortableDataService(database)
        try {
            when (phase) {
                "seed" -> {
                    // Never delete existing data. This database belongs only to this opt-in protocol.
                    assertTrue("Use a fresh #19 qualification database", repository.initialAccountCopy().aquariums.isEmpty())
                    val setup = repository.createConfiguredAquarium(
                        "Qualification #19", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                        OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
                    )
                    repository.saveRapidSession(RapidSessionInput(
                        aquariumId = setup.aquarium.id,
                        occurredAtEpochMillis = System.currentTimeMillis(),
                        idempotencyKey = "qualification-19-save",
                        measurementInputs = mapOf(setup.parameters.first { it.isActive }.id to "24,5"),
                        maintenanceActions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
                    ))
                    File(context.filesDir, "qualification-19-export.json").writeText(portable.export())
                }
                "import" -> {
                    assertTrue("Import only into an empty #19 database", repository.initialAccountCopy().aquariums.isEmpty())
                    val result = portable.importData(File(context.filesDir, "qualification-19-import.json").readText())
                    assertTrue(result is PortableImportResult.Imported)
                }
                "verify" -> Unit
                else -> error("Unknown qualification phase")
            }
            if (BuildConfig.APPLICATION_ID.endsWith(".dev")) {
                assertTrue("Pending sync work must survive process death", repository.pendingSyncBatch().operations.isNotEmpty())
            }
            val aquarium = requireNotNull(repository.observeCurrentAquarium().first())
            assertEquals("Qualification #19", aquarium.name)
            val session = requireNotNull(repository.observeLatestSession(aquarium.id).first())
            assertEquals(1, repository.sessionCount(aquarium.id))
            assertEquals(24.5, session.measurements.single().value, 0.0)
            assertEquals(MaintenanceActionType.WATER_CHANGE, session.maintenanceActions.single().type)
            assertEquals(20.0, session.maintenanceActions.single().quantity!!, 0.0)
            (context.applicationContext as TestAquaLogApplication).installRepository(repository, database)
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            try {
                composeRule.waitUntil(timeoutMillis = 10_000) {
                    composeRule.onAllNodes(hasText("Qualification #19")).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("Qualification #19").assertIsDisplayed()
            } finally {
                scenario.close()
            }
        } finally {
            database.close()
        }
    }
}
