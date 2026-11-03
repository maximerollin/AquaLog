package com.maximerollin.aqualog.widget

import android.content.Context
import android.content.res.Configuration
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.glance.text.EmittableText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.HomeOverview
import com.maximerollin.aqualog.MainActivity
import com.maximerollin.aqualog.WidgetTarget
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmallTankWidgetTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun alertTank_showsNameNextStepAndAlertDescription() = runGlanceAppWidgetUnitTest {
        val text = text("fr", BuiltInParameter.NITRITE)
        setContext(localized("fr"))
        provideComposable { SmallTankContent(WidgetContent.Tank(text)) }

        onNode(hasTextEqualTo("Salon")).assertExists()
        onNode(hasTextEqualTo("NO₂ très hors plage")).assertExists()
        onNode(hasContentDescriptionEqualTo("très en dehors de votre plage indicative")).assertExists()
    }

    @Test
    fun needsPro_showsTheInvitation_andOpensThePaywall() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        provideComposable { SmallTankContent(WidgetContent.NeedsPro) }

        onNode(hasTextEqualTo("Le widget fait partie d’Aquagenda Pro")).assertExists()
        onNode(hasStartActivityClickAction<MainActivity>(extras(null, WidgetTarget.PRO))).assertExists()
    }

    @Test
    fun noAquarium_showsTheInvitationToOpenTheApp() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        provideComposable { SmallTankContent(WidgetContent.NoAquarium) }

        onNode(hasTextEqualTo("Ouvrez Aquagenda pour créer votre bac")).assertExists()
        onNode(hasStartActivityClickAction<MainActivity>(extras(null, WidgetTarget.HOME))).assertExists()
    }

    @Test
    fun tank_tapOpensTheAppOnThatAquarium() = runGlanceAppWidgetUnitTest {
        val text = text("fr", BuiltInParameter.NITRITE)
        setContext(localized("fr"))
        provideComposable { SmallTankContent(WidgetContent.Tank(text)) }

        onNode(hasStartActivityClickAction<MainActivity>(extras("salon", WidgetTarget.HOME)))
            .assertHasContentDescriptionEqualTo("Salon, NO₂ très hors plage")
    }

    @Test
    fun germanLargeFont_nextStepIsEllipsizedNotCut() = runGlanceAppWidgetUnitTest {
        val text = text("de", BuiltInParameter.PHOSPHATE)
        setContext(localized("de"))
        provideComposable { SmallTankContent(WidgetContent.Tank(text)) }

        onNode(hasTextEqualTo(text.next).and(hasMaxLines(2))).assertExists()
        onNode(hasTextEqualTo("Salon").and(hasMaxLines(1))).assertExists()
    }

    private fun text(language: String, parameter: BuiltInParameter): WidgetTankText {
        val definition = ParameterDefinition("nitrite", "salon", parameter, true, 0, "mg/L", 2, 0.0, 0.1)
        val state = WidgetAquarium(
            aquarium = Aquarium("salon", "Salon", 120.0, VolumeUnit.LITERS, 0L),
            overview = HomeOverview(emptyList(), outsideRangeCount = 1, latestReadingAtEpochMillis = null, alertCount = 1),
            light = WidgetLight.ALERT,
            next = NextStep.Value(definition, ReadingLevel.ALERT),
            dueTasks = emptyList(),
        )
        // Compose resources read their language from the default locale.
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(language))
        return try { runBlocking { widgetText(state, today) } } finally { Locale.setDefault(previous) }
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

    private fun hasMaxLines(lines: Int) = GlanceNodeMatcher<MappedNode>("maxLines == $lines") { node ->
        (node.value.emittable as? EmittableText)?.maxLines == lines
    }
}
