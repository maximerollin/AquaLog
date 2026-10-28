package com.maximerollin.aqualog

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
@SuppressLint("NonObservableLocale")
actual fun currentLocalizedFormatters(): LocalizedFormatters {
    val configuration = LocalConfiguration.current
    return remember(configuration) { LocalizedFormatters(regionalFormatLocale()) }
}

private fun regionalFormatLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    Locale.getDefault(Locale.Category.FORMAT)
} else {
    Locale.getDefault()
}
