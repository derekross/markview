package com.derekross.markview.core.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Bundled variable fonts (SIL OFL). No downloadable fonts, so the app works without Play Services. */
object MarkviewFonts {
    private val weights = listOf(
        FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
        FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
    )

    @OptIn(ExperimentalTextApi::class)
    private fun variable(upright: Int, italic: Int?): FontFamily = FontFamily(
        weights.flatMap { weight ->
            listOfNotNull(
                Font(upright, weight, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))),
                italic?.let { Font(it, weight, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))) },
            )
        },
    )

    /** Literata: a serif designed for long-form screen reading. */
    val Literata: FontFamily = variable(R.font.literata, R.font.literata_italic)

    /** Inter: a neutral, highly legible UI sans. */
    val Inter: FontFamily = variable(R.font.inter, R.font.inter_italic)

    /** JetBrains Mono: code, with clear glyph distinctions. */
    val JetBrainsMono: FontFamily = variable(R.font.jetbrains_mono, null)
}
