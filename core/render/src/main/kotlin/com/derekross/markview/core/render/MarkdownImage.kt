package com.derekross.markview.core.render

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.svg.SvgDecoder

/** Shared image loader with SVG support (README badges are usually SVG). */
object MarkviewImageLoader {
    fun create(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()
}

/**
 * An image that sizes itself like a browser would: intrinsic pixels map to dp (CSS px),
 * capped at the available width. Large photos therefore fill the column; badges stay small.
 */
@Composable
internal fun MarkdownImage(
    url: String,
    alt: String,
    link: String?,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
    maxInlineHeight: Dp = 28.dp,
) {
    val callbacks = LocalMarkdownCallbacks.current
    val model = remember(url) { callbacks.resolveImage(url) } ?: url
    val context = LocalContext.current
    val density = LocalDensity.current
    var intrinsic by remember(model) { mutableStateOf<androidx.compose.ui.geometry.Size?>(null) }
    var failed by remember(model) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (inline) 4.dp else 14.dp)
    val onClick = {
        if (link != null) callbacks.onLinkClick(link) else callbacks.onImageClick(model, alt)
    }

    BoxWithConstraints(modifier = if (inline) modifier else modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val request = remember(model, maxWidthPx) {
            ImageRequest.Builder(context)
                .data(model)
                .size(coil3.size.Size(Dimension(maxWidthPx), Dimension.Undefined))
                .build()
        }
        val size = intrinsic
        val sizing = when {
            failed -> Modifier.size(if (inline) maxInlineHeight else 96.dp)
            size == null || size.width <= 0f || size.height <= 0f ->
                if (inline) Modifier.height(maxInlineHeight).width(maxInlineHeight * 3) else Modifier.fillMaxWidth().height(200.dp)
            else -> {
                // Treat decoded pixels as CSS pixels (dp), never wider than the column.
                var width = min(size.width.dp, maxWidth)
                if (inline) width = min(width, maxInlineHeight * (size.width / size.height))
                Modifier.width(width).aspectRatio(size.width / size.height)
            }
        }
        if (failed) {
            Box(
                Modifier.then(sizing).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .semantics { contentDescription = alt.ifBlank { "Image failed to load" } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AsyncImage(
                model = request,
                contentDescription = alt.ifBlank { null },
                contentScale = ContentScale.Fit,
                onSuccess = { intrinsic = it.painter.intrinsicSize },
                onError = { failed = true },
                modifier = sizing
                    .clip(shape)
                    .then(if (size == null) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow) else Modifier)
                    .clickable(onClick = onClick),
            )
        }
    }
}
