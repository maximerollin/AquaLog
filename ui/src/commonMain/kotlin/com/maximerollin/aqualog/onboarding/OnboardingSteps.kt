package com.maximerollin.aqualog.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.MeasureTile
import com.maximerollin.aqualog.MeasureTileRow
import com.maximerollin.aqualog.ParameterEditorState
import com.maximerollin.aqualog.PortableDataMessage
import com.maximerollin.aqualog.PortableDataUiState
import com.maximerollin.aqualog.StatusBarOverWater
import com.maximerollin.aqualog.currentLocalizedFormatters
import com.maximerollin.aqualog.formattedVolume
import com.maximerollin.aqualog.home.HeroText
import com.maximerollin.aqualog.home.TankScene
import com.maximerollin.aqualog.rememberReducedMotion
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.app_name
import com.maximerollin.aqualog.resources.aquarium_details_description
import com.maximerollin.aqualog.resources.aquarium_details_title
import com.maximerollin.aqualog.resources.aquarium_name_label
import com.maximerollin.aqualog.resources.aquarium_volume_label
import com.maximerollin.aqualog.resources.continue_to_aquarium
import com.maximerollin.aqualog.resources.continue_to_parameters
import com.maximerollin.aqualog.resources.get_started
import com.maximerollin.aqualog.resources.invalid_aquarium
import com.maximerollin.aqualog.resources.practice_description
import com.maximerollin.aqualog.resources.practice_not_saved
import com.maximerollin.aqualog.resources.practice_title
import com.maximerollin.aqualog.resources.profile_cycling
import com.maximerollin.aqualog.resources.profile_cycling_description
import com.maximerollin.aqualog.resources.profile_description
import com.maximerollin.aqualog.resources.profile_established
import com.maximerollin.aqualog.resources.profile_established_description
import com.maximerollin.aqualog.resources.profile_planted_shrimp
import com.maximerollin.aqualog.resources.profile_planted_shrimp_description
import com.maximerollin.aqualog.resources.profile_title
import com.maximerollin.aqualog.resources.restore_from_export
import com.maximerollin.aqualog.resources.see_free_and_pro
import com.maximerollin.aqualog.resources.unit_liters
import com.maximerollin.aqualog.resources.unit_us_gallons
import com.maximerollin.aqualog.resources.welcome_description
import com.maximerollin.aqualog.resources.welcome_title
import com.maximerollin.aqualog.sessionColumns
import com.maximerollin.aqualog.sessionImeAction
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Fish
import com.maximerollin.aqualog.ui.icon.FlaskConical
import com.maximerollin.aqualog.ui.icon.Leaf
import com.maximerollin.aqualog.ui.icon.Lock
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.PeriodSegmented
import com.maximerollin.aqualog.ui.theme.WaterPrimaryButtonText
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import com.maximerollin.aqualog.ui.theme.fullScreenBacking
import com.maximerollin.aqualog.ui.theme.overlapNext
import org.jetbrains.compose.resources.stringResource

/** What the onboarding screens ask of their ViewModel; every default does nothing, for tests. */
class OnboardingActions(
    val startOnboarding: () -> Unit = {},
    val requestImport: () -> Unit = {},
    val selectProfile: (AquariumProfile) -> Unit = {},
    val continueToAquarium: () -> Unit = {},
    val updateAquariumName: (String) -> Unit = {},
    val updateVolume: (String) -> Unit = {},
    val selectVolumeUnit: (VolumeUnit) -> Unit = {},
    val continueToParameters: () -> Unit = {},
    val setParameterActive: (BuiltInParameter, Boolean) -> Unit = { _, _ -> },
    val moveParameter: (BuiltInParameter, Int) -> Unit = { _, _ -> },
    val updateParameterUnit: (BuiltInParameter, String) -> Unit = { _, _ -> },
    val updateParameterPrecision: (BuiltInParameter, String) -> Unit = { _, _ -> },
    val updateParameterMinimum: (BuiltInParameter, String) -> Unit = { _, _ -> },
    val updateParameterMaximum: (BuiltInParameter, String) -> Unit = { _, _ -> },
    val continueToPractice: () -> Unit = {},
    val updatePracticeValue: (BuiltInParameter, String) -> Unit = { _, _ -> },
    val continueToPaywall: () -> Unit = {},
)

