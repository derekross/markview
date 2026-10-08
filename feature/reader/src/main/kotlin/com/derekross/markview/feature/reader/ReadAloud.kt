package com.derekross.markview.feature.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.derekross.markview.core.markdown.MdBlock
import com.derekross.markview.core.markdown.MdDocument
import com.derekross.markview.core.markdown.plainText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** One chunk of speech, tied back to the block it came from. */
data class Utterance(val blockIndex: Int, val text: String)

data class ReadAloudState(
    val status: Status = Status.Idle,
    /** Block currently being spoken, or -1. */
    val blockIndex: Int = -1,
    val rate: Float = 1f,
    val error: String? = null,
) {
    enum class Status { Idle, Starting, Playing, Paused }

    val active: Boolean get() = status != Status.Idle
}

/**
 * Reads a document aloud with the device's offline text-to-speech engine, block by block.
 *
 * Only a couple of utterances are queued ahead so pause, skip and rate changes take effect
 * immediately. Callbacks arrive on TTS binder threads; state is exposed as a [StateFlow].
 */
class ReadAloudController(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var utterances: List<Utterance> = emptyList()
    private var pendingStart: Int? = null

    /** Index into [utterances] of the chunk being spoken. */
    @Volatile private var position = 0
    @Volatile private var queuedThrough = -1

    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    fun setDocument(document: MdDocument) {
        utterances = buildUtterances(document)
    }

    /** Starts reading at the first speakable block at or after [blockIndex]. */
    fun play(blockIndex: Int) {
        val start = utterances.indexOfFirst { it.blockIndex >= blockIndex }.takeIf { it >= 0 } ?: 0
        if (utterances.isEmpty()) {
            _state.update { it.copy(error = "Nothing to read in this document") }
            return
        }
        _state.update { it.copy(status = ReadAloudState.Status.Starting, blockIndex = utterances[start].blockIndex, error = null) }
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingStart = start
            if (engine == null) tts = TextToSpeech(appContext, ::onInit)
            return
        }
        speakFrom(start)
    }

    fun pause() {
        tts?.stop()
        _state.update { if (it.active) it.copy(status = ReadAloudState.Status.Paused) else it }
    }

    fun resume() {
        if (!_state.value.active) return
        if (ttsReady) speakFrom(position) else pendingStart = position
    }

    fun togglePause() = if (_state.value.status == ReadAloudState.Status.Paused) resume() else pause()

    /** Skips to the next or previous block. */
    fun skip(forward: Boolean) {
        if (utterances.isEmpty()) return
        val currentBlock = utterances.getOrNull(position)?.blockIndex ?: return
        val target = if (forward) {
            utterances.indexOfFirst { it.blockIndex > currentBlock }.takeIf { it >= 0 } ?: return
        } else {
            val previousBlock = utterances.lastOrNull { it.blockIndex < currentBlock }?.blockIndex ?: currentBlock
            utterances.indexOfFirst { it.blockIndex == previousBlock }
        }
        if (_state.value.status == ReadAloudState.Status.Paused) {
            position = target
            _state.update { it.copy(blockIndex = utterances[target].blockIndex) }
        } else {
            speakFrom(target)
        }
    }

    fun setRate(rate: Float) {
        _state.update { it.copy(rate = rate) }
        tts?.setSpeechRate(rate)
        if (_state.value.status == ReadAloudState.Status.Playing) speakFrom(position)
    }

    fun stop() {
        pendingStart = null
        tts?.stop()
        _state.update { ReadAloudState(rate = it.rate) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            _state.update { ReadAloudState(rate = it.rate, error = "Text-to-speech isn't available on this device") }
            return
        }
        val locale = Locale.getDefault()
        if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) engine.language = locale
        engine.setSpeechRate(_state.value.rate)
        engine.setOnUtteranceProgressListener(listener)
        ttsReady = true
        pendingStart?.let { start ->
            pendingStart = null
            if (_state.value.status == ReadAloudState.Status.Starting) speakFrom(start)
        }
    }

    private fun speakFrom(index: Int) {
        val engine = tts ?: return
        position = index.coerceIn(0, utterances.lastIndex)
        engine.stop()
        queuedThrough = position - 1
        _state.update {
            it.copy(status = ReadAloudState.Status.Playing, blockIndex = utterances[position].blockIndex)
        }
        enqueueAhead(flush = true)
    }

    /** Keeps the engine's queue topped up to [LOOKAHEAD] utterances past the current one. */
    @Synchronized
    private fun enqueueAhead(flush: Boolean = false) {
        val engine = tts ?: return
        var first = flush
        while (queuedThrough < minOf(position + LOOKAHEAD, utterances.lastIndex)) {
            queuedThrough++
            val utterance = utterances[queuedThrough]
            engine.speak(
                utterance.text,
                if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                Bundle(),
                queuedThrough.toString(),
            )
            first = false
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            val index = utteranceId.toIntOrNull() ?: return
            position = index
            _state.update {
                if (it.status == ReadAloudState.Status.Playing) it.copy(blockIndex = utterances[index].blockIndex) else it
            }
            enqueueAhead()
        }

        override fun onDone(utteranceId: String) {
            val index = utteranceId.toIntOrNull() ?: return
            if (index >= utterances.lastIndex && _state.value.status == ReadAloudState.Status.Playing) {
                _state.update { ReadAloudState(rate = it.rate) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = onError(utteranceId, TextToSpeech.ERROR)

        override fun onError(utteranceId: String, errorCode: Int) {
            _state.update { ReadAloudState(rate = it.rate, error = "Read aloud stopped (speech engine error $errorCode)") }
        }
    }

    companion object {
        private const val LOOKAHEAD = 2
        val Rates = listOf(0.8f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        /** Turns blocks into speakable chunks. Code and display math are skipped. */
        fun buildUtterances(document: MdDocument, maxLength: Int = 3_000): List<Utterance> =
            document.blocks.flatMapIndexed { index, block ->
                val text = speakableText(block)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                if (text.isEmpty()) emptyList() else chunk(text, maxLength).map { Utterance(index, it) }
            }

        internal fun speakableText(block: MdBlock): String? = when (block) {
            is MdBlock.Heading -> block.text.trimEnd('.') + "."
            is MdBlock.Paragraph -> block.plainText()
            is MdBlock.Quote -> block.plainText()
            is MdBlock.Callout ->
                (block.title ?: block.type.label).trimEnd('.') + ". " + block.blocks.joinToString(" ") { it.plainText() }
            is MdBlock.ListBlock -> block.items.joinToString(" ") { item ->
                val text = item.blocks.joinToString(" ") { child -> speakableText(child).orEmpty() }.trim()
                if (text.isEmpty() || text.last() in ".!?:;") text else "$text."
            }
            is MdBlock.Table -> (listOf(block.header) + block.rows).joinToString(" ") { row ->
                row.joinToString(", ") { it.content.plainText() }.trim() + "."
            }
            is MdBlock.Image -> block.alt.takeIf { it.isNotBlank() }?.let { "Image: $it." }
            is MdBlock.Html -> block.raw
            is MdBlock.CodeBlock, is MdBlock.MathBlock, MdBlock.ThematicBreak -> null
        }

        /** Splits on sentence boundaries so each chunk fits the engine's input limit. */
        internal fun chunk(text: String, maxLength: Int): List<String> {
            if (text.length <= maxLength) return listOf(text)
            val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            val chunks = mutableListOf<String>()
            val current = StringBuilder()
            for (sentence in sentences) {
                if (current.isNotEmpty() && current.length + sentence.length + 1 > maxLength) {
                    chunks += current.toString()
                    current.clear()
                }
                if (sentence.length > maxLength) {
                    sentence.chunked(maxLength).forEach { chunks += it }
                } else {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(sentence)
                }
            }
            if (current.isNotEmpty()) chunks += current.toString()
            return chunks
        }
    }
}
