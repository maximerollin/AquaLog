package com.maximerollin.aqualog.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Lucide `circle-x` (ISC licence). */
@Suppress("UnusedReceiverParameter")
val AquagendaIcons.CircleX: ImageVector
    get() {
        if (_CircleX != null) {
            return _CircleX!!
        }
        _CircleX = ImageVector.Builder(
            name = "CircleX",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 22f, 12f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 9f)
                lineTo(9f, 15f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 9f)
                lineTo(15f, 15f)
            }
        }.build()

        return _CircleX!!
    }

@Suppress("ObjectPropertyName")
private var _CircleX: ImageVector? = null