/** The ViewModel's onboarding calls as [OnboardingActions]; [onRequestImport] opens the import confirmation by default. */
fun HomeViewModel.onboardingActions(onRequestImport: () -> Unit = ::requestPortableImport): OnboardingActions =
    OnboardingActions(
        startOnboarding = ::startOnboarding,
        requestImport = onRequestImport,
        selectProfile = ::selectProfile,
        continueToAquarium = ::continueToAquarium,
        updateAquariumName = ::updateAquariumName,
        updateVolume = ::updateVolume,
        selectVolumeUnit = ::selectVolumeUnit,
        continueToParameters = ::continueToParameters,
        setParameterActive = ::setParameterActive,
        moveParameter = ::moveParameter,
        updateParameterUnit = ::updateParameterUnit,
        updateParameterPrecision = ::updateParameterPrecision,
        updateParameterMinimum = ::updateParameterMinimum,
        updateParameterMaximum = ::updateParameterMaximum,
        continueToPractice = ::continueToPractice,
        updatePracticeValue = ::updatePracticeValue,
        continueToPaywall = ::continueToPaywall,
    )

/** The first screen: a full-frame animated tank, then at the bottom the promise, « Commencer » and the restore. */
@Composable
fun WelcomeContent(portableData: PortableDataUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    StatusBarOverWater(true)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = maxHeight
        TankScene(
            onboardingPreviewAquarium("", "", VolumeUnit.LITERS, AquariumProfile.ESTABLISHED),
            inhabitants = emptyList(),
            modifier = Modifier.matchParentSize(),
            animate = !rememberReducedMotion(),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().heightIn(min = viewport), verticalArrangement = Arrangement.Bottom) {
                Column(
                    Modifier.fillMaxWidth().fullScreenBacking().navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 16.dp),
                ) {
                    Text(
                        stringResource(Res.string.app_name).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.78.sp),
                        color = Color.White,
                    )
                    Text(
                        stringResource(Res.string.welcome_title),
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                        style = TextStyle(
                            fontFamily = frauncesFamily(), fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 40.sp,
                        ),
                        color = Color.White,
                    )
                    Text(
                        stringResource(Res.string.welcome_description),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = Color.White,
                    )
                    PortableDataMessage(
                        portableData,
                        modifier = Modifier.padding(top = 10.dp),
                        colorOverride = Color.White,
                    )
                    WelcomeDots(Modifier.padding(top = 22.dp, bottom = 18.dp))
                    Button(
                        onClick = actions.startOnboarding,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WaterPrimaryButtonText),
                    ) { Text(stringResource(Res.string.get_started), fontWeight = FontWeight.SemiBold) }
                    TextButton(
                        onClick = actions.requestImport,
                        enabled = !portableData.isWorking,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = .6f),
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            stringResource(Res.string.restore_from_export),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Step 1: what the user follows, as three selectable cards under the water. */
@Composable
fun ProfileContent(selected: AquariumProfile, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    OnboardingScaffold(
        listState = listState,
        primaryLabel = stringResource(Res.string.continue_to_aquarium),
        onPrimary = actions.continueToAquarium,
        modifier = modifier,
    ) {
        item {
            OnboardingHeader(
                aquarium = onboardingPreviewAquarium("", "", VolumeUnit.LITERS, selected),
                minHeight = 300.dp,
                animate = true,
                modifier = Modifier.overlapNext(30.dp),
                step = 1,
            ) {
                OnboardingTitle(stringResource(Res.string.profile_title), stringResource(Res.string.profile_description))
            }
        }
        item { PaperSheetTop() }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProfileCard(
                    stringResource(Res.string.profile_established),
                    stringResource(Res.string.profile_established_description),
                    AquagendaIcons.Fish,
                    selected == AquariumProfile.ESTABLISHED,
                ) { actions.selectProfile(AquariumProfile.ESTABLISHED) }
                ProfileCard(
                    stringResource(Res.string.profile_cycling),
                    stringResource(Res.string.profile_cycling_description),
                    AquagendaIcons.FlaskConical,
                    selected == AquariumProfile.CYCLING,
                ) { actions.selectProfile(AquariumProfile.CYCLING) }
                ProfileCard(
                    stringResource(Res.string.profile_planted_shrimp),
                    stringResource(Res.string.profile_planted_shrimp_description),
                    AquagendaIcons.Leaf,
                    selected == AquariumProfile.PLANTED_SHRIMP,
                ) { actions.selectProfile(AquariumProfile.PLANTED_SHRIMP) }
            }
        }
    }
}

