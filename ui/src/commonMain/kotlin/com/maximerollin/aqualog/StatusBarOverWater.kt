package com.maximerollin.aqualog

import androidx.compose.runtime.Composable

/** While in composition, the status-bar icons are light (they sit over the water); they revert on leaving. */
@Composable
expect fun StatusBarOverWater()
