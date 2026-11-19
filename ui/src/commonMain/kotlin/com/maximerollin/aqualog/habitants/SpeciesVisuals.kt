package com.maximerollin.aqualog.habitants

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.habitants_fit_bad
import com.maximerollin.aqualog.resources.habitants_fit_caution
import com.maximerollin.aqualog.resources.habitants_fit_good
import com.maximerollin.aqualog.resources.habitants_pill_good
import com.maximerollin.aqualog.resources.habitants_pill_problems
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.encyclopedia.Assessment
import com.maximerollin.aqualog.shared.encyclopedia.CriterionStatus
import com.maximerollin.aqualog.shared.encyclopedia.Fit
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.CircleAlert
import com.maximerollin.aqualog.ui.icon.CircleCheck
import com.maximerollin.aqualog.ui.icon.CircleMinus
import com.maximerollin.aqualog.ui.icon.CircleX
import com.maximerollin.aqualog.ui.theme.levelContainerColor
import com.maximerollin.aqualog.ui.theme.levelTextColor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A species' drawing in a 56 x 44 dp tile. Silent: the row beside it says the name. */
@Composable
fun SpeciesThumb(drawing: DrawableResource, tint: Color?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width = 56.dp, height = 44.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(drawing), contentDescription = null, modifier = Modifier.width(40.dp),
            contentScale = ContentScale.Fit, colorFilter = tint?.let { ColorFilter.tint(it) },
        )
    }
}

/** The fit in words, in a pill: no symbol, the text is the semantics. */
@Composable
fun FitPill(text: String, fit: Fit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = when (fit) {
        Fit.GOOD -> colors.secondaryContainer
        Fit.CAUTION -> levelContainerColor(ReadingLevel.ATTENTION)
        Fit.BAD -> levelContainerColor(ReadingLevel.ALERT)
    } ?: colors.surfaceVariant
    Box(
        modifier.defaultMinSize(minHeight = 28.dp).background(fill, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fitColor(fit)) }
}

/** A criterion's status as a 24 dp mark. Decorative: the row's own semantics say the status in words. */
@Composable
fun FitMark(status: CriterionStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (icon, tint) = when (status) {
        CriterionStatus.OK -> AquagendaIcons.CircleCheck to colors.primary
        CriterionStatus.CAUTION -> AquagendaIcons.CircleAlert to (levelTextColor(ReadingLevel.ATTENTION) ?: colors.onSurface)
        CriterionStatus.PROBLEM -> AquagendaIcons.CircleX to (levelTextColor(ReadingLevel.ALERT) ?: colors.error)
        CriterionStatus.NOT_MEASURED, CriterionStatus.UNIT_NOT_COMPARED -> AquagendaIcons.CircleMinus to colors.onSurfaceVariant
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(24.dp))
}

/** The verdict's sentence for [aquariumName]: words only, the colour and mark come from [fitColor]. */
@Composable
fun fitVerdict(fit: Fit, aquariumName: String): String = stringResource(
    when (fit) {
        Fit.GOOD -> Res.string.habitants_fit_good
        Fit.CAUTION -> Res.string.habitants_fit_caution
        Fit.BAD -> Res.string.habitants_fit_bad
    },
    aquariumName,
)

/** « Compatible », the reason, or « N problèmes »: the pill always carries words. */
@Composable
fun fitPillText(assessment: Assessment): String {
    val reason = assessment.badgeReason?.label()
    return when {
        assessment.problems >= 2 -> pluralStringResource(Res.plurals.habitants_pill_problems, assessment.problems, assessment.problems)
        assessment.fit != Fit.GOOD && reason != null -> reason
        else -> stringResource(Res.string.habitants_pill_good)
    }
}
