package com.derekross.markview.core.render.engine

import android.view.ViewGroup
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Attaches the render engine's WebView to the current window (1dp, invisible). Chromium only
 * reliably lays out and rasterizes for attached views, so place this in screens that render math
 * or diagrams.
 */
@Composable
fun RenderEngineHost(modifier: Modifier = Modifier) {
    RenderEngine.init(LocalContext.current)
    AndroidView(
        factory = {
            RenderEngine.ensureWebView().also { view -> (view.parent as? ViewGroup)?.removeView(view) }
        },
        modifier = modifier.size(1.dp).alpha(0f),
    )
}
