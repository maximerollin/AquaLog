package com.maximerollin.aqualog.widget

import android.content.Context
import android.content.res.Configuration
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.MainActivity
import com.maximerollin.aqualog.WidgetTarget
import java.util.Locale
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
