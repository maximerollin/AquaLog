package com.maximerollin.aqualog.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlin.coroutines.cancellation.CancellationException

/**
 * Re-renders every placed widget; cheap when none is placed. Never throws: a widget that could not refresh keeps
 * what it shows until the next update.
 */
suspend fun updateAllWidgets(context: Context) {
    try {
        val manager = GlanceAppWidgetManager(context)
        for (widget in listOf(SmallTankWidget(), MediumTankWidget(), TanksWidget())) {
            for (id in manager.getGlanceIds(widget.javaClass)) {
                // A running session only recomposes from its Glance state: the bump makes it reload (liveWidgetContent).
                updateAppWidgetState(context, id) { it[REFRESH_COUNT] = (it[REFRESH_COUNT] ?: 0L) + 1 }
                widget.update(context, id)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
    }
}
