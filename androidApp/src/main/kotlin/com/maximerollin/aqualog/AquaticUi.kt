package com.maximerollin.aqualog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Decorative identity only: the drawing never depends on Aquarium measurements. */
@Composable
internal fun AquariumArtwork(modifier: Modifier = Modifier) {
    val plant = MaterialTheme.colorScheme.primary
    val water = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
    val fish = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawOval(water, Offset(0f, h * .55f), Size(w, h * .45f))
        listOf(.58f, .76f, .91f).forEachIndexed { index, x ->
            val top = h * (.18f + index * .12f)
            drawLine(plant, Offset(w * x, h * .9f), Offset(w * (x - .09f), top), w * .022f)
            repeat(3) { leaf ->
                val y = top + leaf * h * .16f
                drawOval(plant.copy(alpha = .65f), Offset(w * (x - .22f + (leaf % 2) * .12f), y), Size(w * .17f, h * .12f))
            }
        }
        drawOval(fish, Offset(w * .12f, h * .4f), Size(w * .3f, h * .15f))
        drawPath(Path().apply {
            moveTo(w * .17f, h * .475f); lineTo(w * .04f, h * .37f)
            lineTo(w * .04f, h * .58f); close()
        }, fish)
        drawCircle(plant, w * .014f, Offset(w * .36f, h * .46f))
    }
}

@Composable
internal fun HomeMeasurements(state: HomeUiState) {
    val recorded = state.latestSession ?: return
    if (recorded.measurements.isEmpty()) return
    val formatter = currentLocalizedFormatters()
    val definitions = state.setup?.parameters.orEmpty().associateBy { it.id }
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.measurements_heading), style = MaterialTheme.typography.titleLarge)
        Text(formatter.dateTime(recorded.session.occurredAtEpochMillis),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        recorded.measurements.filter { it.parameterDefinitionId in definitions }.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { measurement ->
                    val definition = definitions.getValue(measurement.parameterDefinitionId)
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(definition.parameter.label(), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatter.number(measurement.value, definition.precision),
                                style = MaterialTheme.typography.headlineSmall)
                            if (definition.unit.isNotBlank()) Text(definition.unit, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (row.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}
