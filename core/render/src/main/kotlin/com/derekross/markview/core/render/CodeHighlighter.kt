package com.derekross.markview.core.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import java.util.concurrent.ConcurrentHashMap

/** Syntax highlighting for fenced code blocks, cached per (code, language, dark). */
object CodeHighlighter {
    private val cache = ConcurrentHashMap<Triple<String, String?, Boolean>, AnnotatedString>()

    private val aliases = mapOf(
        "c" to SyntaxLanguage.C, "h" to SyntaxLanguage.C,
        "cpp" to SyntaxLanguage.CPP, "c++" to SyntaxLanguage.CPP, "cc" to SyntaxLanguage.CPP, "hpp" to SyntaxLanguage.CPP,
        "dart" to SyntaxLanguage.DART,
        "java" to SyntaxLanguage.JAVA,
        "kotlin" to SyntaxLanguage.KOTLIN, "kt" to SyntaxLanguage.KOTLIN, "kts" to SyntaxLanguage.KOTLIN,
        "rust" to SyntaxLanguage.RUST, "rs" to SyntaxLanguage.RUST,
        "csharp" to SyntaxLanguage.CSHARP, "cs" to SyntaxLanguage.CSHARP, "c#" to SyntaxLanguage.CSHARP,
        "coffeescript" to SyntaxLanguage.COFFEESCRIPT, "coffee" to SyntaxLanguage.COFFEESCRIPT,
        "javascript" to SyntaxLanguage.JAVASCRIPT, "js" to SyntaxLanguage.JAVASCRIPT, "jsx" to SyntaxLanguage.JAVASCRIPT,
        "mjs" to SyntaxLanguage.JAVASCRIPT, "json" to SyntaxLanguage.JAVASCRIPT,
        "typescript" to SyntaxLanguage.TYPESCRIPT, "ts" to SyntaxLanguage.TYPESCRIPT, "tsx" to SyntaxLanguage.TYPESCRIPT,
        "perl" to SyntaxLanguage.PERL, "pl" to SyntaxLanguage.PERL,
        "python" to SyntaxLanguage.PYTHON, "py" to SyntaxLanguage.PYTHON,
        "ruby" to SyntaxLanguage.RUBY, "rb" to SyntaxLanguage.RUBY,
        "shell" to SyntaxLanguage.SHELL, "sh" to SyntaxLanguage.SHELL, "bash" to SyntaxLanguage.SHELL,
        "zsh" to SyntaxLanguage.SHELL, "console" to SyntaxLanguage.SHELL, "shellscript" to SyntaxLanguage.SHELL,
        "swift" to SyntaxLanguage.SWIFT,
        "go" to SyntaxLanguage.GO, "golang" to SyntaxLanguage.GO,
        "php" to SyntaxLanguage.PHP,
    )

    fun isSupported(language: String?): Boolean = language?.lowercase() in aliases

    fun highlight(code: String, language: String?, dark: Boolean): AnnotatedString {
        val lang = aliases[language?.lowercase()] ?: return AnnotatedString(code)
        // Keep the cache bounded; documents rarely have more than a few hundred code blocks.
        if (cache.size > 512) cache.clear()
        return cache.getOrPut(Triple(code, language, dark)) {
            runCatching {
                val highlights = Highlights.Builder()
                    .code(code)
                    .language(lang)
                    .theme(SyntaxThemes.atom(darkMode = dark))
                    .build()
                    .getHighlights()
                buildAnnotatedString {
                    append(code)
                    for (h in highlights) {
                        val start = h.location.start.coerceIn(0, code.length)
                        val end = h.location.end.coerceIn(start, code.length)
                        when (h) {
                            is ColorHighlight -> addStyle(SpanStyle(color = Color(0xFF000000.toInt() or h.rgb)), start, end)
                            is BoldHighlight -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                        }
                    }
                }
            }.getOrElse { AnnotatedString(code) }
        }
    }
}
