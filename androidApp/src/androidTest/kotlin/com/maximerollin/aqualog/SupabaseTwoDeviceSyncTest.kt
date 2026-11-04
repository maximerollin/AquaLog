package com.maximerollin.aqualog

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.DirectoryAquariumPhotoStore
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.SyncCoordinator
import com.maximerollin.aqualog.shared.SyncRunResult
import com.maximerollin.aqualog.shared.SyncSession
import com.maximerollin.aqualog.shared.SyncSessionProvider
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SupabaseTwoDeviceSyncTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun productionAdapter_syncsTwoRoomStores_andRendersDownloadedSessionInChronology() {
        val configuration = SupabaseConfiguration.fromBuildConfig()
        assumeTrue("Local Supabase URL and anon key are required", configuration.isConfigured)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val authenticated = signUp(
            configuration,
            email = "android-sync-$suffix@example.com",
            password = "Aq!$suffix",
        )
        val firstDatabase = createAquaLogDatabase(createAndroidDatabaseBuilder(context, "sync-first-$suffix.db"))
        val secondDatabase = createAquaLogDatabase(createAndroidDatabaseBuilder(context, "sync-second-$suffix.db"))
        val first = RoomAquariumRepository(firstDatabase, { UUID.randomUUID().toString() }, System::currentTimeMillis)
        val second = RoomAquariumRepository(secondDatabase, { UUID.randomUUID().toString() }, System::currentTimeMillis)
        val provider = object : SyncSessionProvider {
            override suspend fun currentSession() = authenticated
        }

        runBlocking {
            val setup = first.createConfiguredAquarium(
                "Android integration", 90.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            val temperature = setup.parameters.first { it.isActive }
            first.saveRapidSession(
                RapidSessionInput(
                    setup.aquarium.id,
                    System.currentTimeMillis(),
                    "android-supabase-$suffix",
                    mapOf(temperature.id to "24.5"),
                    listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
                    observation = "Downloaded from first installation",
                ),
            )
            assertTrue(SyncCoordinator(first, SupabaseSyncBackend(configuration), provider).runOnce() is SyncRunResult.Synchronized)
            assertTrue(SyncCoordinator(second, SupabaseSyncBackend(configuration), provider).runOnce() is SyncRunResult.Synchronized)
        }

        val application = context.applicationContext as TestAquaLogApplication
        application.installRepository(second, secondDatabase)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Chronology").assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("Open Session details") and hasText("24.5", substring = true) and hasClickAction(),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Water change · 20 %").assertIsDisplayed()
        composeRule.onNodeWithText("Downloaded from first installation").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open Session details").performClick()
        composeRule.onNodeWithText("Observation · Downloaded from first installation").assertIsDisplayed()
        scenario.close()
        firstDatabase.close()
        secondDatabase.close()
    }

    @Test
    fun productionAdapter_syncsAnAquariumPhotoToASecondDevice() {
        val configuration = SupabaseConfiguration.fromBuildConfig()
        assumeTrue("Local Supabase URL and anon key are required", configuration.isConfigured)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val authenticated = signUp(
            configuration,
            email = "android-photo-$suffix@example.com",
            password = "Aq!$suffix",
        )
        val firstDatabase = createAquaLogDatabase(createAndroidDatabaseBuilder(context, "photo-first-$suffix.db"))
        val secondDatabase = createAquaLogDatabase(createAndroidDatabaseBuilder(context, "photo-second-$suffix.db"))
        val firstStore = DirectoryAquariumPhotoStore(File(context.cacheDir, "photo-first-$suffix"))
        val secondStore = DirectoryAquariumPhotoStore(File(context.cacheDir, "photo-second-$suffix"))
        val first = RoomAquariumRepository(
            firstDatabase, { UUID.randomUUID().toString() }, System::currentTimeMillis, photoStore = firstStore,
        )
        val second = RoomAquariumRepository(
            secondDatabase, { UUID.randomUUID().toString() }, System::currentTimeMillis, photoStore = secondStore,
        )
        val provider = object : SyncSessionProvider {
            override suspend fun currentSession() = authenticated
        }
        val backend = SupabaseSyncBackend(configuration)
        val firstCoordinator = SyncCoordinator(first, backend, provider, photoStore = firstStore)
        val secondCoordinator = SyncCoordinator(second, backend, provider, photoStore = secondStore)
        val jpeg = ByteArrayOutputStream().also {
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it)
        }.toByteArray()

        runBlocking {
            val aquarium = first.createConfiguredAquarium(
                "Android photo", 90.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            ).aquarium
            first.setAquariumPhoto(aquarium.id, jpeg)
            firstCoordinator.runOnce().let { assertTrue(it.toString(), it is SyncRunResult.Synchronized) }
            secondCoordinator.runOnce().let { assertTrue(it.toString(), it is SyncRunResult.Synchronized) }
            val photoId = second.loadSetup(aquarium.id)!!.aquarium.photoId!!
            assertArrayEquals(jpeg, second.readAquariumPhoto(photoId))

            first.setAquariumPhoto(aquarium.id, null)
            firstCoordinator.runOnce().let { assertTrue(it.toString(), it is SyncRunResult.Synchronized) }
            secondCoordinator.runOnce().let { assertTrue(it.toString(), it is SyncRunResult.Synchronized) }
            assertNull(second.loadSetup(aquarium.id)!!.aquarium.photoId)
            assertNull(backend.downloadAquariumPhoto(authenticated, aquarium.id))
        }
        firstDatabase.close()
        secondDatabase.close()
    }

    private fun signUp(configuration: SupabaseConfiguration, email: String, password: String): SyncSession {
        val connection = URL("${configuration.projectUrl}/auth/v1/signup").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", configuration.anonymousKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use {
                it.write(JSONObject().put("email", email).put("password", password).toString().toByteArray())
            }
            val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            check(connection.responseCode in 200..299) { "Local Supabase signup failed: HTTP ${connection.responseCode}" }
            val json = JSONObject(response)
            SyncSession(json.getJSONObject("user").getString("id"), json.getString("access_token"))
        } finally {
            connection.disconnect()
        }
    }
}
