package com.derekross.markview.core.render.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A typeset formula. Sizes are in em of the surrounding text; the baseline sits [ascent] below the top. */
class MathSvg(val svg: String, val widthEm: Float, val ascentEm: Float, val depthEm: Float) {
    val heightEm: Float get() = ascentEm + depthEm

    companion object {
        private val VIEW_BOX = Regex("""viewBox="([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)"""")

        /** MathJax viewBox units are 1/1000 em with the baseline at y = 0. */
        fun parse(svg: String): MathSvg? {
            val (_, minY, width, height) = VIEW_BOX.find(svg)?.destructured ?: return null
            val top = minY.toFloatOrNull() ?: return null
            val w = width.toFloatOrNull() ?: return null
            val h = height.toFloatOrNull() ?: return null
            return MathSvg(svg, w / 1000f, -top / 1000f, (h + top) / 1000f)
        }
    }
}

/** A rasterized Mermaid diagram. [width]/[height] are in CSS px (treated as dp). */
class DiagramImage(val bitmap: Bitmap, val file: File, val width: Float, val height: Float)

/** Mermaid theme derived from the app's color scheme. */
data class DiagramTheme(val dark: Boolean, val variables: Map<String, String>, val scale: Float) {
    fun toJson(): String = JSONObject().apply {
        put("dark", dark)
        put("scale", scale.toDouble())
        put("fontFamily", "sans-serif")
        put("themeVariables", JSONObject(variables))
    }.toString()
}

/**
 * Offline renderer for math (MathJax → SVG) and Mermaid (→ PNG) running in a single hidden WebView.
 *
 * Bundled scripts live in `assets/markview-render`. Results are cached in memory and on disk, so a
 * document renders instantly the second time it's opened. The WebView is created lazily on the main
 * thread; [RenderEngineHost] attaches it to the window so Chromium lays out and rasterizes reliably.
 */
@SuppressLint("StaticFieldLeak") // Holds the application context only.
object RenderEngine {
    private const val TAG = "RenderEngine"
    private const val TIMEOUT_MS = 20_000L
    private const val ORIGIN = "https://appassets.androidplatform.net"

    private lateinit var appContext: Context
    private var webView: WebView? = null
    private var ready = CompletableDeferred<Unit>()
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<RawResult>>()

