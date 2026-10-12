package app.yomu.ui.screens

import android.speech.tts.Voice
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.ai.FishSearchResult
import app.yomu.ai.FishVoices
import app.yomu.core.Pref
import app.yomu.ui.openExternal
import kotlinx.coroutines.launch

/** Settings for the Gemini-powered read-aloud mode. */
@Composable
fun AiSettingsSection() {
    val prefs = Yomu.prefs
    val context = LocalContext.current
    val key by prefs.geminiKey.flow.collectAsState()
    val model by prefs.geminiModel.flow.collectAsState()
    val translate by prefs.aiTranslateGerman.flow.collectAsState()
    val captions by prefs.showCaptions.flow.collectAsState()
    val rate by prefs.ttsRate.flow.collectAsState()

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Im Reader auf die Mitte tippen → „KI-Vorlesen starten“. Gemini erkennt die Sprechblasen und wer spricht, " +
                "dein Handy liest mit Frauen-, Männer- und Erzählerstimme vor und blättert selbst weiter.",
            style = MaterialTheme.typography.bodyMedium,
        )
        var keyText by remember { mutableStateOf(key) }
        OutlinedTextField(
            value = keyText,
            onValueChange = {
                keyText = it
                prefs.geminiKey.set(it.trim())
            },
            label = { Text("Gemini-API-Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { openExternal(context, "https://aistudio.google.com/apikey") }) {
            Text("Kostenlosen Key bei Google AI Studio holen")
        }
        var modelText by remember { mutableStateOf(model) }
        OutlinedTextField(
            value = modelText,
            onValueChange = {
                modelText = it
                prefs.geminiModel.set(it.trim())
            },
            label = { Text("Modell") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ListItem(
        headlineContent = { Text("Auf Deutsch übersetzen") },
        supportingContent = { Text("Englische/japanische Comics werden auf Deutsch vorgelesen") },
        trailingContent = { Switch(checked = translate, onCheckedChange = { prefs.aiTranslateGerman.set(it) }) },
        modifier = Modifier.clickable { prefs.aiTranslateGerman.set(!translate) },
    )
    ListItem(
        headlineContent = { Text("Untertitel anzeigen") },
        trailingContent = { Switch(checked = captions, onCheckedChange = { prefs.showCaptions.set(it) }) },
        modifier = Modifier.clickable { prefs.showCaptions.set(!captions) },
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Sprechtempo: ${"%.1f".format(rate)}×", style = MaterialTheme.typography.bodyLarge)
        Slider(value = rate, onValueChange = { prefs.ttsRate.set(it) }, valueRange = 0.6f..1.8f)
    }

    FishAudioSettings()

    val language = if (translate) "de" else "en"
    var voices by remember(language) { mutableStateOf<List<Voice>>(emptyList()) }
    LaunchedEffect(language) { voices = Yomu.readAloud.voices(language) }
    if (voices.isEmpty()) {
        Text(
            "Stimmen werden geladen … (Falls nichts erscheint: Google-Sprachausgabe in den Android-Einstellungen installieren.)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    } else {
        Text(
            "Handy-Stimmen (werden benutzt, wenn Fish Audio aus ist)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        VoicePicker("Stimme für Frauen", prefs.voiceFemale, voices, language)
        VoicePicker("Stimme für Männer", prefs.voiceMale, voices, language)
        VoicePicker("Stimme für Erzähler", prefs.voiceNarrator, voices, language)
    }
}

@Composable
private fun VoicePicker(title: String, pref: Pref<String>, voices: List<Voice>, language: String) {
    val selected by pref.flow.collectAsState()
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                selected.ifBlank { "Automatisch" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { open = true }) { Text("Wählen") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Automatisch") }, onClick = {
                pref.set("")
                open = false
            })
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.name + if (voice.isNetworkConnectionRequired) " (online)" else "") },
                    onClick = {
                        pref.set(voice.name)
                        open = false
                        scope.launch { Yomu.readAloud.preview(voice.name, language) }
                    },
                )
            }
        }
    }
}

