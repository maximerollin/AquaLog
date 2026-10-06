package com.maximerollin.aqualog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.VolumeUnit

@Composable
fun AquaLogContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingContent(modifier)
        state.rapidSession != null && state.setup != null -> RapidSessionScreen(
            state = state.rapidSession,
            aquarium = state.setup.aquarium,
            viewModel = viewModel,
            modifier = modifier,
        )
        state.setup != null -> AquariumShell(state, viewModel, modifier)
        state.step == OnboardingStep.WELCOME -> WelcomeScreen(viewModel::startOnboarding, modifier)
        state.step == OnboardingStep.PROFILE -> ProfileScreen(
            selected = state.profile,
            onSelected = viewModel::selectProfile,
            onContinue = viewModel::continueToAquarium,
            modifier = modifier,
        )
        state.step == OnboardingStep.AQUARIUM -> AquariumDetailsScreen(
            state = state,
            viewModel = viewModel,
            modifier = modifier,
        )
        state.step == OnboardingStep.PARAMETERS -> ParametersScreen(
            state = state,
            viewModel = viewModel,
            modifier = modifier,
        )
        state.step == OnboardingStep.PRACTICE -> PracticeScreen(
            state = state,
            onValueChanged = viewModel::updatePracticeValue,
            onContinue = viewModel::continueToPaywall,
            modifier = modifier,
        )
        else -> PaywallScreen(
            isSaving = state.isSaving,
            onContinueForFree = viewModel::finishForFree,
            modifier = modifier,
        )
    }
}

