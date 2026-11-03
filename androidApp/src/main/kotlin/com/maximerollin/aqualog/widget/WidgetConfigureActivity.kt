package com.maximerollin.aqualog.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.maximerollin.aqualog.AquaLogApplication
import com.maximerollin.aqualog.R
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import kotlinx.coroutines.launch

/** Pins the Aquarium a small or medium widget shows; canceled (widget kept as is) unless one is picked. */
class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val repository = (application as AquaLogApplication).aquariumRepository
        setContent {
            AquaLogTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val aquariums by repository.observeAquariums().collectAsState(initial = emptyList())
                    LazyColumn(Modifier.systemBarsPadding(), contentPadding = PaddingValues(16.dp)) {
                        item {
                            Text(
                                stringResource(R.string.widget_configure_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        items(aquariums, key = { it.id }) { aquarium ->
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable(role = Role.Button) { pin(widgetId, aquarium.id) },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    aquarium.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pin(widgetId: Int, aquariumId: String) {
        lifecycleScope.launch {
            updateAppWidgetState(applicationContext, GlanceAppWidgetManager(applicationContext).getGlanceIdBy(widgetId)) { it[PINNED_AQUARIUM_ID] = aquariumId }
            updateAllWidgets(applicationContext)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}
