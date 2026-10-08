package com.derekross.markview.core.render

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.derekross.markview.core.markdown.MdInline
import com.derekross.markview.core.render.engine.DiagramImage
import com.derekross.markview.core.render.engine.DiagramTheme
import com.derekross.markview.core.render.engine.MathSvg
import com.derekross.markview.core.render.engine.RenderEngine

// region Math

/** Parses MathJax SVG for AndroidSVG, baking in the text color (MathJax uses currentColor). */
@Composable
private fun rememberSvg(math: MathSvg, color: Color): SVG? = remember(math, color) {
    val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())
    runCatching {
        SVG.getFromString(math.svg.replace("currentColor", hex)).apply {
            // MathJax sizes the root in ex units; fill whatever box we draw into instead.
            setDocumentWidth("100%")
            setDocumentHeight("100%")
        }
    }.getOrNull()
}

/** Draws [math] filling this composable's bounds. */
@Composable
internal fun MathCanvas(math: MathSvg, color: Color, modifier: Modifier) {
    val svg = rememberSvg(math, color) ?: return
    val alpha = color.alpha
    Canvas(modifier.semantics { contentDescription = "Equation" }) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val save = if (alpha < 1f) native.saveLayerAlpha(0f, 0f, size.width, size.height, (alpha * 255).toInt()) else native.save()
            svg.renderToCanvas(native, RenderOptions.create().viewPort(0f, 0f, size.width, size.height))
            native.restoreToCount(save)
        }
    }
}

/** Distance of the math axis above the baseline, in em; inline formulas are centered on it. */
private const val MATH_AXIS_EM = 0.25f

/**
 * Inline formulas are placed with [PlaceholderVerticalAlign.TextCenter], which centers the
 * placeholder near the math axis. The box is padded symmetrically around the axis so the formula's
 * own baseline lines up with the text baseline.
 */
private class InlineMathBox(val math: MathSvg) {
    val half = maxOf(math.ascentEm - MATH_AXIS_EM, math.depthEm + MATH_AXIS_EM, 0.35f)
    val boxHeightEm = 2 * half
    val topOffsetEm = half + MATH_AXIS_EM - math.ascentEm
}

/** Collects every inline formula in [inlines], recursively. */
internal fun List<MdInline>.mathSources(): List<String> = buildList {
    fun walk(items: List<MdInline>) {
        for (item in items) when (item) {
            is MdInline.Math -> add(item.tex)
            is MdInline.Emphasis -> walk(item.children)
            is MdInline.Strong -> walk(item.children)
            is MdInline.Strikethrough -> walk(item.children)
            is MdInline.Link -> walk(item.children)
            else -> Unit
        }
    }
    walk(this@mathSources)
}

/** Typeset inline math for [inlines]; renders missing formulas in the background. */
@Composable
internal fun rememberInlineMath(inlines: List<MdInline>): Map<String, MathSvg> {
    val sources = remember(inlines) { inlines.mathSources().distinct() }
    if (sources.isEmpty()) return emptyMap()
    val rendered = remember(sources) {
        mutableStateMapOf<String, MathSvg>().apply {
            sources.forEach { tex -> RenderEngine.cachedMath(tex, display = false)?.let { put(tex, it) } }
        }
    }
    LaunchedEffect(sources) {
        for (tex in sources) {
            if (tex !in rendered) RenderEngine.renderMath(tex, display = false)?.let { rendered[tex] = it }
        }
    }
    return rendered
}

internal fun mathInlineId(tex: String) = "math:$tex"

/** Inline content entries for rendered formulas, keyed by [mathInlineId]. */
internal fun inlineMathContent(math: Map<String, MathSvg>, color: Color): Map<String, InlineTextContent> =
    math.entries.associate { (tex, svg) ->
        val box = InlineMathBox(svg)
        mathInlineId(tex) to InlineTextContent(
            Placeholder(width = svg.widthEm.em, height = box.boxHeightEm.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val emPx = maxHeight / box.boxHeightEm
                MathCanvas(
                    svg,
                    color,
                    Modifier
                        .padding(top = emPx * box.topOffsetEm)
                        .width(maxWidth)
                        .height(emPx * svg.heightEm),
                )
            }
        }
    }

@Composable
internal fun DisplayMathView(tex: String, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    var math by remember(tex) { mutableStateOf(RenderEngine.cachedMath(tex, display = true)) }
    var failed by remember(tex) { mutableStateOf(false) }
    LaunchedEffect(tex) {
        if (math == null) {
            math = RenderEngine.renderMath(tex, display = true)
            failed = math == null
        }
    }
    val density = LocalDensity.current
    // Display math is set a touch larger than body text, like most typesetting systems.
    val emDp = with(density) { (theme.body.fontSize * 1.12f).toDp() }
    Box(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val current = math
        when {
            current != null -> MathCanvas(
                current,
                theme.textColor,
                Modifier.padding(horizontal = 4.dp).size(emDp * current.widthEm, emDp * current.heightEm),
            )
            else -> Text(
                tex,
                style = theme.code.copy(fontStyle = FontStyle.Italic, color = if (failed) theme.extended.caution else theme.mutedColor),
                softWrap = false,
            )
        }
    }
}

