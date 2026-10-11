package app.yomu.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import app.yomu.BuildConfig
import app.yomu.Yomu
import app.yomu.core.Images
import app.yomu.core.Pref
import app.yomu.core.ReaderMode
import app.yomu.core.ThemeMode
import app.yomu.ui.NsfwChips
import app.yomu.ui.langName

private val LANGS = listOf("de", "en", "all", "ja", "ko", "zh", "es", "fr", "it", "pt-BR", "ru", "tr", "id", "vi")

@Composable
fun SettingsTab() {
    val prefs = Yomu.prefs
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Einstellungen") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Header("Lesen")
            val readerMode by prefs.readerMode.flow.collectAsState()
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Standard-Lesemodus", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderMode.entries.forEach { mode ->
                        FilterChip(
                            selected = readerMode == mode,
                            onClick = { prefs.readerMode.set(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
            Toggle("Lautstärketasten blättern", prefs.volumeKeys)
            Toggle("Bildschirm beim Lesen anlassen", prefs.keepScreenOn)
            Toggle("Seitenzahl anzeigen", prefs.showPageNumber)
            Toggle("Tippzonen umkehren", prefs.invertTapZones)
            val preload by prefs.preloadPages.flow.collectAsState()
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Seiten vorladen: $preload", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = preload.toFloat(),
                    onValueChange = { prefs.preloadPages.set(it.toInt()) },
                    valueRange = 2f..15f,
                    steps = 12,
                )
            }

            Header("Aussehen")
            val theme by prefs.themeMode.flow.collectAsState()
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.DARK to "Dunkel", ThemeMode.LIGHT to "Hell").forEach { (m, l) ->
                    FilterChip(selected = theme == m, onClick = { prefs.themeMode.set(m) }, label = { Text(l) })
                }
            }
            Toggle("Echtes Schwarz (OLED)", prefs.amoledBlack)
            Toggle("Farben vom Hintergrundbild (Material You)", prefs.dynamicColor)
            Toggle("Immer 120 Hz (flüssiger, minimal mehr Akku)", prefs.force120Hz)
            val columns by prefs.gridColumns.flow.collectAsState()
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Spalten in der Bibliothek: $columns", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = columns.toFloat(),
                    onValueChange = { prefs.gridColumns.set(it.toInt()) },
                    valueRange = 2f..5f,
                    steps = 2,
                )
            }

            Header("Quellen")
            val langs by prefs.languages.flow.collectAsState()
            Text("Sprachen", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGS.forEach { code ->
                    FilterChip(
                        selected = code in langs,
                        onClick = { prefs.languages.set(if (code in langs) langs - code else langs + code) },
                        label = { Text(langName(code)) },
                    )
                }
            }
            Text("18+ Inhalte", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            NsfwChips(Modifier.padding(horizontal = 16.dp))

            Header("Speicher")
            ListItem(
                headlineContent = { Text("Seiten-Cache leeren") },
                supportingContent = { Text("Gelesene Seiten werden zwischengespeichert") },
                modifier = Modifier.clickable {
                    Images.clearPageCache()
                    Toast.makeText(context, "Cache geleert", Toast.LENGTH_SHORT).show()
                },
            )
            ListItem(
                headlineContent = { Text("Yomu ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("Erweiterungen von Keiyoushi · für Pixel 8a optimiert") },
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Toggle(title: String, pref: Pref<Boolean>) {
    val value by pref.flow.collectAsState()
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = value, onCheckedChange = { pref.set(it) }) },
        modifier = Modifier.clickable { pref.set(!value) },
    )
}
