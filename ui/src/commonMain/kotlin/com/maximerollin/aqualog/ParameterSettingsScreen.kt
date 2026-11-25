package com.maximerollin.aqualog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.onboarding.OnboardingActions
import com.maximerollin.aqualog.onboarding.ParametersEditor
import com.maximerollin.aqualog.onboarding.RangeSheet
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.ui.icon.*
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.overlapNext
import org.jetbrains.compose.resources.stringResource

/** « Paramètres mesurés » of the current Aquarium: the onboarding editor, saved when the screen is left. */
@Composable
fun ParameterSettingsScreen(state: HomeUiState, aquarium: Aquarium, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    ParameterSettingsContent(
        aquarium = aquarium,
        parameters = state.parameters,
        showError = state.showParameterValidationError,
        actions = OnboardingActions(
            setParameterActive = viewModel::setParameterActive,
            moveParameter = viewModel::moveParameter,
            updateParameterUnit = viewModel::updateParameterUnit,
            updateParameterPrecision = viewModel::updateParameterPrecision,
            updateParameterMinimum = viewModel::updateParameterMinimum,
            updateParameterMaximum = viewModel::updateParameterMaximum,
        ),
        onClose = viewModel::closeParameterSettings,
        modifier = modifier,
    )
}

/** The stateless screen: [onClose] is Back, which saves; it stays open with [showError] while a row is invalid. */
@Composable
internal fun ParameterSettingsContent(
    aquarium: Aquarium,
    parameters: List<ParameterEditorState>,
    showError: Boolean,
    actions: OnboardingActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openRange by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("parameter-settings-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
        ) {
            item(key = "header") {
                WaterHeader(
                    aquarium = aquarium,
                    title = stringResource(Res.string.measured_parameters_title),
                    subtitle = aquarium.name,
                    leading = {
                        GlassIconButton(
                            AquagendaIcons.ArrowLeft, stringResource(Res.string.back), onClose,
                            Modifier.testTag("parameter-settings-back"),
                        )
                    },
                    modifier = Modifier.overlapNext(30.dp),
                )
            }
            item(key = "sheet-top") { PaperSheetTop() }
            item(key = "parameters") {
                ParametersEditor(parameters, showError, actions, onOpen = { openRange = it.storageValue })
            }
        }
        StatusBarOverHeader(listState)
    }
    parameters.firstOrNull { it.parameter.storageValue == openRange }?.let { state ->
        RangeSheet(state, actions, onDismiss = { openRange = null })
    }
}
