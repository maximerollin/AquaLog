package com.maximerollin.aqualog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.AquaLogTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        val application = application as AquaLogApplication
        HomeViewModel.factory(application.aquariumRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AquaLogTheme {
                AquaLogApp(homeViewModel)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AquaLogApp(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        when {
            uiState.isLoading -> LoadingContent(Modifier.padding(contentPadding))
            uiState.aquarium != null -> AquariumHome(
                aquarium = requireNotNull(uiState.aquarium),
                modifier = Modifier.padding(contentPadding),
            )
            else -> CreateAquariumForm(
                isSaving = uiState.isSaving,
                showValidationError = uiState.showValidationError,
                onSave = viewModel::createAquarium,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CreateAquariumForm(
    isSaving: Boolean,
    showValidationError: Boolean,
    onSave: (String, String, VolumeUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var volume by rememberSaveable { mutableStateOf("") }
    var volumeUnit by rememberSaveable { mutableStateOf(VolumeUnit.LITERS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.create_aquarium_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.create_aquarium_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.aquarium_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = volume,
            onValueChange = { volume = it },
            label = { Text(stringResource(R.string.aquarium_volume_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.volume_unit_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Column(Modifier.selectableGroup()) {
            VolumeUnitOption(
                text = stringResource(R.string.unit_liters),
                selected = volumeUnit == VolumeUnit.LITERS,
                onClick = { volumeUnit = VolumeUnit.LITERS },
            )
            VolumeUnitOption(
                text = stringResource(R.string.unit_us_gallons),
                selected = volumeUnit == VolumeUnit.US_GALLONS,
                onClick = { volumeUnit = VolumeUnit.US_GALLONS },
            )
        }
        if (showValidationError) {
            Text(
                text = stringResource(R.string.invalid_aquarium),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onSave(name, volume, volumeUnit) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.save_aquarium))
        }
    }
}

@Composable
private fun VolumeUnitOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun AquariumHome(
    aquarium: Aquarium,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(aquarium.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = when (aquarium.volumeUnit) {
                        VolumeUnit.LITERS -> stringResource(
                            R.string.volume_liters_format,
                            aquarium.volume.displayValue(),
                        )
                        VolumeUnit.US_GALLONS -> stringResource(
                            R.string.volume_us_gallons_format,
                            aquarium.volume.displayValue(),
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun Double.displayValue(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