@Composable
private fun AquariumShell(state: HomeUiState, viewModel: HomeViewModel, modifier: Modifier) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { destination ->
                    val label = when (destination) {
                        MainDestination.HOME -> stringResource(R.string.navigation_home)
                        MainDestination.HISTORY -> stringResource(R.string.navigation_history)
                        MainDestination.SETTINGS -> stringResource(R.string.navigation_settings)
                    }
                    NavigationBarItem(
                        selected = state.destination == destination,
                        onClick = { viewModel.selectDestination(destination) },
                        icon = { Text(label.take(1)) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (state.destination) {
            MainDestination.HOME -> AquariumHome(state, viewModel, contentModifier)
            MainDestination.HISTORY -> TimelineScreen(state, viewModel, contentModifier)
            MainDestination.SETTINGS -> StandardScreen(contentModifier) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.settings_placeholder))
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit, modifier: Modifier) {
    StandardScreen(modifier) {
        Spacer(Modifier.weight(1f))
        ScreenHeading(R.string.welcome_title, R.string.welcome_description)
        Spacer(Modifier.weight(1f))
        PrimaryButton(R.string.get_started, onContinue)
    }
}

@Composable
private fun ProfileScreen(
    selected: AquariumProfile,
    onSelected: (AquariumProfile) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier,
) {
    StandardScreen(modifier) {
        ScreenHeading(R.string.profile_title, R.string.profile_description)
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileOption(
                title = stringResource(R.string.profile_established),
                description = stringResource(R.string.profile_established_description),
                selected = selected == AquariumProfile.ESTABLISHED,
                onClick = { onSelected(AquariumProfile.ESTABLISHED) },
            )
            ProfileOption(
                title = stringResource(R.string.profile_cycling),
                description = stringResource(R.string.profile_cycling_description),
                selected = selected == AquariumProfile.CYCLING,
                onClick = { onSelected(AquariumProfile.CYCLING) },
            )
            ProfileOption(
                title = stringResource(R.string.profile_planted_shrimp),
                description = stringResource(R.string.profile_planted_shrimp_description),
                selected = selected == AquariumProfile.PLANTED_SHRIMP,
                onClick = { onSelected(AquariumProfile.PLANTED_SHRIMP) },
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(R.string.continue_to_aquarium, onContinue)
    }
}

@Composable
private fun ProfileOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AquariumDetailsScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier,
) {
    StandardScreen(modifier) {
        ScreenHeading(R.string.aquarium_details_title, R.string.aquarium_details_description)
        OutlinedTextField(
            value = state.aquariumName,
            onValueChange = viewModel::updateAquariumName,
            label = { Text(stringResource(R.string.aquarium_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.volumeInput,
            onValueChange = viewModel::updateVolume,
            label = { Text(stringResource(R.string.aquarium_volume_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.volume_unit_label), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            VolumeUnitOption(
                text = stringResource(R.string.unit_liters),
                selected = state.volumeUnit == VolumeUnit.LITERS,
                onClick = { viewModel.selectVolumeUnit(VolumeUnit.LITERS) },
            )
            VolumeUnitOption(
                text = stringResource(R.string.unit_us_gallons),
                selected = state.volumeUnit == VolumeUnit.US_GALLONS,
                onClick = { viewModel.selectVolumeUnit(VolumeUnit.US_GALLONS) },
            )
        }
        if (state.showAquariumValidationError) {
            ErrorText(R.string.invalid_aquarium)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(R.string.continue_to_parameters, viewModel::continueToParameters)
    }
}

@Composable
private fun VolumeUnitOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun ParametersScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeading(R.string.parameters_title, R.string.parameters_description) }
        itemsIndexed(state.parameters, key = { _, item -> item.parameter.storageValue }) { index, item ->
            ParameterEditor(
                state = item,
                canMoveUp = index > 0,
                canMoveDown = index < state.parameters.lastIndex,
                viewModel = viewModel,
            )
        }
        if (state.showParameterValidationError) {
            item { ErrorText(R.string.invalid_parameters) }
        }
        item { PrimaryButton(R.string.try_mini_session, viewModel::continueToPractice) }
    }
}

@Composable
private fun ParameterEditor(
    state: ParameterEditorState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    viewModel: HomeViewModel,
) {
    val label = state.parameter.label()
    val moveUpDescription = stringResource(R.string.move_parameter_up, label)
    val moveDownDescription = stringResource(R.string.move_parameter_down, label)
    val enabledDescription = stringResource(R.string.enabled_for_parameter, label)
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { viewModel.moveParameter(state.parameter, -1) },
                    enabled = canMoveUp,
                    modifier = Modifier.semantics { contentDescription = moveUpDescription },
                ) { Text(stringResource(R.string.move_up_symbol)) }
                TextButton(
                    onClick = { viewModel.moveParameter(state.parameter, 1) },
                    enabled = canMoveDown,
                    modifier = Modifier.semantics { contentDescription = moveDownDescription },
                ) { Text(stringResource(R.string.move_down_symbol)) }
                Switch(
                    checked = state.isActive,
                    onCheckedChange = { viewModel.setParameterActive(state.parameter, it) },
                    modifier = Modifier.semantics { contentDescription = enabledDescription },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParameterTextField(
                    value = state.unit,
                    onValueChange = { viewModel.updateParameterUnit(state.parameter, it) },
                    label = stringResource(R.string.parameter_unit),
                    description = stringResource(R.string.unit_for_parameter, label),
                    modifier = Modifier.weight(1f),
                )
                ParameterTextField(
                    value = state.precision,
                    onValueChange = { viewModel.updateParameterPrecision(state.parameter, it) },
                    label = stringResource(R.string.parameter_precision),
                    description = stringResource(R.string.precision_for_parameter, label),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParameterTextField(
                    value = state.indicativeMinimum,
                    onValueChange = { viewModel.updateParameterMinimum(state.parameter, it) },
                    label = stringResource(R.string.parameter_minimum),
                    description = stringResource(R.string.minimum_for_parameter, label),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                ParameterTextField(
                    value = state.indicativeMaximum,
                    onValueChange = { viewModel.updateParameterMaximum(state.parameter, it) },
                    label = stringResource(R.string.parameter_maximum),
                    description = stringResource(R.string.maximum_for_parameter, label),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ParameterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    description: String,
    modifier: Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun PracticeScreen(
    state: HomeUiState,
    onValueChanged: (BuiltInParameter, String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier,
) {
    val activeParameters = state.parameters.filter(ParameterEditorState::isActive).take(3)
    StandardScreen(modifier) {
        ScreenHeading(R.string.practice_title, R.string.practice_description)
        activeParameters.forEach { parameter ->
            val label = parameter.parameter.label()
            val practiceDescription = stringResource(R.string.practice_value_for_parameter, label)
            OutlinedTextField(
                value = state.practiceValues[parameter.parameter].orEmpty(),
                onValueChange = { onValueChanged(parameter.parameter, it) },
                label = { Text("$label (${parameter.unit})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = practiceDescription },
            )
        }
        Text(
            stringResource(R.string.practice_not_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(R.string.see_free_and_pro, onContinue)
    }
}

@Composable
private fun PaywallScreen(
    isSaving: Boolean,
    onContinueForFree: () -> Unit,
    modifier: Modifier,
) {
    StandardScreen(modifier) {
        ScreenHeading(R.string.paywall_title, R.string.paywall_description)
        PlanCard(R.string.free_plan, R.string.free_plan_description)
        PlanCard(R.string.pro_plan, R.string.pro_plan_description)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onContinueForFree,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isSaving) CircularProgressIndicator() else Text(stringResource(R.string.continue_for_free))
        }
    }
}

@Composable
private fun PlanCard(title: Int, description: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AquariumHome(state: HomeUiState, viewModel: HomeViewModel, modifier: Modifier) {
    val aquarium = requireNotNull(state.setup).aquarium
    StandardScreen(modifier) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.aquarium_ready),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(aquarium.name, style = MaterialTheme.typography.titleLarge)
                Text(aquarium.formattedVolume(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (state.latestSession == null) R.string.no_session_yet else R.string.session_saved,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        if (state.latestSession == null) {
                            R.string.no_session_description
                        } else {
                            R.string.session_saved_description
                        },
                        state.latestSession?.measurements?.size ?: 0,
                        state.latestSession?.maintenanceActions?.size ?: 0,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(R.string.new_session, viewModel::openRapidSession)
    }
}

@Composable
private fun StandardScreen(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun ScreenHeading(title: Int, description: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrimaryButton(label: Int, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(stringResource(label))
    }
}

@Composable
private fun ErrorText(message: Int) {
    Text(
        stringResource(message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun BuiltInParameter.label(): String = stringResource(
    when (this) {
        BuiltInParameter.TEMPERATURE -> R.string.parameter_temperature
        BuiltInParameter.PH -> R.string.parameter_ph
        BuiltInParameter.AMMONIA -> R.string.parameter_ammonia
        BuiltInParameter.NITRITE -> R.string.parameter_nitrite
        BuiltInParameter.NITRATE -> R.string.parameter_nitrate
        BuiltInParameter.GH -> R.string.parameter_gh
        BuiltInParameter.KH -> R.string.parameter_kh
        BuiltInParameter.TDS -> R.string.parameter_tds
        BuiltInParameter.PHOSPHATE -> R.string.parameter_phosphate
        BuiltInParameter.SALINITY -> R.string.parameter_salinity
    },
)

@Composable
private fun Aquarium.formattedVolume(): String = when (volumeUnit) {
    VolumeUnit.LITERS -> stringResource(R.string.volume_liters_format, volume.displayValue())
    VolumeUnit.US_GALLONS -> stringResource(R.string.volume_us_gallons_format, volume.displayValue())
}

private fun Double.displayValue(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
