package com.derekross.markview

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.derekross.markview.core.data.appContainer
import com.derekross.markview.core.designsystem.AppTheme
import com.derekross.markview.core.designsystem.MarkviewTheme
import com.derekross.markview.feature.reader.ReaderScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertTrue

/** Regression test: with a gesture navigation bar, scrolling down must hide the reader toolbar completely. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi", application = MarkviewApplication::class)
class ToolbarInsetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun toolbarClearsNavigationBarWhenHidden() {
        val activity = compose.activity
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val source = activity.appContainer.documents.putInline("Showcase", File("../samples/showcase.md").readText())
        val navBarPx = (48 * activity.resources.displayMetrics.density).toInt()
        var composeView: android.view.View? = null
        compose.setContent {
            composeView = LocalView.current
            MarkviewTheme(theme = AppTheme.Light, dynamicColor = false) {
                ReaderScreen(source = source, onBack = {}, onOpenDocument = {})
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Text that feels good")).fetchSemanticsNodes().isNotEmpty() }
        // Dispatch a gesture-navigation inset once Compose is listening for insets.
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarPx))
                .build()
            ViewCompat.dispatchApplyWindowInsets(composeView!!.rootView, insets)
        }
        compose.waitForIdle()

        val screenHeight = compose.onRoot().fetchSemanticsNode().size.height
        val shownNode = compose.onNodeWithContentDescription("Table of contents").fetchSemanticsNode()
        val shownTop = shownNode.positionInRoot.y
        // Sanity check: the inset was applied, so the visible toolbar sits above the navigation bar.
        assertTrue(shownTop + shownNode.size.height <= screenHeight - navBarPx, "toolbar not lifted above nav bar: top=$shownTop")

        compose.onNode(hasScrollAction()).performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + bottom * 0.2f) }
        compose.waitForIdle()
        val hiddenTop = compose.onNodeWithContentDescription("Table of contents").fetchSemanticsNode().positionInRoot.y
        assertTrue(hiddenTop >= screenHeight, "toolbar still visible after scrolling: top=$hiddenTop screen=$screenHeight")
    }
}
