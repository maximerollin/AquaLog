package com.maximerollin.aqualog

import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.shared.FeedbackGateway
import com.maximerollin.aqualog.shared.FeedbackKind
import com.maximerollin.aqualog.shared.FeedbackOutcome
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.VoluntaryFeedback
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackStateTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class Fixture(var outcome: FeedbackOutcome) {
        val sent = mutableListOf<VoluntaryFeedback>()
        private val directory = Files.createTempDirectory("aqualog-feedback-vm")
        private val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val viewModel = HomeViewModel(
            RoomAquariumRepository(database, { UUID.randomUUID().toString() }, { 1_700_000_000_000L }),
            feedbackGateway = FeedbackGateway { sent += it; outcome },
        )
        val feedback get() = viewModel.uiState.value.feedback

        suspend fun close() {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            database.close()
        }
    }

    @Test
    fun `opening on a species sets its kind, id and name`() = runTest {
        val fixture = Fixture(FeedbackOutcome.SENT)
        fixture.viewModel.openFeedback(FeedbackKind.SPECIES, "neon", "Néon bleu")

        assertTrue(fixture.feedback.isOpen)
        assertEquals(FeedbackKind.SPECIES, fixture.feedback.kind)
        assertEquals("neon", fixture.feedback.speciesId)
        assertEquals("Néon bleu", fixture.feedback.speciesName)
        fixture.close()
    }

    @Test
    fun `submit sends the message, kind, species and language`() = runTest {
        val fixture = Fixture(FeedbackOutcome.SENT)
        fixture.viewModel.openFeedback(FeedbackKind.SPECIES, "neon", "Néon bleu")
        fixture.viewModel.updateFeedback("Mauvais pH")
        fixture.viewModel.submitFeedback("fr")

        assertEquals(listOf(VoluntaryFeedback("Mauvais pH", FeedbackKind.SPECIES, "neon", "fr")), fixture.sent)
        fixture.close()
    }

    @Test
    fun `a sent message is cleared and the dialog stays open with the notice`() = runTest {
        val fixture = Fixture(FeedbackOutcome.SENT)
        fixture.viewModel.openFeedback()
        fixture.viewModel.updateFeedback("Bonjour")
        fixture.viewModel.submitFeedback("fr")

        assertEquals("", fixture.feedback.message)
        assertTrue(fixture.feedback.isOpen)
        assertEquals(FeedbackOutcome.SENT, fixture.feedback.outcome)
        fixture.close()
    }

    @Test
    fun `rate limited and failed sends keep the message`() = runTest {
        for (outcome in listOf(FeedbackOutcome.RATE_LIMITED, FeedbackOutcome.FAILED)) {
            val fixture = Fixture(outcome)
            fixture.viewModel.openFeedback()
            fixture.viewModel.updateFeedback("Bonjour")
            fixture.viewModel.submitFeedback("fr")

            assertEquals("Bonjour", fixture.feedback.message)
            assertEquals(outcome, fixture.feedback.outcome)
            fixture.close()
        }
    }

    @Test
    fun `choosing a kind keeps the message`() = runTest {
        val fixture = Fixture(FeedbackOutcome.SENT)
        fixture.viewModel.openFeedback()
        fixture.viewModel.updateFeedback("Bonjour")
        fixture.viewModel.selectFeedbackKind(FeedbackKind.BUG)

        assertEquals(FeedbackKind.BUG, fixture.feedback.kind)
        assertEquals("Bonjour", fixture.feedback.message)
        fixture.close()
    }

    @Test
    fun `a second submit while one is in flight is ignored`() = runTest {
        val release = CompletableDeferred<Unit>()
        val sent = mutableListOf<VoluntaryFeedback>()
        val directory = Files.createTempDirectory("aqualog-feedback-vm")
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val viewModel = HomeViewModel(
            RoomAquariumRepository(database, { UUID.randomUUID().toString() }, { 1_700_000_000_000L }),
            feedbackGateway = FeedbackGateway { sent += it; release.await(); FeedbackOutcome.SENT },
        )
        viewModel.openFeedback()
        viewModel.updateFeedback("Bonjour")

        viewModel.submitFeedback("fr")
        assertTrue(viewModel.uiState.value.feedback.isSending)
        viewModel.submitFeedback("fr")
        release.complete(Unit)

        assertEquals(1, sent.size)
        assertEquals(false, viewModel.uiState.value.feedback.isSending)
        assertEquals(FeedbackOutcome.SENT, viewModel.uiState.value.feedback.outcome)
        viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        database.close()
    }
}
