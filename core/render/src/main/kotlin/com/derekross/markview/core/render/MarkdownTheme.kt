package com.derekross.markview.core.render

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.derekross.markview.core.designsystem.ExtendedColors
import com.derekross.markview.core.designsystem.LocalExtendedColors
import com.derekross.markview.core.designsystem.MarkviewFonts
import com.derekross.markview.core.designsystem.ReaderSettings
import com.derekross.markview.core.designsystem.ReaderTypeface

/** Every visual token the Markdown renderer uses. Derived from [MaterialTheme] + [ReaderSettings]. */
@Immutable
data class MarkdownTheme(
    val body: TextStyle,
    val headings: List<TextStyle>,
    val code: TextStyle,
    val codeFontFamily: FontFamily,
    val textColor: Color,
    val mutedColor: Color,
    val linkColor: Color,
    val accentColor: Color,
    val inlineCodeBackground: Color,
    val codeBlockBackground: Color,
    val dividerColor: Color,
    val searchHighlight: Color,
    val searchHighlightCurrent: Color,
    val blockSpacing: Dp,
    val extended: ExtendedColors,
) {
    fun heading(level: Int): TextStyle = headings[(level - 1).coerceIn(0, headings.lastIndex)]
}

val LocalMarkdownTheme = staticCompositionLocalOf<MarkdownTheme> { error("MarkdownTheme not provided") }

@Composable
fun rememberMarkdownTheme(settings: ReaderSettings): MarkdownTheme {
    val colors = MaterialTheme.colorScheme
    val extended = LocalExtendedColors.current
    return remember(settings.typeface, settings.textScale, settings.lineHeight, colors, extended) {
        val scale = settings.textScale.coerceIn(ReaderSettings.TextScaleRange)
        val lineHeight = settings.lineHeight.coerceIn(ReaderSettings.LineHeightRange)
        val (bodyFamily, headingFamily) = when (settings.typeface) {
            ReaderTypeface.Editorial -> MarkviewFonts.Literata to MarkviewFonts.Literata
            ReaderTypeface.Modern -> MarkviewFonts.Inter to MarkviewFonts.Inter
            ReaderTypeface.Technical -> MarkviewFonts.Inter to MarkviewFonts.JetBrainsMono
        }
        val bodySize = 17.sp * scale
        val body = TextStyle(
            fontFamily = bodyFamily,
            fontSize = bodySize,
            lineHeight = (lineHeight).em,
            color = colors.onBackground,
            lineBreak = LineBreak.Paragraph,
            textDirection = TextDirection.Content,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        fun heading(size: TextUnit, weight: FontWeight, tracking: TextUnit = 0.sp) = TextStyle(
            fontFamily = headingFamily,
            fontSize = size * scale,
            fontWeight = weight,
            lineHeight = 1.25.em,
            letterSpacing = tracking,
            color = colors.onBackground,
            lineBreak = LineBreak.Heading,
            textDirection = TextDirection.Content,
        )
        val tight = if (settings.typeface == ReaderTypeface.Technical) 0.sp else (-0.4).sp
        MarkdownTheme(
            body = body,
            headings = listOf(
                heading(32.sp, FontWeight.Bold, tight),
                heading(26.sp, FontWeight.Bold, tight),
                heading(22.sp, FontWeight.SemiBold),
                heading(19.sp, FontWeight.SemiBold),
                heading(17.sp, FontWeight.SemiBold),
                heading(15.sp, FontWeight.SemiBold).copy(color = colors.onSurfaceVariant),
            ),
            code = TextStyle(
                fontFamily = MarkviewFonts.JetBrainsMono,
                fontSize = 14.sp * scale,
                lineHeight = 1.55.em,
                color = colors.onSurface,
            ),
            codeFontFamily = MarkviewFonts.JetBrainsMono,
            textColor = colors.onBackground,
            mutedColor = colors.onSurfaceVariant,
            linkColor = colors.primary,
            accentColor = colors.primary,
            inlineCodeBackground = extended.codeBackground,
            codeBlockBackground = extended.codeBackground,
            dividerColor = colors.outlineVariant,
            searchHighlight = colors.tertiaryContainer,
            searchHighlightCurrent = colors.tertiary.copy(alpha = 0.55f),
            blockSpacing = (14 * scale).dp,
            extended = extended,
        )
    }
}
