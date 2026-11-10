package app.aiqadigest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
        enableEdgeToEdge()
        setContent { DigestTheme { DigestScreen(viewModel) { openUrl(it) } } }
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
private fun DigestTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
private fun DigestScreen(viewModel: DigestViewModel, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Text("AI & QA Digest", style = MaterialTheme.typography.headlineSmall)
            val status = statusLine(state, zone)
            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
            }
        }
        if (state.items.isEmpty() && !state.loading) {
            item {
                Text(
                    if (state.error != null) "Nothing to show yet." else "No entries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(state.items, key = { it.id }) { item ->
            Entry(item, isNew = item.id in state.newIds, zone = zone, onOpen = onOpen)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun Entry(item: DigestItem, isNew: Boolean, zone: ZoneId, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(item.url) }.padding(vertical = 12.dp),
    ) {
        val star = Color(0xFFE0A100)
        Text(
            buildAnnotatedString {
                if (isNew) withStyle(SpanStyle(color = star)) { append("★ ") }
                append(item.title)
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        if (item.excerpt.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(item.excerpt, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOf(item.source, formatDate(item.publishedAt, zone)).filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
