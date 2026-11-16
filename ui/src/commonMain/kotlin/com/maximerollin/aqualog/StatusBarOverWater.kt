package com.maximerollin.aqualog

import androidx.compose.runtime.Composable

/**
 * While in composition, the status-bar icons are light when [overWater] (they sit over the water), else they follow the
 * theme (dark on paper, spec §6); they revert to the theme on leaving.
 */
@Composable
expect fun StatusBarOverWater(overWater: Boolean)
