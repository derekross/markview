package com.derekross.markview.core.designsystem

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WidthNormal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private data class ThemeSwatch(val theme: AppTheme, val paper: Color, val ink: Color)

private val swatches = listOf(
    ThemeSwatch(AppTheme.System, Color(0xFFFBF8FF), Color(0xFF131318)),
    ThemeSwatch(AppTheme.Light, Color(0xFFFBF8FF), Color(0xFF1B1B21)),
    ThemeSwatch(AppTheme.Sepia, Color(0xFFF6EEDD), Color(0xFF3A2E22)),
    ThemeSwatch(AppTheme.Dark, Color(0xFF131318), Color(0xFFE4E1E9)),
    ThemeSwatch(AppTheme.Black, Color(0xFF000000), Color(0xFFE4E1E9)),
)

/** The "Aa" panel: theme, typeface, size, spacing and width, with live preview in the document behind it. */
@Composable
fun ReaderSettingsPanel(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Reading appearance", style = MaterialTheme.typography.titleLarge)

        SectionLabel(Icons.Outlined.LightMode, "Theme")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            swatches.forEach { swatch ->
                ThemeChip(swatch, selected = settings.theme == swatch.theme) { onChange(settings.copy(theme = swatch.theme)) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ToggleRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "Dynamic color",
                subtitle = "Match your wallpaper colors",
                checked = settings.dynamicColor,
                enabled = settings.theme != AppTheme.Sepia,
            ) { onChange(settings.copy(dynamicColor = it)) }
        }

        SectionLabel(Icons.Outlined.TextFields, "Typeface")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderTypeface.entries.forEach { typeface ->
                TypefaceCard(
                    typeface = typeface,
                    selected = settings.typeface == typeface,
                    modifier = Modifier.weight(1f),
                ) { onChange(settings.copy(typeface = typeface)) }
            }
        }

        LabeledSlider(
            icon = Icons.Outlined.TextFields,
            label = "Text size",
            valueLabel = "${(settings.textScale * 100).roundToInt()}%",
            value = settings.textScale,
            range = ReaderSettings.TextScaleRange,
            steps = 15,
        ) { onChange(settings.copy(textScale = it)) }

        LabeledSlider(
            icon = Icons.Outlined.FormatLineSpacing,
            label = "Line spacing",
            valueLabel = "%.1f×".format(settings.lineHeight),
            value = settings.lineHeight,
            range = ReaderSettings.LineHeightRange,
            steps = 6,
        ) { onChange(settings.copy(lineHeight = it)) }

        LabeledSlider(
            icon = Icons.Outlined.WidthNormal,
            label = "Max line width",
            valueLabel = "${settings.maxContentWidth} dp",
            value = settings.maxContentWidth.toFloat(),
            range = ReaderSettings.ContentWidthRange.first.toFloat()..ReaderSettings.ContentWidthRange.last.toFloat(),
            steps = 11,
        ) { onChange(settings.copy(maxContentWidth = it.roundToInt())) }

        ToggleRow(
            icon = Icons.Outlined.LightMode,
            title = "Keep screen on",
            subtitle = "While a document is open",
            checked = settings.keepScreenOn,
        ) { onChange(settings.copy(keepScreenOn = it)) }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThemeChip(swatch: ThemeSwatch, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "ring")
    val ringWidth by animateDpAsState(if (selected) 3.dp else 1.dp, label = "ringWidth")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(4.dp)
            .semantics { contentDescription = "${swatch.theme.label} theme" },
    ) {
        val fill = if (swatch.theme == AppTheme.System) {
            Brush.linearGradient(0.5f to swatch.paper, 0.5f to swatch.ink)
        } else {
            Brush.linearGradient(listOf(swatch.paper, swatch.paper))
        }
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(fill).border(ringWidth, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (swatch.theme == AppTheme.System) MaterialTheme.colorScheme.primary else swatch.ink,
                )
            } else if (swatch.theme != AppTheme.System) {
                Text("Aa", color = swatch.ink, fontFamily = MarkviewFonts.Literata, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(swatch.theme.label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TypefaceCard(typeface: ReaderTypeface, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val family = when (typeface) {
        ReaderTypeface.Editorial -> MarkviewFonts.Literata
        ReaderTypeface.Modern -> MarkviewFonts.Inter
        ReaderTypeface.Technical -> MarkviewFonts.JetBrainsMono
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Aa", fontFamily = family, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = if (selected) colors.onPrimaryContainer else colors.onSurface)
            Text(typeface.label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun LabeledSlider(
    icon: ImageVector,
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
