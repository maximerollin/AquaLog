package com.maximerollin.aqualog.widget

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.BackgroundModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
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
import com.maximerollin.aqualog.HomeValue
import com.maximerollin.aqualog.MainActivity
import com.maximerollin.aqualog.WidgetTarget
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.ui.theme.CarnetDarkColors
import com.maximerollin.aqualog.ui.theme.CarnetLightColors
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
            .assertHasContentDescriptionEqualTo("Salon, très en dehors de votre plage indicative, NO₂ très hors plage")
    }

    @Test
    fun neverMeasured_saysSoInsteadOfAllInRange() = runGlanceAppWidgetUnitTest {
        val text = text("fr", BuiltInParameter.NITRITE, measured = false)
        setContext(localized("fr"))
        provideComposable { SmallTankContent(WidgetContent.Tank(text)) }

        onNode(hasContentDescriptionEqualTo("Pas encore de mesure")).assertExists()
        onNode(hasStartActivityClickAction<MainActivity>(extras("salon", WidgetTarget.HOME)))
            .assertHasContentDescriptionEqualTo("Salon, Pas encore de mesure, Rien à faire")
    }

    @Test
    fun neverMeasured_dotIsTheOutlineColour_measuredOkIsTeal() = runGlanceAppWidgetUnitTest {
        val never = text("fr", BuiltInParameter.NITRITE, measured = false)
        setContext(localized("fr"))
        provideComposable {
            WidgetTheme {
                SmallTankContent(WidgetContent.Tank(never))
                StatusDot(never.copy(light = WidgetLight.OK, isMeasured = true, lightDescription = "measured"))
            }
        }

        onNode(dotColoured(never.lightDescription, ColorProvider(CarnetLightColors.outline, CarnetDarkColors.outline))).assertExists()
        onNode(dotColoured("measured", ColorProvider(CarnetLightColors.primary, CarnetDarkColors.primary))).assertExists()
    }

    @Test
    fun liveContent_reloadsFromTheGlanceState() = runGlanceAppWidgetUnitTest {
        val salon = text("fr", BuiltInParameter.NITRITE)
        setContext(localized("fr"))
        setState(preferencesOf(PINNED_AQUARIUM_ID to "salon", REFRESH_COUNT to 1L))
        // Starts from the pre-loaded content, then follows what the Glance state asks for.
        provideComposable {
            val content = liveWidgetContent(WidgetContent.NoAquarium) { state ->
                if (state[PINNED_AQUARIUM_ID] == "salon") WidgetContent.Tank(salon) else WidgetContent.NoAquarium
            }
            SmallTankContent(content)
        }
        awaitIdle()

        onNode(hasTextEqualTo("Salon")).assertExists()
        onNode(hasTextEqualTo("Ouvrez Aquagenda pour créer votre bac")).assertDoesNotExist()
    }

    @Test
    fun germanLargeFont_nextStepIsEllipsizedNotCut() = runGlanceAppWidgetUnitTest {
        val text = text("de", BuiltInParameter.PHOSPHATE)
        setContext(localized("de"))
        provideComposable { SmallTankContent(WidgetContent.Tank(text)) }

        onNode(hasTextEqualTo(text.next).and(hasMaxLines(2))).assertExists()
        onNode(hasTextEqualTo("Salon").and(hasMaxLines(1))).assertExists()
    }

    private fun text(language: String, parameter: BuiltInParameter, measured: Boolean = true): WidgetTankText {
        val definition = ParameterDefinition("nitrite", "salon", parameter, true, 0, "mg/L", 2, 0.0, 0.1)
        val measuredAt = System.currentTimeMillis()
        val state = if (measured) {
            WidgetAquarium(
                aquarium = Aquarium("salon", "Salon", 120.0, VolumeUnit.LITERS, 0L),
                overview = HomeOverview(
                    listOf(HomeValue(definition, 2.0, measuredAt, ReadingLevel.ALERT, false)),
                    outsideRangeCount = 1,
                    latestReadingAtEpochMillis = measuredAt,
                    alertCount = 1,
                ),
                light = WidgetLight.ALERT,
                next = NextStep.Value(definition, ReadingLevel.ALERT),
                dueTasks = emptyList(),
            )
        } else {
            WidgetAquarium(
                aquarium = Aquarium("salon", "Salon", 120.0, VolumeUnit.LITERS, 0L),
                overview = HomeOverview(listOf(HomeValue(definition, null, null, ReadingLevel.OK, false)), 0, null),
                light = WidgetLight.OK,
                next = NextStep.Nothing,
                dueTasks = emptyList(),
            )
        }
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

    private fun dotColoured(description: String, color: GlanceColorProvider) = hasContentDescriptionEqualTo(description)
        .and(GlanceNodeMatcher<MappedNode>("background $color") { node ->
            node.value.emittable.modifier.any { it is BackgroundModifier.Color && it.colorProvider == color }
        })

    private fun hasMaxLines(lines: Int) = GlanceNodeMatcher<MappedNode>("maxLines == $lines") { node ->
        (node.value.emittable as? EmittableText)?.maxLines == lines
    }
}