/** Step 2: the name and volume, typed under a header that shows them on the water as they are typed. */
@Composable
fun AquariumStepContent(
    name: String,
    volumeInput: String,
    unit: VolumeUnit,
    profile: AquariumProfile,
    showError: Boolean,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val preview = onboardingPreviewAquarium(name, volumeInput, unit, profile)
    val errorMessage = if (showError) stringResource(Res.string.invalid_aquarium) else null
    OnboardingScaffold(
        listState = listState,
        primaryLabel = stringResource(Res.string.continue_to_parameters),
        onPrimary = actions.continueToParameters,
        modifier = modifier,
    ) {
        item {
            OnboardingHeader(
                aquarium = preview,
                minHeight = 300.dp,
                animate = false,
                modifier = Modifier.overlapNext(30.dp),
                step = 2,
            ) {
                // One polite live node: a screen reader hears the change, not the name again on every key.
                Column(
                    Modifier.testTag("onboarding-aquarium-live").semantics(mergeDescendants = true) {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                ) {
                    Text(
                        stringResource(Res.string.aquarium_details_title),
                        fontSize = 13.sp,
                        color = HeroText,
                    )
                    val blank = name.isBlank()
                    Text(
                        if (blank) stringResource(Res.string.aquarium_name_label) else name,
                        modifier = Modifier.padding(top = 2.dp),
                        style = TextStyle(
                            fontFamily = frauncesFamily(), fontWeight = FontWeight.SemiBold,
                            fontSize = 36.sp, lineHeight = 40.sp,
                        ),
                        color = if (blank) HeroText.copy(alpha = .6f) else HeroText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (preview.volume > 0.0) {
                        Text(
                            preview.formattedVolume(),
                            modifier = Modifier.padding(top = 4.dp),
                            fontSize = 14.sp,
                            color = HeroText,
                        )
                    }
                }
            }
        }
        item { PaperSheetTop() }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledField(
                    value = name,
                    onValueChange = actions.updateAquariumName,
                    label = stringResource(Res.string.aquarium_name_label),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    isError = showError,
                    errorMessage = errorMessage,
                    valueStyle = TextStyle(fontFamily = frauncesFamily(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                    testTag = "aquarium-name",
                )
                FilledField(
                    value = volumeInput,
                    onValueChange = actions.updateVolume,
                    label = stringResource(Res.string.aquarium_volume_label),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    isError = showError,
                    errorMessage = errorMessage,
                    testTag = "aquarium-volume",
                )
                PeriodSegmented(
                    labels = listOf(stringResource(Res.string.unit_liters), stringResource(Res.string.unit_us_gallons)),
                    selectedIndex = if (unit == VolumeUnit.LITERS) 0 else 1,
                    onSelect = { actions.selectVolumeUnit(if (it == 0) VolumeUnit.LITERS else VolumeUnit.US_GALLONS) },
                )
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        AquagendaIcons.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(Res.string.aquarium_details_description),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (errorMessage != null) {
                    Text(errorMessage, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Step 4: the Session's tiles on three active Parameters, marked as training: nothing typed here is saved. */
@Composable
fun PracticeContent(
    parameters: List<ParameterEditorState>,
    values: Map<BuiltInParameter, String>,
    aquarium: Aquarium,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val formatter = currentLocalizedFormatters()
    val focusManager = LocalFocusManager.current
    val definitions = parameters.withIndex()
        .filter { it.value.isActive }
        .mapNotNull { (index, editor) -> editor.toPreviewDefinition(index) }
        .take(3)
    OnboardingScaffold(
        listState = listState,
        primaryLabel = stringResource(Res.string.see_free_and_pro),
        onPrimary = actions.continueToPaywall,
        modifier = modifier,
    ) {
        item {
            OnboardingHeader(
                aquarium = aquarium,
                minHeight = 170.dp,
                animate = false,
                modifier = Modifier.overlapNext(30.dp),
                step = 4,
            ) {
                OnboardingTitle(stringResource(Res.string.practice_title), aquarium.name)
            }
        }
        item { PaperSheetTop() }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(Res.string.practice_description),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(Res.string.practice_not_saved),
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = sessionColumns(LocalDensity.current.fontScale, maxWidth)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        definitions.chunked(columns).forEachIndexed { rowIndex, row ->
                            MeasureTileRow(
                                tiles = row.mapIndexed { column, definition ->
                                    val index = rowIndex * columns + column
                                    val tile: @Composable (Modifier) -> Unit = { tileModifier ->
                                        MeasureTile(
                                            definition = definition,
                                            input = values[definition.parameter].orEmpty(),
                                            profile = aquarium.profile,
                                            formatter = formatter,
                                            imeAction = sessionImeAction(index, definitions.lastIndex),
                                            onValueChange = { actions.updatePracticeValue(definition.parameter, it) },
                                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                            onDone = { focusManager.clearFocus() },
                                            modifier = tileModifier.testTag("practice-${definition.parameter.storageValue}"),
                                        )
                                    }
                                    tile
                                },
                                columns = columns,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(title: String, description: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = .15f), spotColor = Color.Black.copy(alpha = .15f))
                    .background(colors.surface, shape).border(BorderStroke(2.dp, colors.primary), shape)
                else Modifier.border(BorderStroke(1.dp, colors.outlineVariant), shape),
            )
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(if (selected) colors.primary else colors.secondaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) colors.onPrimary else colors.primary, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
