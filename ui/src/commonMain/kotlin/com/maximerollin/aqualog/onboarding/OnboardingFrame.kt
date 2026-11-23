package com.maximerollin.aqualog.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.maximerollin.aqualog.ParameterEditorState
import com.maximerollin.aqualog.StatusBarOverHeader
import com.maximerollin.aqualog.home.HeroText
import com.maximerollin.aqualog.home.TankScene
import com.maximerollin.aqualog.rememberReducedMotion
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.onboarding_step_of
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.parseDecimalInput
import com.maximerollin.aqualog.ui.theme.WaterHeaderShade
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import org.jetbrains.compose.resources.stringResource

private const val PreviewAquariumId = "onboarding-preview"

/** The tank the onboarding header draws while the Aquarium is still being typed: a bad volume keeps 0, never a crash. */
fun onboardingPreviewAquarium(name: String, volumeInput: String, unit: VolumeUnit, profile: AquariumProfile): Aquarium {
    val volume = parseDecimalInput(volumeInput)?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    return Aquarium(PreviewAquariumId, name, volume, unit, createdAtEpochMillis = 0L, profile = profile)
}

/** This editor row as a definition of the preview Aquarium, or null when the draft is invalid. */
fun ParameterEditorState.toPreviewDefinition(position: Int): ParameterDefinition? {
    val draft = toDraft(position) ?: return null
    return ParameterDefinition(
        id = draft.parameter.storageValue,
        aquariumId = PreviewAquariumId,
        parameter = draft.parameter,
        isActive = draft.isActive,
        position = draft.position,
        unit = draft.unit,
        precision = draft.precision,
        indicativeMinimum = draft.indicativeMinimum,
        indicativeMaximum = draft.indicativeMaximum,
    )
}

/**
 * The water of an onboarding screen: the tank scene under the header shade, the [OnboardingProgress] under the status bar when
 * [step] is given, then [content] at the bottom. The caller overlaps the paper that follows with overlapNext(30.dp).
 */
@Composable
fun OnboardingHeader(
    aquarium: Aquarium,
    minHeight: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
    step: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().heightIn(min = minHeight)) {
        TankScene(
            aquarium,
            inhabitants = emptyList(),
            modifier = Modifier.matchParentSize(),
            animate = animate && !rememberReducedMotion(),
        )
        Box(Modifier.matchParentSize().background(WaterHeaderShade))
        Column(Modifier.fillMaxWidth().heightIn(min = minHeight)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            if (step != null) OnboardingProgress(step, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), content = content)
            Spacer(Modifier.height(30.dp + 16.dp))
        }
    }
}

/** [total] segments, those up to [step] filled; one node for a screen reader. */
@Composable
fun OnboardingProgress(step: Int, total: Int = 5, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.onboarding_step_of, step, total)
    Row(
        modifier
            .fillMaxWidth()
            .testTag("onboarding-progress")
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat() / total, 0f..1f)
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(
                        if (index + 1 <= step) Color.White else Color.White.copy(alpha = .35f),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

/** The dots under the welcome title: a long first one, five short; decoration only. */
@Composable
fun WelcomeDots(modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("welcome-dots").clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(6) { index ->
            Box(
                Modifier
                    .size(width = if (index == 0) 22.dp else 6.dp, height = 6.dp)
                    .background(if (index == 0) Color.White else Color.White.copy(alpha = .45f), RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** The title (a heading) and optional subtitle on the water. */
@Composable
fun OnboardingTitle(title: String, subtitle: String?) {
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = frauncesFamily(), fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp,
        ),
        color = HeroText,
    )
    if (subtitle != null) {
        Text(
            subtitle,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = HeroText,
        )
    }
}

/** A scrolling screen with a fixed bottom bar: one 56 dp primary button (tagged [primaryTag]), and an optional [secondary] action under it. */
@Composable
fun OnboardingScaffold(
    listState: LazyListState,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondary: (@Composable () -> Unit)? = null,
    primaryTag: String = "onboarding-primary",
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        StatusBarOverHeader(listState)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("onboarding-list"), state = listState, content = content)
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                .imePadding(),
        ) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag(primaryTag),
            ) { Text(primaryLabel) }
            secondary?.invoke()
        }
    }
}
