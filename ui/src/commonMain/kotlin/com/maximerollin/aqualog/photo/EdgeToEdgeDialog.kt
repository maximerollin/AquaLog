package com.maximerollin.aqualog.photo

import androidx.compose.ui.window.DialogProperties

/**
 * A full-screen dialog drawn under the system bars, so its backdrop reaches every edge; the content keeps clear of the bars
 * itself (`safeDrawingPadding`). The common [DialogProperties] has no such option: Android calls it `decorFitsSystemWindows`,
 * desktop `usePlatformInsets`.
 */
internal expect fun edgeToEdgeDialogProperties(): DialogProperties
