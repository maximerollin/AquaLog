package com.maximerollin.aqualog.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Re-renders every placed widget; cheap when none is placed. */
suspend fun updateAllWidgets(context: Context) {
    SmallTankWidget().updateAll(context)
    MediumTankWidget().updateAll(context)
    TanksWidget().updateAll(context)
}
