package app.aiqadigest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: DigestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeId by viewModel.themeId.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val theme = Themes.resolve(themeId, systemDark)
            val darkBackground = theme.bg.luminance() < 0.5f

            // Keep status and navigation bar icons readable on the theme's background.
            LaunchedEffect(darkBackground) {
                val style = if (darkBackground) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                this@MainActivity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            // Material colors are only used by the design picker dialog; they follow the system.
            val scheme = if (systemDark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                var picking by remember { mutableStateOf(false) }
                DigestScreen(viewModel, theme, onOpen = { openUrl(it) }, onDesign = { picking = true })
                if (picking) {
                    DesignPicker(
                        current = themeId,
                        systemDark = systemDark,
                        onPick = { viewModel.setTheme(it) },
                        onClose = { picking = false },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        UpdateWorker.schedule(this, replaceExisting = true)
        viewModel.onStart()
    }

    override fun onStop() {
        viewModel.onStop()
        super.onStop()
    }

    private fun openUrl(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            // no browser installed: nothing to do
        }
    }
}

@Composable
private fun DigestScreen(
    viewModel: DigestViewModel,
    theme: DigestTheme,
    onOpen: (String) -> Unit,
    onDesign: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    Column(Modifier.fillMaxSize().background(theme.bg).safeDrawingPadding()) {
        Header(theme, statusLine(state, zone), onDesign)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(theme.gap),
        ) {
            if (state.items.isEmpty() && !state.loading) {
                item {
                    Text(
                        if (state.error != null) "Nothing to show yet." else "No entries.",
                        color = theme.muted,
                        fontFamily = theme.bodyFont,
                        fontSize = theme.bodySize,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.items, key = { it.id }) { item ->
                Entry(item, isNew = item.id in state.newIds, theme = theme, zone = zone, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun Header(theme: DigestTheme, status: String, onDesign: () -> Unit) {
    val onBar = theme.header == HeaderStyle.Bar
    val titleColor = if (onBar) theme.barText else theme.titleColor
    val statusColor = if (onBar) theme.barText else theme.muted
    val outer = if (onBar) Modifier.fillMaxWidth().background(theme.barColor) else Modifier.fillMaxWidth()
    Column(outer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (theme.upper) "AI & QA DIGEST" else "AI & QA Digest",
                    color = titleColor,
                    fontFamily = theme.headFont,
                    fontWeight = theme.headWeight,
                    fontSize = theme.headSize,
                    letterSpacing = theme.tracking,
                    lineHeight = theme.headSize * 1.1f,
                    style = TextStyle(shadow = theme.glow?.let { Shadow(it, blurRadius = 14f) }),
                )
                if (status.isNotEmpty()) {
                    Text(
                        status,
                        color = statusColor,
                        fontFamily = theme.metaFont,
                        fontSize = theme.metaSize,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onDesign)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Design",
                    color = titleColor,
                    fontFamily = theme.metaFont,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
        if (theme.header == HeaderStyle.Rule) {
            Box(Modifier.fillMaxWidth().height(theme.ruleThickness).background(theme.ruleColor))
        }
    }
}

@Composable
private fun Entry(item: DigestItem, isNew: Boolean, theme: DigestTheme, zone: ZoneId, onOpen: (String) -> Unit) {
    val shape = RoundedCornerShape(theme.radius)
    val framed = theme.entry != EntryStyle.Plain
    val frame: Modifier = when (theme.entry) {
        EntryStyle.Plain -> Modifier
        EntryStyle.Card -> Modifier
            .shadow(theme.elevation, shape, ambientColor = theme.shadowColor, spotColor = theme.shadowColor)
            .background(theme.surface, shape)
            .then(if (theme.border > 0.dp) Modifier.border(theme.border, theme.borderColor, shape) else Modifier)
        EntryStyle.Outlined -> Modifier
            .background(theme.surface, shape)
            .border(theme.border, theme.borderColor, shape)
        EntryStyle.HardShadow -> Modifier
            .drawBehind {
                val off = theme.shadowOffset.toPx()
                drawRoundRect(
                    color = theme.shadowColor,
                    topLeft = Offset(off, off),
                    size = size,
                    cornerRadius = CornerRadius(theme.radius.toPx()),
                )
            }
            .background(theme.surface, shape)
            .border(theme.border, theme.borderColor, shape)
        EntryStyle.Bevel -> Modifier
            .drawBehind {
                val w = 2.dp.toPx()
                val light = Color(0xFFFFFFFF)
                val dark = Color(0xFF404040)
                drawRect(theme.surface)
                drawRect(light, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, w))
                drawRect(light, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(w, size.height))
                drawRect(dark, topLeft = Offset(0f, size.height - w), size = androidx.compose.ui.geometry.Size(size.width, w))
                drawRect(dark, topLeft = Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height))
            }
        EntryStyle.Glossy -> Modifier
            .shadow(theme.elevation, shape, ambientColor = theme.shadowColor, spotColor = theme.shadowColor)
            .background(Brush.verticalGradient(listOf(theme.surface, theme.surface2)), shape)
            .border(theme.border, theme.borderColor, shape)
    }
    val padding = if (framed) Modifier.padding(14.dp) else Modifier.padding(vertical = 12.dp)

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().then(frame).clickable { onOpen(item.url) }.then(padding),
        ) {
            Text(
                buildAnnotatedString {
                    if (isNew) withStyle(SpanStyle(color = theme.star)) { append("★ ") }
                    append(if (theme.upper) item.title.uppercase() else item.title)
                },
                color = theme.titleColor,
                fontFamily = theme.titleFont,
                fontWeight = theme.titleWeight,
                fontStyle = if (theme.italic) FontStyle.Italic else FontStyle.Normal,
                fontSize = theme.titleSize,
                letterSpacing = theme.tracking,
                lineHeight = theme.titleSize * 1.25f,
                textDecoration = if (theme.underline) TextDecoration.Underline else TextDecoration.None,
                style = TextStyle(shadow = theme.glow?.let { Shadow(it, blurRadius = 10f) }),
            )
            if (item.excerpt.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.excerpt,
                    color = theme.text,
                    fontFamily = theme.bodyFont,
                    fontSize = theme.bodySize,
                    lineHeight = theme.bodySize * 1.4f,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                listOf(item.source, formatDate(item.publishedAt, zone)).filter { it.isNotEmpty() }.joinToString(" · "),
                color = theme.muted,
                fontFamily = theme.metaFont,
                fontSize = theme.metaSize,
            )
        }
        if (!framed && theme.lineThickness > 0.dp) {
            HorizontalDivider(thickness = theme.lineThickness, color = theme.line)
        }
    }
}

@Composable
private fun DesignPicker(current: String, systemDark: Boolean, onPick: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    "Design",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    "Applies right away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(Themes.all, key = { it.id }) { t ->
                        val shown = Themes.resolve(t.id, systemDark)
                        val selected = t.id == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(t.id) })
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Swatch(shown)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(t.era, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) {
                                Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun Swatch(t: DigestTheme) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(width = 52.dp, height = 36.dp)
            .background(t.bg, shape)
            .border(1.dp, t.muted, shape),
    ) {
        Text(
            "Aa",
            color = t.titleColor,
            fontFamily = t.titleFont,
            fontWeight = t.titleWeight,
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .size(width = 14.dp, height = 6.dp)
                .background(t.accent),
        )
    }
}

private fun statusLine(state: UiState, zone: ZoneId): String {
    val parts = mutableListOf<String>()
    if (state.lastUpdatedMillis > 0) {
        val t = Instant.ofEpochMilli(state.lastUpdatedMillis).atZone(zone)
        val fmt = if (t.toLocalDate() == LocalDate.now(zone)) "HH:mm" else "d MMM HH:mm"
        parts += "Updated " + t.format(DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH))
    }
    if (state.loading) parts += "refreshing…"
    else state.error?.let { parts += "couldn't refresh ($it)" }
    return parts.joinToString(" · ")
}

private fun formatDate(iso: String, zone: ZoneId): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
} catch (_: Exception) {
    ""
}
