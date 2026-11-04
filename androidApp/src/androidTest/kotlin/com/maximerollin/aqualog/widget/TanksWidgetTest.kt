package com.maximerollin.aqualog.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.MainActivity
import com.maximerollin.aqualog.WidgetTarget
import java.io.ByteArrayOutputStream
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TanksWidgetTest {
    private val rows = listOf(
        row("salon", "Bac du salon", WidgetLight.ATTENTION, "Changement d’eau en retard"),
        row("crevettes", "Crevettes", WidgetLight.OK, "Rien à faire"),
        row("nano", "Nano", WidgetLight.ALERT, "NO₂ à corriger"),
    )

    @Test
    fun oneRowPerAquarium_withDotAndNextStep() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        provideComposable { TanksContent(WidgetContent.Tanks(rows)) }

        for (row in rows) {
            onNode(hasTextEqualTo(row.name)).assertExists()
            onNode(hasTextEqualTo(row.next)).assertExists()
            onNode(hasContentDescriptionEqualTo(row.lightDescription)).assertExists()
            onNode(hasContentDescriptionEqualTo(row.contentDescription)).assertExists()
        }
        onNode(hasContentDescriptionEqualTo("Bac du salon, en dehors de votre plage indicative, Changement d’eau en retard"))
            .assertExists()
    }

    @Test
    fun aRowWithAPhotoShowsItBeforeTheDot_aRowWithoutKeepsTheDotOnly() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        val photo = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        provideComposable { TanksContent(WidgetContent.Tanks(rows), mapOf("salon" to photo)) }

        onNode(hasTestTag(TankPhotoTag("salon"))).assertExists()
        onNode(hasTestTag(TankPhotoTag("crevettes"))).assertDoesNotExist()
        for (row in rows) {
            onNode(hasContentDescriptionEqualTo(row.lightDescription)).assertExists()
            onNode(hasContentDescriptionEqualTo(row.contentDescription)).assertExists()
        }
    }

    @Test
    fun aPhotoIsDecodedToASmallSquare() {
        val jpeg = ByteArrayOutputStream().also {
            Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it)
        }.toByteArray()

        val photo = checkNotNull(decodeTankPhoto(jpeg))

        assertEquals(108 to 108, photo.width to photo.height)
        assertEquals(Bitmap.Config.RGB_565, photo.config)
        assertTrue(photo.byteCount <= 25_000)
    }

    @Test
    fun eachRowOpensItsAquarium() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        provideComposable { TanksContent(WidgetContent.Tanks(rows)) }

        for (row in rows) {
            onNode(hasStartActivityClickAction<MainActivity>(extras(row.aquariumId, WidgetTarget.HOME)))
                .assertHasContentDescriptionEqualTo(row.contentDescription)
        }
    }

    @Test
    fun needsPro_opensThePaywall() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        provideComposable { TanksContent(WidgetContent.NeedsPro) }

        onNode(hasStartActivityClickAction<MainActivity>(extras(null, WidgetTarget.PRO))).assertExists()
    }

    private fun row(id: String, name: String, light: WidgetLight, next: String): WidgetTankText {
        val lightDescription = when (light) {
            WidgetLight.ALERT -> "hors de la zone de sécurité"
            WidgetLight.ATTENTION -> "en dehors de votre plage indicative"
            WidgetLight.OK -> "tout est dans la plage"
        }
        return WidgetTankText(
            aquariumId = id, name = name, light = light, lightDescription = lightDescription,
            contentDescription = "$name, $lightDescription, $next", next = next, status = "", tasks = emptyList(),
        )
    }

    private fun localized(language: String): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(configuration)
    }

    private fun extras(aquariumId: String?, target: WidgetTarget): ActionParameters {
        val targetPair = ActionParameters.Key<String>(MainActivity.EXTRA_WIDGET_TARGET) to target.name
        return if (aquariumId == null) actionParametersOf(targetPair)
        else actionParametersOf(targetPair, ActionParameters.Key<String>(MainActivity.EXTRA_WIDGET_AQUARIUM_ID) to aquariumId)
    }
}
