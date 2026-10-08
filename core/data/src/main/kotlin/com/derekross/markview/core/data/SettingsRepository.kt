package com.derekross.markview.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.derekross.markview.core.designsystem.AppTheme
import com.derekross.markview.core.designsystem.ReaderSettings
import com.derekross.markview.core.designsystem.ReaderTypeface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {
    private val store get() = context.markviewDataStore

    val settings: Flow<ReaderSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[THEME] = next.theme.name
            prefs[DYNAMIC] = next.dynamicColor
            prefs[TYPEFACE] = next.typeface.name
            prefs[TEXT_SCALE] = next.textScale.coerceIn(ReaderSettings.TextScaleRange)
            prefs[LINE_HEIGHT] = next.lineHeight.coerceIn(ReaderSettings.LineHeightRange)
            prefs[WIDTH] = next.maxContentWidth.coerceIn(ReaderSettings.ContentWidthRange)
            prefs[KEEP_ON] = next.keepScreenOn
        }
    }

    private fun Preferences.toSettings(): ReaderSettings {
        val d = ReaderSettings()
        return ReaderSettings(
            theme = this[THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: d.theme,
            dynamicColor = this[DYNAMIC] ?: d.dynamicColor,
            typeface = this[TYPEFACE]?.let { runCatching { ReaderTypeface.valueOf(it) }.getOrNull() } ?: d.typeface,
            textScale = this[TEXT_SCALE] ?: d.textScale,
            lineHeight = this[LINE_HEIGHT] ?: d.lineHeight,
            maxContentWidth = this[WIDTH] ?: d.maxContentWidth,
            keepScreenOn = this[KEEP_ON] ?: d.keepScreenOn,
        )
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val TYPEFACE = stringPreferencesKey("typeface")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val WIDTH = intPreferencesKey("content_width")
        val KEEP_ON = booleanPreferencesKey("keep_screen_on")
    }
}
