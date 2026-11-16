package com.maximerollin.aqualog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * While in composition, the status-bar icons are light when [overWater] (they sit over the water), else they follow the
 * theme (dark on paper, spec §6); they revert to the theme on leaving.
 */
@Composable
expect fun StatusBarOverWater(overWater: Boolean)

/**
 * Whether the water is still what lies under the status bar: the hero is the list's first item and the sheet's top edge,
 * [heroHeightPx] down the list (the hero item's size, its overlap already removed), has not reached the status bar.
 * Null before the list has laid the hero out: the list then opens on the water.
 */
internal fun heroUnderStatusBar(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int, heroHeightPx: Int?, statusBarHeightPx: Int): Boolean =
    firstVisibleItemIndex == 0 &&
        if (heroHeightPx == null) firstVisibleItemScrollOffset == 0 else firstVisibleItemScrollOffset < heroHeightPx - statusBarHeightPx

/**
 * Light status-bar icons while the water is under them (spec §6); once the paper sheet scrolls up to the status bar the icons
 * turn dark again and a band of paper sits behind them, so the clock never reads white on cream.
 */
@Composable
fun StatusBarOverHeader(listState: LazyListState) {
    val statusBarPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    val overWater by remember(listState, statusBarPx) {
        derivedStateOf {
            heroUnderStatusBar(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size,
                statusBarPx,
            )
        }
    }
    StatusBarOverWater(overWater)
    if (!overWater) {
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
    }
}
