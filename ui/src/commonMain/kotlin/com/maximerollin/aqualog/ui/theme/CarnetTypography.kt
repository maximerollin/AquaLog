package com.maximerollin.aqualog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.dm_sans_bold
import com.maximerollin.aqualog.resources.dm_sans_medium
import com.maximerollin.aqualog.resources.dm_sans_regular
import com.maximerollin.aqualog.resources.dm_sans_semibold
import com.maximerollin.aqualog.resources.fraunces_medium
import com.maximerollin.aqualog.resources.fraunces_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun frauncesFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_medium, FontWeight.Medium),
    Font(Res.font.fraunces_semibold, FontWeight.SemiBold),
)

@Composable
fun dmSansFamily(): FontFamily = FontFamily(
    Font(Res.font.dm_sans_regular, FontWeight.Normal),
    Font(Res.font.dm_sans_medium, FontWeight.Medium),
    Font(Res.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.dm_sans_bold, FontWeight.Bold),
)

// Fraunces for display/headline/title styles, DM Sans for everything else; Material's sizes are kept.
@Composable
fun carnetTypography(): Typography {
    val fraunces = frauncesFamily()
    val dmSans = dmSansFamily()
    val base = Typography()
    fun TextStyle.sans() = copy(fontFamily = dmSans)
    fun TextStyle.serif(weight: FontWeight) = copy(fontFamily = fraunces, fontWeight = weight)
    return base.copy(
        displayLarge = base.displayLarge.sans(),
        displayMedium = base.displayMedium.sans(),
        displaySmall = base.displaySmall.serif(FontWeight.Medium),
        headlineLarge = base.headlineLarge.sans(),
        headlineMedium = base.headlineMedium.sans(),
        headlineSmall = base.headlineSmall.serif(FontWeight.Medium),
        titleLarge = base.titleLarge.serif(FontWeight.Medium),
        titleMedium = base.titleMedium.serif(FontWeight.SemiBold),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(),
        labelMedium = base.labelMedium.sans(),
        labelSmall = base.labelSmall.sans(),
    )
}