    private val mathCache = LruCache<String, MathSvg>(512)
    private val diagramCache = object : LruCache<String, DiagramImage>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: DiagramImage) = value.bitmap.allocationByteCount
    }
    private val failures = ConcurrentHashMap.newKeySet<String>()
    private val seededDiagrams = ConcurrentHashMap<String, DiagramImage>()

    private class RawResult(val ok: Boolean, val payload: String, val width: Float, val height: Float)

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    /** Returns cached math synchronously when available (avoids layout jumps on re-display). */
    fun cachedMath(tex: String, display: Boolean): MathSvg? = mathCache.get(key("math", display.toString(), tex))

    fun cachedDiagram(source: String, theme: DiagramTheme): DiagramImage? =
        seededDiagrams[source] ?: diagramCache.get(diagramKey(source, theme))

    /** Pre-populates the math cache (screenshot tests can't run the WebView's JavaScript). */
    @VisibleForTesting
    fun seedMath(tex: String, display: Boolean, svg: String) {
        MathSvg.parse(svg)?.let { mathCache.put(key("math", display.toString(), tex), it) }
    }

    /** Supplies a pre-rendered diagram for [source] regardless of theme (screenshot tests). */
    @VisibleForTesting
    fun seedDiagram(source: String, image: DiagramImage) {
        seededDiagrams[source] = image
    }

    suspend fun renderMath(tex: String, display: Boolean): MathSvg? {
        val key = key("math", display.toString(), tex)
        mathCache.get(key)?.let { return it }
        if (key in failures) return null
        diskRead(key, "svg")?.let { cached -> MathSvg.parse(cached)?.let { mathCache.put(key, it); return it } }
        val result = request(if (display) "math-display" else "math-inline", tex, "{}") ?: return null
        if (!result.ok) {
            failures += key
            return null
        }
        val math = MathSvg.parse(result.payload) ?: return null
        diskWrite(key, "svg", result.payload.toByteArray())
        mathCache.put(key, math)
        return math
    }

    /** Renders a Mermaid diagram, or returns the error message via [onError]. */
    suspend fun renderDiagram(source: String, theme: DiagramTheme, onError: (String) -> Unit = {}): DiagramImage? {
        seededDiagrams[source]?.let { return it }
        val key = diagramKey(source, theme)
        diagramCache.get(key)?.let { return it }
        withContext(Dispatchers.IO) { loadDiagramFromDisk(key) }?.let {
            diagramCache.put(key, it)
            return it
        }
        val result = request("mermaid", source, theme.toJson())
        if (result == null) {
            onError("The diagram renderer didn't respond.")
            return null
        }
        if (!result.ok) {
            onError(result.payload)
            return null
        }
        val image = withContext(Dispatchers.IO) {
            val bytes = Base64.decode(result.payload.substringAfter(','), Base64.DEFAULT)
            val file = diskFile(key, "png")
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            diskFile(key, "size").writeText("${result.width}x${result.height}")
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { DiagramImage(it, file, result.width, result.height) }
        } ?: return null
        diagramCache.put(key, image)
        return image
    }

    private fun loadDiagramFromDisk(key: String): DiagramImage? {
        val png = diskFile(key, "png")
        val size = diskFile(key, "size")
        if (!png.exists() || !size.exists()) return null
        val (w, h) = size.readText().split('x').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 2 } ?: return null
        val bitmap = BitmapFactory.decodeFile(png.path) ?: return null
        return DiagramImage(bitmap, png, w, h)
    }

    private suspend fun request(kind: String, source: String, optionsJson: String): RawResult? {
        if (!::appContext.isInitialized) return null
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<RawResult>()
        pending[id] = deferred
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    val view = ensureWebView()
                    ready.await()
                    view.evaluateJavascript("MV.render($id, ${JSONObject.quote(kind)}, ${JSONObject.quote(source)}, $optionsJson)", null)
                }
                deferred.await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Render failed", e)
            null
        } finally {
            pending.remove(id)
        }
    }

    /** The engine's WebView, created on first use. Must be called on the main thread. */
    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWebView(): WebView {
        webView?.let { return it }
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
            .build()
        val view = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(1, 1)
            addJavascriptInterface(Bridge, "MarkviewBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                    // Recreate on next request instead of crashing the app.
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                    webView = null
                    ready = CompletableDeferred()
                    pending.values.forEach { it.complete(RawResult(false, "Renderer restarted", 0f, 0f)) }
                    return true
                }
            }
            loadUrl("$ORIGIN/assets/markview-render/index.html")
        }
        webView = view
        return view
    }

    private object Bridge {
        @JavascriptInterface
        fun onReady() {
            ready.complete(Unit)
        }

        @JavascriptInterface
        fun onResult(id: Int, ok: Boolean, payload: String, width: Double, height: Double) {
            pending[id]?.complete(RawResult(ok, payload, width.toFloat(), height.toFloat()))
        }
    }

    // region caching

    private fun diagramKey(source: String, theme: DiagramTheme) = key("mermaid", theme.toJson(), source)

    private fun key(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        parts.forEach { digest.update(it.toByteArray()); digest.update(0) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun diskFile(key: String, ext: String) = File(File(appContext.cacheDir, "markview-render"), "$key.$ext")

    private suspend fun diskRead(key: String, ext: String): String? = withContext(Dispatchers.IO) {
        diskFile(key, ext).takeIf { it.exists() }?.readText()
    }

    private suspend fun diskWrite(key: String, ext: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        runCatching {
            val file = diskFile(key, ext)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    // endregion
}
