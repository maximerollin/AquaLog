package com.maximerollin.aqualog.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.encyclopedia.displayName
import com.maximerollin.aqualog.habitants.InhabitantCounter
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** « Habitants »: one row per present group with « Retirer… », then « Ajouter un habitant ». */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HabitantsSheet(
    groups: List<Inhabitant>,
    encyclopedia: List<Species>,
    language: String,
    onRemove: (inhabitantId: String, count: Int, onFailed: () -> Unit) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    var removing by remember { mutableStateOf<Inhabitant?>(null) }
    var failed by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CarnetSectionTitle(stringResource(Res.string.habitants_title))
            groups.forEach { group ->
                val (drawing, tint) = inhabitantDrawing(group, encyclopedia.firstOrNull { it.id == group.speciesId })
                val name = group.displayName(encyclopedia, language)
                // At large font sizes « Retirer… » wraps under the name rather than cutting it mid-word.
                FlowRow(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(drawing), contentDescription = null, modifier = Modifier.size(40.dp), colorFilter = tint?.let { ColorFilter.tint(it) })
                        Text(
                            stringResource(Res.string.habitants_present_group, name, group.count),
                            style = MaterialTheme.typography.bodyLarge,
                        )
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
            if (failed) {
                Text(
                    stringResource(Res.string.habitants_remove_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.habitants_add_one))
            }
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