// endregion

// region Mermaid

private fun Color.hex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

@Composable
private fun rememberDiagramTheme(): DiagramTheme {
    val c = MaterialTheme.colorScheme
    val ext = LocalMarkdownTheme.current.extended
    val density = LocalDensity.current.density
    return remember(c, ext, density) {
        DiagramTheme(
            dark = ext.isDark,
            scale = (density * 1.5f).coerceIn(2f, 4f),
            variables = mapOf(
                "background" to c.background.hex(),
                "primaryColor" to c.surfaceContainerHigh.hex(),
                "primaryTextColor" to c.onSurface.hex(),
                "primaryBorderColor" to c.primary.hex(),
                "secondaryColor" to c.secondaryContainer.hex(),
                "secondaryTextColor" to c.onSecondaryContainer.hex(),
                "secondaryBorderColor" to c.secondary.hex(),
                "tertiaryColor" to c.tertiaryContainer.hex(),
                "tertiaryTextColor" to c.onTertiaryContainer.hex(),
                "tertiaryBorderColor" to c.tertiary.hex(),
                "lineColor" to c.onSurfaceVariant.hex(),
                "textColor" to c.onSurface.hex(),
                "mainBkg" to c.surfaceContainerHigh.hex(),
                "nodeBorder" to c.primary.hex(),
                "clusterBkg" to c.surfaceContainerLow.hex(),
                "clusterBorder" to c.outlineVariant.hex(),
                "edgeLabelBackground" to c.background.hex(),
                "titleColor" to c.onSurface.hex(),
                "noteBkgColor" to c.tertiaryContainer.hex(),
                "noteTextColor" to c.onTertiaryContainer.hex(),
                "noteBorderColor" to c.tertiary.hex(),
                "actorBkg" to c.surfaceContainerHigh.hex(),
                "actorBorder" to c.primary.hex(),
                "actorTextColor" to c.onSurface.hex(),
                "actorLineColor" to c.outline.hex(),
                "signalColor" to c.onSurface.hex(),
                "signalTextColor" to c.onSurface.hex(),
                "labelBoxBkgColor" to c.surfaceContainerHigh.hex(),
                "labelTextColor" to c.onSurface.hex(),
                "pie1" to c.primary.hex(),
                "pie2" to c.tertiary.hex(),
                "pie3" to c.secondary.hex(),
                "pie4" to c.primaryContainer.hex(),
                "pie5" to c.tertiaryContainer.hex(),
                "pie6" to c.secondaryContainer.hex(),
                "pieTitleTextColor" to c.onSurface.hex(),
                "pieSectionTextColor" to c.onPrimary.hex(),
                "pieLegendTextColor" to c.onSurface.hex(),
                "pieStrokeColor" to c.background.hex(),
                "fontSize" to "15px",
            ),
        )
    }
}

private sealed interface DiagramState {
    data object Loading : DiagramState
    data class Ready(val image: DiagramImage) : DiagramState
    data class Failed(val message: String) : DiagramState
}

@Composable
internal fun MermaidView(source: String, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    val callbacks = LocalMarkdownCallbacks.current
    val diagramTheme = rememberDiagramTheme()
    var state by remember(source, diagramTheme) {
        mutableStateOf<DiagramState>(RenderEngine.cachedDiagram(source, diagramTheme)?.let { DiagramState.Ready(it) } ?: DiagramState.Loading)
    }
    LaunchedEffect(source, diagramTheme) {
        if (state is DiagramState.Ready) return@LaunchedEffect
        var error = "Couldn't render this diagram."
        val image = RenderEngine.renderDiagram(source, diagramTheme) { error = it }
        state = if (image != null) DiagramState.Ready(image) else DiagramState.Failed(error)
    }
    val shape = RoundedCornerShape(16.dp)
    AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "diagram", modifier = modifier.fillMaxWidth()) { s ->
        when (s) {
            DiagramState.Loading -> Box(
                Modifier.fillMaxWidth().height(180.dp).clip(shape).background(theme.codeBlockBackground.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Rendering diagram…", style = MaterialTheme.typography.labelMedium, color = theme.mutedColor)
            }
            is DiagramState.Ready -> BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = s.image
                val width = min(image.width.dp, maxWidth)
                Image(
                    bitmap = remember(image) { image.bitmap.asImageBitmap() },
                    contentDescription = "Diagram",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(width)
                        .aspectRatio(image.width / image.height)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { callbacks.onImageClick(image.file, "Diagram") },
                )
            }
            is DiagramState.Failed -> Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = theme.extended.caution, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Diagram error: ${s.message.lineSequence().firstOrNull().orEmpty()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.extended.caution,
                    )
                }
                CodeBlockView(source, "mermaid", SearchHighlight.None, Modifier)
            }
        }
    }
}

// endregion
