package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.TaskActions
import com.maximerollin.aqualog.TaskContent
import com.maximerollin.aqualog.TaskUiState
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tasks and reminders from a fake state: days, overdue and undated rows, the round check, the new-task sheet (lot 4a, Task 3). */
@OptIn(ExperimentalTestApi::class)
class TaskSemanticsTest {
    private val salon = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L)
    private val today = TaskDate(2026, 11, 17)
    private val overdue = TaskOccurrence(
        "o1", "t1", salon.id, "Changement d'eau", TaskDate(2026, 11, 15), 9 * 60, "Europe/Paris", null, null,
        MaintenanceActionType.WATER_CHANGE,
    )
    private val evening = TaskOccurrence("o2", "t2", salon.id, "Nettoyer la vitre", today, 18 * 60, "Europe/Paris", null, null)
    private val tomorrowUntimed =
        TaskOccurrence("o3", "t3", salon.id, "Tailler les plantes", TaskDate(2026, 11, 18), null, "Europe/Paris", null, null)

    private var errorColor = Color.Unspecified

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun french(block: () -> Unit) = withLocale(Locale.FRENCH, block)

    private fun ComposeUiTest.tasks(state: TaskUiState, actions: TaskActions = TaskActions(), fontScale: Float = 1f) {
        setContent {
            AquaLogTheme(false) {
                errorColor = MaterialTheme.colorScheme.error
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp).testTag("frame")) { TaskContent(state, salon, today, actions) }
                }
            }
        }
        waitForIdle()
    }

    /** The node matching [matcher], scrolled into the list first: rows below the fold are not composed. */
    private fun ComposeUiTest.row(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        onNodeWithTag("task-list").performScrollToNode(matcher)
        return onNode(matcher)
    }

    @Test
    fun `overdue row says so with its date and recurrence`() = french {
        runComposeUiTest {
            tasks(
                TaskUiState(
                    pendingOccurrences = listOf(evening, overdue),
                    recurrenceByTaskId = mapOf("t1" to TaskRecurrence.WEEKLY),
                ),
            )
            val dueDate = LocalizedFormatters(Locale.FRENCH).date(2026, 11, 15)
            assertTrue(dueDate.startsWith("15 nov."), dueDate)
            val detail = row(hasText("En retard", substring = true)).fetchSemanticsNode()
            val text = detail.config[SemanticsProperties.Text].single()
            assertEquals("En retard · $dueDate · Hebdomadaire", text.text)
            // « En retard » is in the alert colour, and only it.
            val alert = text.spanStyles.single { it.item.color == errorColor }
            assertEquals("En retard", text.text.substring(alert.start, alert.end))

            // Under « Aujourd'hui », before today's own task.
            val label = row(hasText("AUJOURD’HUI")).fetchSemanticsNode().boundsInRoot
            val overdueTop = row(hasText("Changement d'eau")).fetchSemanticsNode().boundsInRoot.top
            val eveningTop = row(hasText("Nettoyer la vitre")).fetchSemanticsNode().boundsInRoot.top
            assertTrue(label.bottom <= overdueTop && overdueTop < eveningTop, "label $label, overdue $overdueTop, evening $eveningTop")
        }
    }

    @Test
    fun `unknown recurrence is omitted`() = french {
        runComposeUiTest {
            tasks(TaskUiState(pendingOccurrences = listOf(evening, tomorrowUntimed)))
            val formatter = LocalizedFormatters(Locale.FRENCH)
            row(hasText(formatter.time(18 * 60)))
            // An undated time reads as such, under « Demain ».
            val untimed = row(hasText("pas d'heure choisie")).fetchSemanticsNode().boundsInRoot
            val tomorrow = row(hasText("DEMAIN")).fetchSemanticsNode().boundsInRoot
            assertTrue(tomorrow.bottom <= untimed.top)
            onAllNodes(hasText(" · ", substring = true)).assertCountEquals(0)
        }
    }

    @Test
    fun `check is a 48 dp checkbox named after the task`() = french {
        runComposeUiTest {
            val completed = mutableListOf<String>()
            tasks(
                TaskUiState(pendingOccurrences = listOf(overdue)),
                TaskActions(onComplete = { occurrence, _ -> completed += occurrence.id }),
            )
            val check = row(hasContentDescription("Fait, Changement d'eau"))
            check.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            check.performClick()
            check.performClick()
            // The double-tap guard: one completion.
            assertEquals(listOf("o1"), completed)
        }
    }

    @Test
    fun `header plus opens the new task sheet`() = french {
        runComposeUiTest {
            var back = 0
            tasks(TaskUiState(), TaskActions(onBack = { back++ }))
            // The header « + » replaces the section's text action.
            onAllNodesWithText("Nouvelle tâche").assertCountEquals(0)
            onAllNodesWithText("Enregistrer la tâche").assertCountEquals(0)
            onNode(hasContentDescription("Nouvelle tâche") and hasClickAction()).performClick()
            waitForIdle()
            onNode(hasText("Enregistrer la tâche")).assertExists()
            onNodeWithTag("tasks-back").assert(hasContentDescription("Retour"))
        }
    }

    @Test
    fun `no pending task says so`() = french {
        runComposeUiTest {
            tasks(TaskUiState(batteryOptimizationActive = true))
            row(hasText("Aucune tâche en attente.")).assertExists()
            row(hasText("L'optimisation de la batterie Android est active", substring = true)).assertExists()
        }
    }

    @Test
    fun `date and time tiles share a row at 100 percent`() = french {
        runComposeUiTest {
            tasks(TaskUiState())
            onNode(hasContentDescription("Nouvelle tâche") and hasClickAction()).performClick()
            waitForIdle()
            val date = onNode(hasText("Première date d'échéance") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val time = onNode(hasText("Heure (facultatif)") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertEquals(date.top, time.top)
            assertTrue(time.left >= date.right, "date $date, time $time")
        }
    }

    @Test
    fun `date and time tiles stack at 150 percent`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            tasks(TaskUiState(), fontScale = 1.5f)
            onNode(hasContentDescription("Neue Aufgabe") and hasClickAction()).performClick()
            waitForIdle()
            val date = onNode(hasText("Erster Fälligkeitstermin") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val time = onNode(hasText("Zeit (optional)") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue(time.top >= date.bottom, "date $date, time $time")
        }
    }
}
