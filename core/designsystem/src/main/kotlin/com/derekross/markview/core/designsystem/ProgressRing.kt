package com.derekross.markview.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Circular reading-progress indicator with the percentage in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    stroke: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    showLabel: Boolean = true,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    val percent = (progress.coerceIn(0f, 1f) * 100).roundToInt()
    Box(modifier.size(size).semantics { contentDescription = "$percent percent read" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val width = stroke.toPx()
            val style = Stroke(width = width, cap = StrokeCap.Round)
            val inset = width / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - width, this.size.height - width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = style)
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = style)
        }
        if (showLabel) Text("$percent", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
