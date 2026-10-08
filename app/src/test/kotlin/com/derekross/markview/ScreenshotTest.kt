package com.derekross.markview

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.derekross.markview.core.data.appContainer
import com.derekross.markview.core.designsystem.AppTheme
import com.derekross.markview.core.designsystem.MarkviewTheme
import com.derekross.markview.core.designsystem.ReaderTypeface
import com.derekross.markview.feature.home.HomeScreen
import com.derekross.markview.feature.reader.ReaderScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders real screens on the JVM (Robolectric native graphics) and records PNGs to docs/screenshots.
 * Run with `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi", application = MarkviewApplication::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MarkviewApplication>()
    private val showcase = File("../samples/showcase.md").readText()
    private val outDir = File("../docs/screenshots").apply { mkdirs() }

    private fun shot(name: String) = compose.onRoot().captureRoboImage(File(outDir, "$name.png").path)

    private fun reader(theme: AppTheme, typeface: ReaderTypeface, scrollTo: Int? = null, name: String, after: () -> Unit = {}) {
        runBlocking { app.appContainer.settings.update { it.copy(theme = theme, typeface = typeface, dynamicColor = false) } }
        val source = app.appContainer.documents.putInline("Showcase", showcase)
        compose.setContent {
            MarkviewTheme(theme = theme, dynamicColor = false) {
                ReaderScreen(source = source, onBack = {}, onOpenDocument = {})
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Text that feels good")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        scrollTo?.let { compose.onNode(hasScrollAction() and hasText("Text that feels good", substring = true).not()).performScrollToIndex(it) }
        after()
        compose.waitForIdle()
        shot(name)
    }

    @Test
    fun readerLightEditorial() = reader(AppTheme.Light, ReaderTypeface.Editorial, name = "reader_light_editorial")

    @Test
    fun readerSepiaEditorialCallouts() = reader(AppTheme.Sepia, ReaderTypeface.Editorial, scrollTo = 6, name = "reader_sepia_callouts")

    @Test
    fun readerDarkTechnicalCode() = reader(AppTheme.Dark, ReaderTypeface.Technical, scrollTo = 14, name = "reader_dark_code")

    @Test
    fun readerBlackModernTable() = reader(AppTheme.Black, ReaderTypeface.Modern, scrollTo = 18, name = "reader_black_table")

    @Test
    fun readerTableOfContents() = reader(AppTheme.Light, ReaderTypeface.Modern, name = "reader_toc") {
        compose.onNodeWithContentDescription("Table of contents").performClick()
        compose.waitForIdle()
    }

    @Test
    fun readerAppearancePanel() = reader(AppTheme.Light, ReaderTypeface.Editorial, name = "reader_appearance") {
        compose.onNodeWithContentDescription("Reading appearance").performClick()
        compose.waitForIdle()
    }

    @Test
    fun homeEmpty() {
        runBlocking {
            val lib = app.appContainer.library
            lib.library.first().entries.forEach { lib.remove(it.key) }
        }
        compose.setContent {
            MarkviewTheme(theme = AppTheme.Light, dynamicColor = false) {
                HomeScreen(onOpenDocument = {}, onOpenFolder = {})
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("A calmer way to read Markdown")).fetchSemanticsNodes().isNotEmpty() }
        shot("home_empty")
    }

    @Test
    fun homeWithLibrary() {
        runBlocking {
            val lib = app.appContainer.library
            val docs = listOf(
                Triple("https://raw.githubusercontent.com/o/r/HEAD/README.md", "Project README", 0.42f),
                Triple("content://docs/notes.md", "Meeting notes, October", 0.75f),
                Triple("content://docs/guide.md", "The Markview Showcase", 0.12f),
                Triple("content://docs/essay.md", "On Reading Slowly", 1f),
            )
            docs.forEachIndexed { i, (key, title, progress) ->
                val source = com.derekross.markview.core.data.DocumentSource.fromKey(key)
                lib.recordOpened(source, title, key.substringAfterLast('/'), 2400 + i * 900, 10 + i * 4)
                lib.savePosition(key, 3, 0, progress)
                if (i == 1) lib.setFavorite(key, true)
            }
        }
        compose.setContent {
            MarkviewTheme(theme = AppTheme.Light, dynamicColor = false) {
                HomeScreen(onOpenDocument = {}, onOpenFolder = {})
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Continue reading")).fetchSemanticsNodes().isNotEmpty() }
        shot("home_library")
    }
}
