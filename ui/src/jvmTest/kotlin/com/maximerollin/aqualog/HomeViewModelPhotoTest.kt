package com.maximerollin.aqualog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.maximerollin.aqualog.photo.centredCrop
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumCreationAccess
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.DirectoryAquariumPhotoStore
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPhotoTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class Fixture(withPhotoStore: Boolean = true) {
        private val directory = Files.createTempDirectory("aqualog-photo-vm")
        private val database = createAquaLogDatabase(createJvmDatabaseBuilder(directory.resolve("aqualog.db").toString()))
        val repository = RoomAquariumRepository(
            database,
            { UUID.randomUUID().toString() },
            { 1_700_000_000_000L },
            photoStore = DirectoryAquariumPhotoStore(directory.resolve("photos").toFile()).takeIf { withPhotoStore },
        )
        lateinit var viewModel: HomeViewModel

        suspend fun aquarium(name: String) = repository.createConfiguredAquarium(
            name, 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED), AquariumCreationAccess.PRO,
        ).aquarium

        /** Starts the view model and waits until it shows [shown]. */
        suspend fun start(shown: Aquarium, savedState: SavedStateHandle = SavedStateHandle()): HomeViewModel {
            viewModel = HomeViewModel(repository, savedStateHandle = savedState)
            awaitShown(shown.id)
            return viewModel
        }

        suspend fun awaitShown(aquariumId: String) =
            viewModel.uiState.first { it.setup?.aquarium?.id == aquariumId && it.aquariums.isNotEmpty() }

        suspend fun awaitPhoto(predicate: (PhotoUiState) -> Boolean) = viewModel.uiState.first { predicate(it.photo) }.photo

        fun close() {
            viewModel.viewModelScope.cancel()
            database.close()
        }
    }

    private fun jpeg(width: Int = 400, height: Int = 300): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, (x * 255 / width) shl 16 or (y * 255 / height))
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    @Test
    fun `openPhoto without a photo shows the source sheet`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val viewModel = fixture.start(salon)

        viewModel.openPhoto()

        assertEquals(PhotoUiState.SourceSheet(salon.id), viewModel.uiState.value.photo)
        fixture.close()
    }

    @Test
    fun `openPhoto with a photo shows the viewer`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val photoId = assertNotNull(fixture.repository.setAquariumPhoto(salon.id, jpeg()).photoId)
        val viewModel = fixture.start(salon)
        viewModel.uiState.first { it.setup?.aquarium?.photoId == photoId }

        viewModel.openPhoto()

        assertEquals(PhotoUiState.Viewer(salon.id, photoId), viewModel.uiState.value.photo)
        fixture.close()
    }

    @Test
    fun `an unreadable picked file shows Unreadable and saves nothing`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val viewModel = fixture.start(salon)
        viewModel.openPhoto()

        viewModel.onPhotoPicked("not an image".encodeToByteArray())

        assertEquals(PhotoUiState.Unreadable(salon.id), fixture.awaitPhoto { it !is PhotoUiState.Processing })
        assertNull(fixture.repository.loadSetup(salon.id)!!.aquarium.photoId)
        fixture.close()
    }

    @Test
    fun `a cancelled pick returns to None`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val viewModel = fixture.start(salon)
        viewModel.openPhoto()

        viewModel.onPhotoPicked(null)

        assertEquals(PhotoUiState.None, viewModel.uiState.value.photo)
        fixture.close()
    }

    @Test
    fun `confirming the crop saves a JPEG for the Aquarium that was shown`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val bureau = fixture.aquarium("Bureau")
        fixture.repository.selectAquarium(salon.id)
        val viewModel = fixture.start(salon)
        viewModel.openPhoto()
        viewModel.onPhotoPicked(jpeg())
        val crop = assertIs<PhotoUiState.Crop>(fixture.awaitPhoto { it !is PhotoUiState.Processing })
        assertEquals(salon.id, crop.aquariumId)

        viewModel.selectAquarium(bureau.id)
        fixture.awaitShown(bureau.id)
        viewModel.confirmCrop(centredCrop(crop.image.width, crop.image.height))
        fixture.awaitPhoto { it == PhotoUiState.None }

        val photoId = assertNotNull(fixture.repository.loadSetup(salon.id)!!.aquarium.photoId)
        val bytes = assertNotNull(fixture.repository.readAquariumPhoto(photoId))
        assertEquals(listOf(0xFF, 0xD8, 0xFF), bytes.take(3).map { it.toInt() and 0xFF })
        assertNull(fixture.repository.loadSetup(bureau.id)!!.aquarium.photoId)
        fixture.close()
    }

    @Test
    fun `removing asks first, then clears`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val photoId = assertNotNull(fixture.repository.setAquariumPhoto(salon.id, jpeg()).photoId)
        val viewModel = fixture.start(salon)
        viewModel.uiState.first { it.setup?.aquarium?.photoId == photoId }
        viewModel.openPhoto()

        viewModel.askRemovePhoto()
        assertEquals(PhotoUiState.ConfirmRemove(salon.id), viewModel.uiState.value.photo)
        viewModel.removePhoto()
        fixture.awaitPhoto { it == PhotoUiState.None }

        assertNull(fixture.repository.loadSetup(salon.id)!!.aquarium.photoId)
        fixture.close()
    }

    @Test
    fun `aquariumPhoto decodes the stored photo, and nothing for an unknown id`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val photoId = assertNotNull(fixture.repository.setAquariumPhoto(salon.id, jpeg(400, 300)).photoId)
        val viewModel = fixture.start(salon)

        val image = assertNotNull(viewModel.aquariumPhoto(photoId))
        assertEquals(400 to 300, image.width to image.height)
        assertTrue(viewModel.aquariumPhoto(photoId) === image, "decoded once, then cached")
        assertNull(viewModel.aquariumPhoto("missing"))
        fixture.close()
    }

    @Test
    fun `a picker result after cancelPhoto is ignored`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val viewModel = fixture.start(salon)
        viewModel.openPhoto()
        viewModel.cancelPhoto()

        viewModel.onPhotoPicked(jpeg())

        assertEquals(PhotoUiState.None, viewModel.uiState.value.photo)
        fixture.close()
    }

    @Test
    fun `a save failure ends in SaveFailed`() = runTest {
        val fixture = Fixture(withPhotoStore = false)
        val salon = fixture.aquarium("Salon")
        val viewModel = fixture.start(salon)
        viewModel.openPhoto()
        viewModel.onPhotoPicked(jpeg())
        val crop = assertIs<PhotoUiState.Crop>(fixture.awaitPhoto { it !is PhotoUiState.Processing })

        viewModel.confirmCrop(centredCrop(crop.image.width, crop.image.height))

        assertEquals(PhotoUiState.SaveFailed(salon.id), fixture.awaitPhoto { it is PhotoUiState.SaveFailed })
        fixture.close()
    }

    @Test
    fun `a picker result reaches the crop after the process was recreated`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        val savedState = SavedStateHandle()
        fixture.start(salon, savedState).openPhoto()
        assertEquals(salon.id, savedState.get<String>(HomeViewModel.photoTargetKey))
        fixture.viewModel.viewModelScope.cancel()

        val restoredState = SavedStateHandle(mapOf(HomeViewModel.photoTargetKey to salon.id))
        val restored = fixture.start(salon, restoredState)
        restored.onPhotoPicked(jpeg())

        val crop = assertIs<PhotoUiState.Crop>(fixture.awaitPhoto { it !is PhotoUiState.Processing && it !is PhotoUiState.SourceSheet })
        assertEquals(salon.id, crop.aquariumId)
        assertNull(restoredState.get<String>(HomeViewModel.photoTargetKey), "only the source sheet is kept")
        fixture.close()
    }

    @Test
    fun `an Aquarium that leaves the list ends its photo flow`() = runTest {
        val fixture = Fixture()
        val salon = fixture.aquarium("Salon")
        fixture.aquarium("Bureau")
        fixture.repository.selectAquarium(salon.id)
        val viewModel = fixture.start(salon)
        viewModel.openPhoto()

        fixture.repository.archiveAquarium(salon.id)

        assertEquals(PhotoUiState.None, fixture.awaitPhoto { it == PhotoUiState.None })
        fixture.close()
    }
}
