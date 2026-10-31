package com.maximerollin.aqualog.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            modifier = Modifier.weight(1f),
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

/** A measurement: big number, small unit, label below, optional age line. */
@Composable
fun ReadingValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    attention: Boolean = false,
    age: String? = null,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = if (attention) attentionTextColor() else MaterialTheme.colorScheme.onSurface,
            )
            if (unit != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (age != null) {
            Text(
                age,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CarnetDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
