package com.maximerollin.aqualog.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.Trend
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.reading_alert_description
import com.maximerollin.aqualog.resources.reading_attention_description
import com.maximerollin.aqualog.resources.trend_down
import com.maximerollin.aqualog.resources.trend_flat
import com.maximerollin.aqualog.resources.trend_up
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.ui.icon.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Screen header: Fraunces title, muted context line, optional trailing action. */
@Composable
fun CarnetScreenTitle(
    title: String,
    context: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (context != null) {
                Text(
                    context,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/** Section heading in primary, with an optional text action on the right (48 dp target). */
@Composable
fun CarnetSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The words that announce a level, since colour alone must not carry it; null for OK. */
@Composable
fun levelDescription(level: ReadingLevel): String? = when (level) {
    ReadingLevel.OK -> null
    ReadingLevel.ATTENTION -> stringResource(Res.string.reading_attention_description)
    ReadingLevel.ALERT -> stringResource(Res.string.reading_alert_description)
}

/** The arrow of a [Trend] and the words that say it, since an arrow alone must not carry it. */
internal fun trendIcon(trend: Trend): Pair<ImageVector, StringResource> = when (trend) {
    Trend.UP -> AquagendaIcons.ArrowUp to Res.string.trend_up
    Trend.DOWN -> AquagendaIcons.ArrowDown to Res.string.trend_down
    Trend.FLAT -> AquagendaIcons.ArrowRight to Res.string.trend_flat
}

@Composable
fun CarnetDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
