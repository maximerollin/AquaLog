package com.maximerollin.aqualog.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maximerollin.aqualog.HomeUiState
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.PhotoUiState
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.X
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Every step of the photo flow started from the home vignette; problems show in [snackbar]. */
@Composable
fun PhotoSheets(state: HomeUiState, viewModel: HomeViewModel, snackbar: SnackbarHostState) {
    // Launchers stay composed whatever the step, so a result coming back from the picker always has a receiver.
    val scope = rememberCoroutineScope()
    val gallery = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        scope.launch(Dispatchers.IO) { viewModel.onPhotoPicked(file?.photoBytes()) }
    }
    val camera = rememberCameraLauncher(viewModel::onPhotoPicked)
    val photo = state.photo
    val name = (photo as? PhotoUiState.Targeted)?.let { target -> state.aquariums.firstOrNull { it.id == target.aquariumId }?.name }.orEmpty()
    when (photo) {
        PhotoUiState.None -> Unit
        is PhotoUiState.SourceSheet -> SourceSheet(camera, gallery::launch, viewModel::cancelPhoto)
        is PhotoUiState.Processing -> Dialog(onDismissRequest = {}, DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            CircularProgressIndicator()
        }
        is PhotoUiState.Crop -> PhotoCropScreen(photo.image, viewModel::confirmCrop, viewModel::cancelPhoto)
        is PhotoUiState.Viewer -> PhotoViewer(
            name, photo.photoId, viewModel::aquariumPhoto, viewModel::choosePhotoSource, viewModel::askRemovePhoto, viewModel::cancelPhoto,
        )
        is PhotoUiState.ConfirmRemove -> AlertDialog(
            onDismissRequest = viewModel::cancelPhoto,
            title = { Text(stringResource(Res.string.photo_remove_title)) },
            text = { Text(stringResource(Res.string.photo_remove_text)) },
            confirmButton = { TextButton(onClick = viewModel::removePhoto) { Text(stringResource(Res.string.photo_remove_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelPhoto) { Text(stringResource(Res.string.cancel)) } },
        )
        is PhotoUiState.Unreadable, is PhotoUiState.SaveFailed -> {
            val message = stringResource(if (photo is PhotoUiState.Unreadable) Res.string.photo_unreadable else Res.string.photo_save_failed)
            LaunchedEffect(photo) {
                snackbar.showSnackbar(message)
                viewModel.cancelPhoto()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSheet(onTake: (() -> Unit)?, onChoose: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            onTake?.let { SheetRow(stringResource(Res.string.photo_take), it) }
            SheetRow(stringResource(Res.string.photo_choose), onChoose)
        }
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun PhotoViewer(
    aquariumName: String,
    photoId: String,
    loadPhoto: suspend (String) -> ImageBitmap?,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val image by produceState<ImageBitmap?>(null, photoId) { value = loadPhoto(photoId) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                    Icon(AquagendaIcons.X, contentDescription = stringResource(Res.string.close))
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    image?.let {
                        Image(
                            it,
                            contentDescription = stringResource(Res.string.photo_of, aquariumName),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.photo_remove))
                    }
                    Button(onClick = onChange, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.photo_change))
                    }
                }
            }
        }
    }
}
