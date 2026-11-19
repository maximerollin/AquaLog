package com.maximerollin.aqualog.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.encyclopedia.displayName
import com.maximerollin.aqualog.habitants.InhabitantCounter
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.encyclopedia.Species
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.habitants.SpeciesThumb
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Plus
import com.maximerollin.aqualog.ui.theme.LargeFontScale
import com.maximerollin.aqualog.ui.theme.frauncesFamily

/** « Habitants »: one row per present group with « Retirer… », then « Ajouter un habitant ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitantsSheet(
    groups: List<Inhabitant>,
    encyclopedia: List<Species>,
    language: String,
    aquariumName: String,
    onRemove: (inhabitantId: String, count: Int, onFailed: () -> Unit) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        HabitantsSheetBody(groups, encyclopedia, language, aquariumName, onRemove, onAdd)
    }
}

/** The sheet's content and its remove dialog, apart from the [ModalBottomSheet] so a JVM test can render it. */
@Composable
internal fun HabitantsSheetBody(
    groups: List<Inhabitant>,
    encyclopedia: List<Species>,
    language: String,
    aquariumName: String,
    onRemove: (inhabitantId: String, count: Int, onFailed: () -> Unit) -> Unit,
    onAdd: () -> Unit,
) {
    var removing by remember { mutableStateOf<Inhabitant?>(null) }
    var failed by remember { mutableStateOf(false) }
    val total = groups.sumOf { it.count }
    Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        val title: @Composable () -> Unit = {
            Text(
                stringResource(Res.string.habitants_title),
                modifier = Modifier.semantics { heading() },
                style = TextStyle(fontFamily = frauncesFamily(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val count: @Composable () -> Unit = {
            Text(
                stringResource(Res.string.habitants_count_in_aquarium, total, aquariumName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // At large font sizes the count goes under the title rather than squeezing it.
        if (LocalDensity.current.fontScale >= LargeFontScale) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                title()
                count()
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                title()
                count()
            }
        }
        groups.forEachIndexed { index, group ->
            val species = encyclopedia.firstOrNull { it.id == group.speciesId }
            val (drawing, tint) = inhabitantDrawing(group, species)
            val name = group.displayName(encyclopedia, language)
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpeciesThumb(drawing, tint)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.habitants_present_group, name, group.count),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    if (species != null) {
                        Text(
                            species.scientificName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val removeLabel = stringResource(Res.string.habitants_remove_named, name)
                TextButton(
                    onClick = { removing = group },
                    // The visible « Retirer… » is the same on every row: TalkBack hears which group.
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = removeLabel },
                ) {
                    Text(stringResource(Res.string.habitants_remove))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (failed) {
            Text(
                stringResource(Res.string.habitants_remove_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) {
            Icon(AquagendaIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.habitants_add_one))
        }
    }
    removing?.let { group ->
        var count by remember(group.id) { mutableIntStateOf(1) }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.habitants_remove_title)) },
            text = {
                Column {
                    Text(group.displayName(encyclopedia, language), style = MaterialTheme.typography.bodyLarge)
                    InhabitantCounter(count, { count = it.coerceIn(1, group.count) }, max = group.count)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        failed = false
                        onRemove(group.id, count) { failed = true }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.habitants_remove_confirm, count)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
