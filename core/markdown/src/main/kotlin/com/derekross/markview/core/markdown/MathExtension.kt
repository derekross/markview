package com.derekross.markview.core.markdown

import org.commonmark.Extension
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.parser.Parser
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState
import org.commonmark.parser.SourceLine

/** Inline `$…$` math. */
class InlineMath(val tex: String) : CustomNode()

/** Display `$$ … $$` math. */
class DisplayMath : CustomBlock() {
    var tex: String = ""
}

/**
 * Adds `$inline$` and `$$display$$` math parsing.
 *
 * Inline rules follow the common Pandoc/GitHub convention so prices like "$5 and $10" stay text:
 * the opening `$` must not be followed by whitespace, the closing `$` must not be preceded by
 * whitespace nor followed by a digit.
 */
class MathExtension private constructor() : Parser.ParserExtension {
    override fun extend(builder: Parser.Builder) {
        builder.customBlockParserFactory(DisplayMathParser.Factory())
        builder.customInlineContentParserFactory(InlineMathParser.Factory())
    }

    companion object {
        fun create(): Extension = MathExtension()
    }
}

private class InlineMathParser : InlineContentParser {
    override fun tryParse(inlineParserState: InlineParserState): ParsedInline? {
        val scanner = inlineParserState.scanner()
        // Opening delimiter: exactly one '$' (a run of two is display math written inline).
        val openCount = scanner.matchMultiple('$')
        if (openCount != 1) return ParsedInline.none()
        val contentStart = scanner.position()
        val first = scanner.peek()
        if (first == ' ' || first == '\t' || first == '\n' || first == org.commonmark.parser.beta.Scanner.END) {
            return ParsedInline.none()
        }
        while (scanner.hasNext()) {
            val c = scanner.peek()
            if (c == '\\') {
                scanner.next()
                if (scanner.hasNext()) scanner.next()
                continue
            }
            if (c == '$') {
                val contentEnd = scanner.position()
                val tex = scanner.getSource(contentStart, contentEnd).content
                scanner.next()
                val after = scanner.peek()
                if (tex.isEmpty() || tex.last().isWhitespace() || after.isDigit()) return ParsedInline.none()
                return ParsedInline.of(InlineMath(tex), scanner.position())
            }
            scanner.next()
        }
        return ParsedInline.none()
    }

    class Factory : InlineContentParserFactory {
        override fun getTriggerCharacters(): Set<Char> = setOf('$')
        override fun create(): InlineContentParser = InlineMathParser()
    }
}

private class DisplayMathParser(firstLineRest: String) : AbstractBlockParser() {
    private val block = DisplayMath()
    private val lines = mutableListOf<String>()
    private var finished = false

    init {
        // Support single-line `$$ x $$` as well as the opening line carrying content.
        val rest = firstLineRest.trim()
        if (rest.endsWith("$$") && rest.length >= 2) {
            lines += rest.removeSuffix("$$")
            finished = true
        } else if (rest.isNotEmpty()) {
            lines += rest
        }
    }

    override fun getBlock() = block

    override fun tryContinue(parserState: ParserState): BlockContinue? {
        if (finished) return BlockContinue.none()
        val line = parserState.line.content
        val trimmed = line.toString().trim()
        if (trimmed.endsWith("$$")) {
            val before = trimmed.removeSuffix("$$")
            if (before.isNotBlank()) lines += before
            finished = true
            return BlockContinue.finished()
        }
        return BlockContinue.atIndex(parserState.index)
    }

    override fun addLine(line: SourceLine) {
        lines += line.content.toString()
    }

    override fun closeBlock() {
        block.tex = lines.joinToString("\n").trim()
    }

    class Factory : AbstractBlockParserFactory() {
        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
            if (state.indent >= 4) return BlockStart.none()
            val line = state.line.content
            val start = state.nextNonSpaceIndex
            if (line.length - start < 2 || line[start] != '$' || line[start + 1] != '$') return BlockStart.none()
            val rest = line.subSequence(start + 2, line.length).toString()
            return BlockStart.of(DisplayMathParser(rest)).atIndex(line.length)
        }
    }
}
