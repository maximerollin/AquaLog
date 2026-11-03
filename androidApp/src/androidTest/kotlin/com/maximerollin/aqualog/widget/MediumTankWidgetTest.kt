package com.maximerollin.aqualog.widget

import android.content.Context
import android.content.res.Configuration
import androidx.glance.action.ActionParameters
import androidx.glance.action.ActionModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.RunCallbackAction
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.EmittableCheckBox
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.Emittable
import androidx.glance.layout.EmittableRow
import androidx.glance.text.EmittableText
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.MainActivity
import com.maximerollin.aqualog.TestAquaLogApplication
import com.maximerollin.aqualog.WidgetTarget
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskInput
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.TimelineFilter
import com.maximerollin.aqualog.shared.VolumeUnit
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediumTankWidgetTest {
    private val status = "1 valeur à surveiller · mesuré il y a 2 j"
    private val tasks = listOf(
        WidgetTaskRow("o-1", "Changer l’eau", isOverdue = true),
        WidgetTaskRow("o-2", "Nettoyer le filtre", isOverdue = false),
    )

    @Test
    fun showsStatusTasksAndMeasure() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { MediumTankContent(WidgetContent.Tank(text(tasks))) }

        onNode(hasTextEqualTo("Salon")).assertExists()
        onNode(hasTextEqualTo(status)).assertExists()
        onNode(hasTextEqualTo("Changer l’eau")).assertExists()
        onNode(hasTextEqualTo("Nettoyer le filtre")).assertExists()
        onAllNodes(hasTextEqualTo("en retard")).assertCountEquals(1)
        onNode(rowWithOverdueMarker("o-1")).assertExists()
        onNode(rowWithOverdueMarker("o-2")).assertDoesNotExist()
        onNode(hasStartActivityClickAction<MainActivity>(extras("salon", WidgetTarget.MEASURE)))
            .assertHasContentDescriptionEqualTo("Mesurer")
    }

    @Test
    fun eachTaskCheckbox_runsCompleteTaskWithItsOccurrence() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { MediumTankContent(WidgetContent.Tank(text(tasks))) }

        for (task in tasks) {
            onNode(checksOff(task.occurrenceId))
                .assertHasContentDescriptionEqualTo(task.title)
        }
    }

    @Test
    fun noTasks_showsNextStepInstead() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { MediumTankContent(WidgetContent.Tank(text(emptyList()))) }

        onNode(hasTextEqualTo("Rien à faire")).assertExists()
        onNode(isACheckBox()).assertDoesNotExist()
        onNode(hasContentDescriptionEqualTo("Mesurer")).assertExists()
    }

    @Test
    fun tooShortForRows_showsNextStepAndKeepsMeasureInTheHeader() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { MediumTankContent(WidgetContent.Tank(text(tasks))) }

        onNode(isACheckBox()).assertDoesNotExist()
        onNode(hasTextEqualTo("Rien à faire")).assertExists()
        onNode(hasStartActivityClickAction<MainActivity>(extras("salon", WidgetTarget.MEASURE))).assertExists()
    }

    @Test
    fun needsPro_showsTheInvitation() = runGlanceAppWidgetUnitTest {
        setContext(localized("fr"))
        provideComposable { MediumTankContent(WidgetContent.NeedsPro) }

        onNode(hasStartActivityClickAction<MainActivity>(extras(null, WidgetTarget.PRO))).assertExists()
    }

    @Test
    fun completeTaskAction_completesALinkedTaskOnce() = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val repository = application.aquariumRepository
        val today = LocalDate.now()
        val setup = repository.createConfiguredAquarium(
            name = "Salon", volume = 120.0, volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED, parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val created = repository.createTask(
            TaskInput(
                aquariumId = setup.aquarium.id,
                title = "Changer l’eau",
                recurrence = TaskRecurrence.WEEKLY,
                firstDueDate = TaskDate(today.year, today.monthValue, today.dayOfMonth),
                minuteOfDay = null,
                timeZoneId = ZoneId.systemDefault().id,
                maintenanceActionType = MaintenanceActionType.WATER_CHANGE,
            ),
        )
        val params = occurrence(created.occurrence.id)

        repeat(2) { CompleteTaskAction().onAction(application, AppWidgetId(1), params) }

        val sessions = repository.observeTimeline(TimelineFilter(aquariumId = setup.aquarium.id)).first()
        assertEquals(1, sessions.size)
        assertEquals(listOf(MaintenanceActionType.WATER_CHANGE), sessions.single().maintenanceActions.map { it.type })
        assertEquals(created.occurrence.id, repository.observeResolvedTaskOccurrences().first().single().id)
    }

    private fun text(tasks: List<WidgetTaskRow>) = WidgetTankText(
        aquariumId = "salon",
        name = "Salon",
        light = WidgetLight.ATTENTION,
        lightDescription = "en dehors de votre plage indicative",
        contentDescription = "Salon",
        next = "Rien à faire",
        status = status,
        tasks = tasks,
    )

    // The library's hasRunCallbackClickAction does not unwrap a checkbox's CompoundButtonAction, which is internal: reflection reads its inner action.
    private fun checksOff(occurrenceId: String) = GlanceNodeMatcher<MappedNode>("checkbox runs CompleteTaskAction for $occurrenceId") {
        it.value.emittable.checksOff(occurrenceId)
    }

    private fun rowWithOverdueMarker(occurrenceId: String) = GlanceNodeMatcher<MappedNode>("row of $occurrenceId marked en retard") { node ->
        val children = (node.value.emittable as? EmittableRow)?.children.orEmpty()
        children.any { it.checksOff(occurrenceId) } && children.any { (it as? EmittableText)?.text == "en retard" }
    }

    private fun Emittable.checksOff(occurrenceId: String): Boolean {
        val inner = modifier
            .foldIn<ActionModifier?>(null) { found, element -> (element as? ActionModifier) ?: found }
            ?.action?.let { it.javaClass.takeIf { type -> type.simpleName == "CompoundButtonAction" }?.getMethod("getInnerAction")?.invoke(it) }
            as? RunCallbackAction
        return inner?.callbackClass == CompleteTaskAction::class.java && inner.parameters == occurrence(occurrenceId)
    }

    private fun isACheckBox() = GlanceNodeMatcher<MappedNode>("is a checkbox") { it.value.emittable is EmittableCheckBox }

    private fun occurrence(id: String) = actionParametersOf(CompleteTaskAction.OCCURRENCE_ID to id)

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
