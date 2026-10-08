package com.derekross.markview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.core.data.appContainer
import com.derekross.markview.core.designsystem.MarkviewTheme
import com.derekross.markview.core.designsystem.ReaderSettings
import com.derekross.markview.core.designsystem.isDark

class MainActivity : ComponentActivity() {
    /** A document requested by an incoming intent, consumed by the nav host. */
    private var pendingSource by mutableStateOf<DocumentSource?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingSource = IntentParser.parse(intent, appContainer)

        setContent {
            val settings by appContainer.settings.settings.collectAsStateWithLifecycle(initialValue = ReaderSettings())
            val dark = settings.theme.isDark()
            androidx.compose.runtime.LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
            }
            MarkviewTheme(theme = settings.theme, dynamicColor = settings.dynamicColor) {
                MarkviewNavHost(
                    pendingSource = pendingSource,
                    onPendingConsumed = { pendingSource = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        IntentParser.parse(intent, appContainer)?.let { pendingSource = it }
    }
}
