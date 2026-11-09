package com.maximerollin.aqualog.habitants

import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.MainDestination
import com.maximerollin.aqualog.shared.AquariumCreationAccess
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.encyclopedia.Reason
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.Fit
import com.maximerollin.aqualog.shared.encyclopedia.SpeciesKind
import com.maximerollin.aqualog.shared.encyclopedia.assessSpecies
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AddInhabitantStateTest {
    private val species = decodeSpecies(File("src/commonMain/composeResources/files/encyclopedia/species.json").readText())

    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private inner class Fixture(load: suspend () -> List<com.maximerollin.aqualog.shared.encyclopedia.Species> = { species }) {
        private val directory = Files.createTempDirectory("aqualog-add-inhabitant")
        private val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val repository = RoomAquariumRepository(database, { UUID.randomUUID().toString() }, { 1_700_000_000_000L })
        val viewModel = HomeViewModel(repository, loadSpecies = load)
        lateinit var setup: AquariumSetup

        suspend fun start(): HomeViewModel {
            setup = repository.createConfiguredAquarium(
                "Salon", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED), AquariumCreationAccess.PRO,
            )
            viewModel.uiState.first { it.setup?.aquarium?.id == setup.aquarium.id }
            return viewModel
        }

        suspend fun openLoaded() {
            viewModel.openAddInhabitant()
            viewModel.uiState.first { it.addInhabitant?.species != null }
        }

        suspend fun present() = repository.observeInhabitants(setup.aquarium.id).first()

        /** Waits for the view model's work to end, so nothing touches the database or Main once they are gone. */
        suspend fun close() {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            database.close()
        }
    }

    @Test
    fun `search finds "neon" for "néon", "Neon" and "Paracheirodon"`() {
        listOf("néon", "Neon", "neon", "NÉON", "Paracheirodon").forEach { query ->
            assertTrue("neon" in searchSpecies(species, query, "fr", null).map { it.id }, query)
        }
        assertTrue("neon" in searchSpecies(species, "neon", "en", null).map { it.id })
        assertEquals(listOf("cardinalis", "neon", "neon-vert"), searchSpecies(species, "paracheirodon", "fr", null).map { it.id }.sorted())
        assertEquals(emptyList(), searchSpecies(species, "zzz", "fr", null))
        assertEquals(species.size, searchSpecies(species, "  ", "fr", null).size)
    }

    @Test
    fun `the kind chips filter`() {
        val shrimps = searchSpecies(species, "", "fr", SpeciesKind.SHRIMP)
        assertTrue(setOf("red-cherry", "amano").all { id -> shrimps.any { it.id == id } })
        assertTrue(shrimps.all { it.kind == SpeciesKind.SHRIMP })
        assertEquals(emptyList(), searchSpecies(species, "neon", "fr", SpeciesKind.SHRIMP))
        assertTrue(searchSpecies(species, "", "fr", SpeciesKind.FISH).all { it.kind == SpeciesKind.FISH })
    }

    @Test
    fun `grouping keeps the three sections in order`() = runTest {
        val fixture = Fixture()
        fixture.start()
        val byId = species.associateBy { it.id }
        val assessments = listOf("scalaire", "otocinclus", "guppy", "corydoras-panda", "neon")
            .map { assessSpecies(byId.getValue(it), fixture.setup, emptyList(), emptyList()) }
            .map { if (it.species.id == "guppy") it.copy(fit = Fit.CAUTION) else it }

        val grouped = groupedForAquarium(assessments, "fr")

        assertEquals(listOf(Fit.GOOD, Fit.CAUTION, Fit.BAD), grouped.keys.toList())
        // Alphabetical by localized name, accents ignored: Corydoras panda, Néon bleu, Otocinclus.
        assertEquals(listOf("corydoras-panda", "neon", "otocinclus"), grouped.getValue(Fit.GOOD).map { it.species.id })
        assertEquals(listOf("guppy"), grouped.getValue(Fit.CAUTION).map { it.species.id })
        assertEquals(listOf("scalaire"), grouped.getValue(Fit.BAD).map { it.species.id })
        assertEquals(listOf(Fit.GOOD), groupedForAquarium(assessments.filter { it.fit == Fit.GOOD }, "fr").keys.toList())
        fixture.close()
    }

    @Test
    fun `a pH-only reason keeps « pH » as written at the start of the sentence`() = runTest {
        val fixture = Fixture()
        fixture.start()
        val ph = fixture.setup.parameters.single { it.parameter == BuiltInParameter.PH }
        val assessment = assessSpecies(species.first { it.id == "neon" }, fixture.setup, listOf(LatestReading(ph.id, 8.5, 0L)), emptyList())

        assertEquals(listOf(Reason.PH_TOO_HIGH), explanationReasons(assessment))
        assertEquals("pH trop haut", sentenceStart("pH trop haut"))
        assertEquals("pH-Wert zu hoch", sentenceStart("pH-Wert zu hoch"))
        assertEquals("Bac trop petit", sentenceStart("bac trop petit"))
        fixture.close()
    }

    @Test
    fun `adding uses the counter value and returns to the home`() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.start()
        viewModel.selectDestination(MainDestination.MORE)
        fixture.openLoaded()

        viewModel.openSpeciesFiche("otocinclus")
        assertEquals(6, viewModel.uiState.value.addInhabitant?.count)
        viewModel.changeInhabitantCount(8)
        viewModel.confirmAddInhabitant()

        val state = viewModel.uiState.first { it.addInhabitant == null }
        assertEquals(MainDestination.TANK, state.destination)
        val added = fixture.present().single()
        assertEquals("otocinclus", added.speciesId)
        assertEquals(8, added.count)
        assertEquals(DrawingFamily.CATFISH, added.family)
        fixture.close()
    }

    @Test
    fun `the counter never goes below one`() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.start()
        fixture.openLoaded()
        viewModel.openSpeciesFiche("betta")

        viewModel.changeInhabitantCount(0)

        assertEquals(1, viewModel.uiState.value.addInhabitant?.count)
        fixture.close()
    }

    @Test
    fun `"Ajouter quand même" on a BAD species still adds`() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.start()
        fixture.openLoaded()
        val scalaire = species.first { it.id == "scalaire" }
        assertEquals(Fit.BAD, assessSpecies(scalaire, fixture.setup, emptyList(), emptyList()).fit)

        viewModel.openSpeciesFiche("scalaire")
        viewModel.confirmAddInhabitant()

        viewModel.uiState.first { it.addInhabitant == null }
        assertEquals(listOf("scalaire" to 2), fixture.present().map { it.speciesId to it.count })
        fixture.close()
    }

    @Test
    fun `a custom species needs a name and a family`() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.start()
        fixture.openLoaded()
        viewModel.openCustomInhabitant()

        viewModel.confirmAddInhabitant()
        assertEquals(true, viewModel.uiState.value.addInhabitant?.showCustomError)
        viewModel.updateCustomInhabitantName("  Barbus de Sumatra ")
        viewModel.confirmAddInhabitant()
        assertEquals(true, viewModel.uiState.value.addInhabitant?.showCustomError)
        assertEquals(emptyList(), fixture.present())

        viewModel.selectCustomInhabitantFamily(DrawingFamily.CYPRINID)
        viewModel.changeInhabitantCount(5)
        viewModel.confirmAddInhabitant()

        viewModel.uiState.first { it.addInhabitant == null }
        val added = fixture.present().single()
        assertNull(added.speciesId)
        assertEquals("Barbus de Sumatra", added.customName)
        assertEquals(DrawingFamily.CYPRINID, added.family)
        assertEquals(5, added.count)
        fixture.close()
    }

    @Test
    fun `an unreadable encyclopedia gives an empty search and keeps the custom form`() = runTest {
        val fixture = Fixture(load = { error("broken file") })
        val viewModel = fixture.start()

        viewModel.openAddInhabitant()
        val state = assertNotNull(viewModel.uiState.first { it.addInhabitant?.species != null }.addInhabitant)

        assertEquals(emptyList(), state.species)
        assertTrue(state.loadFailed)
        viewModel.openCustomInhabitant()
        assertEquals(true, viewModel.uiState.value.addInhabitant?.custom)
        fixture.close()
    }

    @Test
    fun `back goes from the fiche to the search, then closes`() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.start()
        fixture.openLoaded()
        viewModel.openSpeciesFiche("neon")

        viewModel.backInAddInhabitant()
        assertNull(viewModel.uiState.value.addInhabitant?.ficheSpeciesId)
        assertNotNull(viewModel.uiState.value.addInhabitant)
        viewModel.backInAddInhabitant()
        assertNull(viewModel.uiState.value.addInhabitant)
        fixture.close()
    }

    @Test
    fun `removing part of a group leaves the rest, a stale removal reports instead of crashing`() = runTest {
        val fixture = Fixture()
        fixture.start()
        val group = fixture.repository.addInhabitant(fixture.setup.aquarium.id, "otocinclus", null, DrawingFamily.CATFISH, 6)

        fixture.viewModel.removeInhabitants(group.id, 2) { error("unexpected failure") }
        fixture.repository.observeInhabitants(fixture.setup.aquarium.id).first { it.singleOrNull()?.count == 4 }

        // Removing more than the group still holds (another device got there first) must not crash.
        val failed = CompletableDeferred<Unit>()
        fixture.viewModel.removeInhabitants(group.id, 5) { failed.complete(Unit) }
        withContext(Dispatchers.Default) { withTimeout(5_000) { failed.await() } }
        assertEquals(listOf(4), fixture.present().map { it.count })
        fixture.close()
    }

    @Test
    fun `the inhabitants count as loaded only once the displayed Aquarium's list arrives`() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.start()
        fixture.repository.addInhabitant(fixture.setup.aquarium.id, "otocinclus", null, DrawingFamily.CATFISH, 6)
        viewModel.uiState.first { it.inhabitantsLoaded && it.inhabitants.size == 1 }
        val other = fixture.repository.createConfiguredAquarium(
            "Bureau", 60.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED), AquariumCreationAccess.PRO,
        )
        viewModel.selectAquarium(other.aquarium.id)
        // Loaded for the new Aquarium means its own (empty) list, never the previous Aquarium's group.
        val loaded = viewModel.uiState.first { it.setup?.aquarium?.id == other.aquarium.id && it.inhabitantsLoaded }
        assertEquals(emptyList(), loaded.inhabitants)
        fixture.close()
    }
}