@Composable
private fun FishAudioSettings() {
    val prefs = Yomu.prefs
    val context = LocalContext.current
    val enabled by prefs.useFishAudio.flow.collectAsState()
    ListItem(
        headlineContent = { Text("Fish Audio benutzen") },
        supportingContent = { Text("Echt klingende Stimmen aus der Cloud (braucht Fish-Audio-Key und Guthaben)") },
        trailingContent = { Switch(checked = enabled, onCheckedChange = { prefs.useFishAudio.set(it) }) },
        modifier = Modifier.clickable { prefs.useFishAudio.set(!enabled) },
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Entering a key switches Fish Audio on, so it can't be forgotten.
        PrefTextField("Fish-Audio-API-Key", prefs.fishKey, secret = true) { if (it.isNotBlank()) prefs.useFishAudio.set(true) }
        TextButton(onClick = { openExternal(context, "https://fish.audio") }) { Text("Key bei fish.audio holen") }
        PrefTextField("Modell (z. B. s2.1-pro oder s2.1-pro-free)", prefs.fishModel)
        Text("Stimmen-IDs", style = MaterialTheme.typography.titleSmall)
        FishVoiceField("Junge Frau", prefs.fishYoungWoman)
        FishVoiceField("Frau (älter)", prefs.fishWoman)
        FishVoiceField("Junger Mann", prefs.fishYoungMan)
        FishVoiceField("Mann", prefs.fishMan)
        FishVoiceField("Älterer Mann", prefs.fishOldMan)
        FishVoiceField("Erzähler", prefs.fishNarrator)
        Text(
            "Du kannst hier eine ID oder einfach den Link von fish.audio einfügen. Mit ☰ nimmst du eine deiner gespeicherten Stimmen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MyFishVoices()
    }
}

@Composable
private fun PrefTextField(
    label: String,
    pref: Pref<String>,
    secret: Boolean = false,
    onChanged: (String) -> Unit = {},
) {
    val value by pref.flow.collectAsState()
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != text.trim()) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            pref.set(it.trim())
            onChanged(it.trim())
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FishVoiceField(label: String, pref: Pref<String>) {
    val saved by FishVoices.all.collectAsState()
    val current by pref.flow.collectAsState()
    var open by remember { mutableStateOf(false) }
    val savedName = FishVoices.parseId(current)?.let { id -> saved.firstOrNull { it.id == id }?.name }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f)) {
            PrefTextField(if (savedName != null) "$label · $savedName" else label, pref)
        }
        if (saved.isNotEmpty()) {
            Box {
                TextButton(onClick = { open = true }) { Text("☰") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    saved.forEach { voice ->
                        DropdownMenuItem(text = { Text(voice.name) }, onClick = {
                            pref.set(voice.id)
                            open = false
                        })
                    }
                }
            }
        }
        PreviewButton { pref.get() }
    }
}

@Composable
private fun PreviewButton(voiceId: () -> String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    OutlinedButton(onClick = {
        scope.launch {
            runCatching { Yomu.readAloud.previewFish(voiceId()) }.onFailure {
                Toast.makeText(context, it.message ?: "Fehler", Toast.LENGTH_LONG).show()
            }
        }
    }) { Text("▶") }
}

/** The user's own Fish Audio voices: add by ID or link, search the library, preview, delete. */
@Composable
private fun MyFishVoices() {
    val saved by FishVoices.all.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Text("Meine Stimmen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Text(
        "Eigene Fish-Audio-Stimmen speichern. Sie tauchen danach auch bei „Stimmen für diesen Comic“ auf (mit ★).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (saved.isEmpty()) {
        Text("Noch keine eigenen Stimmen.", style = MaterialTheme.typography.bodyMedium)
    }
    saved.forEach { voice ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(voice.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    voice.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PreviewButton { voice.id }
            TextButton(onClick = { FishVoices.remove(voice.id) }) { Text("✕") }
        }
    }

    // Add by ID or link
    var name by remember { mutableStateOf("") }
    var idText by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = idText,
        onValueChange = { idText = it },
        label = { Text("Stimmen-ID oder fish.audio-Link") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name (leer = Name von Fish Audio)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = !adding && idText.isNotBlank(),
        onClick = {
            val id = FishVoices.parseId(idText)
            if (id == null) {
                Toast.makeText(context, "Keine gültige Stimmen-ID gefunden (32 Zeichen).", Toast.LENGTH_LONG).show()
            } else {
                adding = true
                scope.launch {
                    val finalName = name.trim().ifEmpty { FishVoices.lookupTitle(id).orEmpty() }
                    FishVoices.add(finalName, id)
                    Toast.makeText(context, "Gespeichert: ${FishVoices.nameFor(id)}", Toast.LENGTH_SHORT).show()
                    name = ""
                    idText = ""
                    adding = false
                }
            }
        },
    ) { Text(if (adding) "Speichere …" else "Stimme hinzufügen") }

    // Search the Fish Audio library
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FishSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    Text("In Fish Audio suchen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("z. B. anime boy, deutsch, narrator") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            enabled = !searching && query.isNotBlank(),
            onClick = {
                searching = true
                searchError = null
                scope.launch {
                    runCatching { FishVoices.search(query) }
                        .onSuccess {
                            results = it
                            if (it.isEmpty()) searchError = "Nichts gefunden."
                        }
                        .onFailure { searchError = it.message ?: "Fehler bei der Suche" }
                    searching = false
                }
            },
        ) { Text(if (searching) "…" else "Suchen") }
    }
    searchError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    results.forEach { result ->
        val isSaved = saved.any { it.id == result.id }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val info = listOfNotNull(
                    result.languages.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    result.description.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (info.isNotEmpty()) {
                    Text(
                        info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            PreviewButton { result.id }
            TextButton(enabled = !isSaved, onClick = { FishVoices.add(result.title, result.id) }) {
                Text(if (isSaved) "✓" else "+")
            }
        }
    }
}
