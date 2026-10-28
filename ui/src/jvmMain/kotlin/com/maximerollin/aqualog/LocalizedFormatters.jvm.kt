package com.maximerollin.aqualog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Locale

@Composable
actual fun currentLocalizedFormatters(): LocalizedFormatters =
    remember { LocalizedFormatters(Locale.getDefault(Locale.Category.FORMAT)) }
