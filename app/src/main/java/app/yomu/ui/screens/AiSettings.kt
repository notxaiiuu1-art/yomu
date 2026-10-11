package app.yomu.ui.screens

import android.speech.tts.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
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
    if (!enabled) return

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrefTextField("Fish-Audio-API-Key", prefs.fishKey, secret = true)
        TextButton(onClick = { openExternal(context, "https://fish.audio") }) { Text("Key bei fish.audio holen") }
        PrefTextField("Modell (z. B. s2.1-pro oder s2.1-pro-free)", prefs.fishModel)
        Text("Stimmen-IDs", style = MaterialTheme.typography.titleSmall)
        FishVoiceField("Junge Frau", prefs.fishYoungWoman)
        FishVoiceField("Frau (älter)", prefs.fishWoman)
        FishVoiceField("Mann", prefs.fishMan)
        FishVoiceField("Älterer Mann", prefs.fishOldMan)
        FishVoiceField("Erzähler", prefs.fishNarrator)
    }
}

@Composable
private fun PrefTextField(label: String, pref: Pref<String>, secret: Boolean = false) {
    val value by pref.flow.collectAsState()
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            pref.set(it.trim())
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FishVoiceField(label: String, pref: Pref<String>) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) { PrefTextField(label, pref) }
        OutlinedButton(onClick = {
            scope.launch {
                runCatching { Yomu.readAloud.previewFish(pref.get()) }.onFailure {
                    android.widget.Toast.makeText(context, it.message ?: "Fehler", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }) { Text("▶") }
    }
}
