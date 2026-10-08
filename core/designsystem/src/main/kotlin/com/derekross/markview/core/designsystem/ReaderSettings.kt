package com.derekross.markview.core.designsystem

/** App-wide color theme. Sepia and Black are reader-focused variants. */
enum class AppTheme(val label: String) {
    System("System"), Light("Light"), Dark("Dark"), Sepia("Sepia"), Black("Black");
}

/** Typography presets for document content. */
enum class ReaderTypeface(val label: String, val description: String) {
    Editorial("Editorial", "Serif body for long-form reading"),
    Modern("Modern", "Clean sans for everyday docs"),
    Technical("Technical", "Sans with monospace accents for dev docs"),
}

/** User-tunable reading preferences. Values are clamped to the ranges below when applied. */
data class ReaderSettings(
    val theme: AppTheme = AppTheme.System,
    val dynamicColor: Boolean = true,
    val typeface: ReaderTypeface = ReaderTypeface.Editorial,
    /** Multiplier on the base body size (17sp). */
    val textScale: Float = 1f,
    /** Line height as a multiple of font size. */
    val lineHeight: Float = 1.6f,
    /** Maximum content width in dp; keeps line length comfortable on tablets. */
    val maxContentWidth: Int = 680,
    val keepScreenOn: Boolean = false,
) {
    companion object {
        val TextScaleRange = 0.8f..1.6f
        val LineHeightRange = 1.3f..2.0f
        val ContentWidthRange = 480..960
    }
}
