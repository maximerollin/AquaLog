package com.maximerollin.aqualog

import com.maximerollin.aqualog.resources.*
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
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeMeasurements(state: HomeUiState) {
    val recorded = state.latestSession ?: return
    if (recorded.measurements.isEmpty()) return
    val formatter = currentLocalizedFormatters()
    val definitions = state.setup?.parameters.orEmpty().associateBy { it.id }
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.measurements_heading), style = MaterialTheme.typography.titleLarge)
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
