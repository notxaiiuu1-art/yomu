package app.orbis.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Account
import app.orbis.data.Bank
import app.orbis.data.Money
import app.orbis.data.Security
import app.orbis.data.ThemeMode
import app.orbis.ui.Accents
import app.orbis.ui.Biometric
import app.orbis.ui.O
import app.orbis.ui.Swatches
import app.orbis.ui.Toast
import app.orbis.ui.components.Chip
import app.orbis.ui.components.Divider
import app.orbis.ui.components.Field
import app.orbis.ui.components.Gap
import app.orbis.ui.components.GradientButton
import app.orbis.ui.components.OrbisSwitch
import app.orbis.ui.components.Panel
import app.orbis.ui.components.PinDots
import app.orbis.ui.components.PinPad
import app.orbis.ui.components.SectionTitle
import app.orbis.ui.components.SettingRow
import app.orbis.ui.components.SubScreen
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    val st = s.settings
    val context = LocalContext.current
    var editProfile by remember { mutableStateOf(false) }
    var editCurrency by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(("﻿" + Bank.exportCsv(me.id)).toByteArray()) }
            }.isSuccess
            Toast.show(if (ok) "CSV exportiert" else "Export fehlgeschlagen")
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(Bank.exportBackup().toByteArray()) } }.isSuccess
            Toast.show(if (ok) "Backup gespeichert" else "Backup fehlgeschlagen")
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            Toast.show(if (text != null && Bank.importBackup(text)) "Backup wiederhergestellt – bitte anmelden" else "Datei ist kein gültiges Backup")
        }
    }

    SubScreen("Einstellungen") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            SectionTitle("Profil")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                SettingRow(Icons.Rounded.Person, "Name & Farbe", me.name) { editProfile = true }
            }

            SectionTitle("Design")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Text("Modus", color = O.c.muted, fontSize = 13.sp)
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m -> Chip(m.label, st.theme == m, { Bank.updateSettings { it.copy(theme = m) } }) }
                }
                Gap(16.dp)
                Text("Akzent", color = O.c.muted, fontSize = 13.sp)
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Accents.forEachIndexed { i, a ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(a.a, a.b)))
                                    .border(3.dp, if (st.accent == i) O.c.text else Color.Transparent, CircleShape)
                                    .clickable { Bank.updateSettings { it.copy(accent = i) } },
                            )
                            Text(a.name, color = O.c.muted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }

            SectionTitle("Währung")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                SettingRow(
                    Icons.Rounded.CurrencyExchange, "Währung anpassen",
                    "${st.currencyName} (${st.currencySymbol}) · Beispiel: ${Money.format(170000, st)}",
                ) { editCurrency = true }
            }

            SectionTitle("Privatsphäre")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                SettingRow(Icons.Rounded.VisibilityOff, "Beträge verbergen", "Kontostand standardmäßig ausblenden", trailing = {
                    OrbisSwitch(st.hideBalance) { v -> Bank.updateSettings { it.copy(hideBalance = v) } }
                })
                Divider()
                SettingRow(Icons.Rounded.ScreenLockPortrait, "Screenshots blockieren", "Auch in der App-Übersicht unsichtbar", trailing = {
                    OrbisSwitch(st.blockScreenshots) { v -> Bank.updateSettings { it.copy(blockScreenshots = v) } }
                })
                Divider()
                SettingRow(Icons.Rounded.Badge, "Anonym im Live-Feed", "Dein Name wird dort nicht angezeigt", trailing = {
                    OrbisSwitch(me.anonymousInLive) { v -> Bank.updateAccount(me.id) { it.copy(anonymousInLive = v) } }
                })
            }

            SectionTitle("Bedienung")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                SettingRow(Icons.Rounded.Vibration, "Haptisches Feedback", trailing = {
                    OrbisSwitch(st.haptics) { v -> Bank.updateSettings { it.copy(haptics = v) } }
                })
            }

            SectionTitle("Daten")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                SettingRow(Icons.Rounded.TableChart, "Verlauf als CSV", "Für Excel & Co.") { csvLauncher.launch("orbis-${me.id}.csv") }
                Divider()
                SettingRow(Icons.Rounded.Save, "Backup erstellen", "Alle Konten und Daten als Datei") { backupLauncher.launch("orbis-backup.json") }
                Divider()
                SettingRow(Icons.Rounded.Restore, "Backup wiederherstellen", "Ersetzt alle Daten auf diesem Gerät") {
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                Divider()
                SettingRow(Icons.Rounded.Download, "Konto-Infos teilen", "Name, ID und Zahlungslink") {
                    share(context, "${me.name}\nKonto-ID: ${me.id}\n${payLink(me.id)}")
                }
            }

            SectionTitle("Gefahrenzone")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                SettingRow(Icons.Rounded.DeleteForever, "Dieses Konto löschen", "Guthaben und Verlauf von ${me.id}", tint = O.c.negative) { confirmDeleteAccount = true }
                Divider()
                SettingRow(Icons.Rounded.DeleteForever, "Alles zurücksetzen", "Löscht alle Konten auf diesem Gerät", tint = O.c.negative) { confirmReset = true }
            }
        }
    }

    if (editProfile) ProfileSheet(me) { editProfile = false }
    if (editCurrency) CurrencySheet { editCurrency = false }
    if (confirmReset) {
        Confirm("Alles zurücksetzen?", "Alle Konten, Buchungen und Einstellungen werden gelöscht. Erstelle vorher ein Backup!", "Zurücksetzen", { confirmReset = false }) {
            Bank.resetAll()
        }
    }
    if (confirmDeleteAccount) {
        Confirm("Konto löschen?", "${me.name} (${me.id}) und dessen Sparziele, Kontakte und Daueraufträge werden gelöscht.", "Löschen", { confirmDeleteAccount = false }) {
            Bank.deleteAccount(me.id)
        }
    }
}

@Composable
fun Confirm(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = O.c.surface,
        title = { Text(title, color = O.c.text) },
        text = { Text(text, color = O.c.muted) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(action, color = O.c.negative, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = O.c.text) } },
    )
}

@Composable
private fun ProfileSheet(me: Account, onDone: () -> Unit) {
    var name by remember { mutableStateOf(me.name) }
    var bio by remember { mutableStateOf(me.bio) }
    var color by remember { mutableStateOf(me.colorIndex) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = O.c.surface) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
            Text("Profil bearbeiten", style = MaterialTheme.typography.titleLarge, color = O.c.text)
            Gap(14.dp)
            Field(name, { name = it.take(24) }, "Name")
            Gap(10.dp)
            Field(bio, { bio = it.take(60) }, "Status (optional)")
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatches.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c)
                            .border(3.dp, if (i == color) O.c.text else Color.Transparent, CircleShape)
                            .clickable { color = i },
                    )
                }
            }
            Gap(18.dp)
            GradientButton("Speichern", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                Bank.updateAccount(me.id) { it.copy(name = name.trim(), bio = bio.trim(), colorIndex = color) }
                Toast.show("Profil gespeichert")
                onDone()
            }
        }
    }
}

@Composable
private fun CurrencySheet(onDone: () -> Unit) {
    val st = bankState().settings
    var symbol by remember { mutableStateOf(st.currencySymbol) }
    var name by remember { mutableStateOf(st.currencyName) }
    var before by remember { mutableStateOf(st.symbolBefore) }
    var prefix by remember { mutableStateOf(st.idPrefix) }
    val presets = listOf("₳", "◎", "Ⓞ", "✦", "€", "$", "¤", "⌬", "Ꞡ", "⚜")
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = O.c.surface) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
            Text("Währung", style = MaterialTheme.typography.titleLarge, color = O.c.text)
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { p ->
                    Box(
                        Modifier.weight(1f).size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (p == symbol) O.c.accent.a.copy(alpha = 0.2f) else O.c.surface2)
                            .clickable { symbol = p },
                        contentAlignment = Alignment.Center,
                    ) { Text(p, color = O.c.text, fontSize = 17.sp) }
                }
            }
            Gap(10.dp)
            Field(symbol, { symbol = it.take(3) }, "Symbol")
            Gap(10.dp)
            Field(name, { name = it.take(20) }, "Name der Währung")
            Gap(10.dp)
            Field(prefix, { prefix = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(3) }, "Präfix für neue Konto-IDs", keyboard = KeyboardType.Text)
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Symbol vor dem Betrag", color = O.c.text, modifier = Modifier.weight(1f))
                OrbisSwitch(before) { before = it }
            }
            Gap(8.dp)
            Text(
                "Vorschau: " + Money.format(170000, st.copy(currencySymbol = symbol.ifBlank { "₳" }, symbolBefore = before)),
                color = O.c.positive, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
            Gap(18.dp)
            GradientButton("Speichern", Modifier.fillMaxWidth(), enabled = symbol.isNotBlank() && name.isNotBlank()) {
                Bank.updateSettings {
                    it.copy(currencySymbol = symbol.trim(), currencyName = name.trim(), symbolBefore = before, idPrefix = prefix.ifBlank { "OR" })
                }
                onDone()
            }
        }
    }
}

// =============================================================== Security

@Composable
fun SecurityScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    val context = LocalContext.current
    val bioAvailable = remember { Biometric.available(context) }
    var changePin by remember { mutableStateOf(false) }
    var editLimit by remember { mutableStateOf(false) }
    val lockOptions = listOf(0 to "Sofort", 30 to "30 s", 60 to "1 Min.", 300 to "5 Min.", -1 to "Nie")

    SubScreen("Sicherheit") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Panel(Modifier.padding(16.dp).fillMaxWidth()) {
                Text("🛡️", fontSize = 30.sp)
                Gap(6.dp)
                Text("Dein Konto ist geschützt", color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "PIN gehasht gespeichert · ${if (me.biometric) "Biometrie an" else "Biometrie aus"} · " +
                        if (me.dailyLimit > 0) "Limit ${Money.format(me.dailyLimit, s.settings)}/Tag" else "Kein Tageslimit",
                    color = O.c.muted, fontSize = 13.sp,
                )
            }
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                SettingRow(Icons.Rounded.Password, "PIN ändern", "4-stellige PIN") { changePin = true }
                Divider()
                SettingRow(
                    Icons.Rounded.Fingerprint, "Biometrie",
                    if (bioAvailable) "Mit Fingerabdruck entsperren" else "Auf diesem Gerät nicht eingerichtet",
                    trailing = {
                        OrbisSwitch(me.biometric && bioAvailable) { v ->
                            if (bioAvailable) Bank.updateAccount(me.id) { it.copy(biometric = v) } else Toast.show("Richte zuerst Biometrie in Android ein")
                        }
                    },
                )
                Divider()
                SettingRow(Icons.Rounded.VpnKey, "PIN bei Zahlungen", "Jede Überweisung bestätigen", trailing = {
                    OrbisSwitch(me.pinForTransfers) { v -> Bank.updateAccount(me.id) { it.copy(pinForTransfers = v) } }
                })
                Divider()
                SettingRow(
                    Icons.Rounded.Speed, "Tageslimit",
                    if (me.dailyLimit > 0) "${Money.format(me.dailyLimit, s.settings)} · heute ${Money.format(Bank.spentToday(me.id), s.settings)} genutzt" else "Kein Limit",
                ) { editLimit = true }
            }
            SectionTitle("Automatische Sperre")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Rounded.LockClock, null, tint = O.c.accent.a)
                    Text("Sperren nach dem Verlassen der App", color = O.c.text, modifier = Modifier.padding(start = 12.dp))
                }
                Gap(10.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lockOptions.forEach { (sec, label) ->
                        Chip(label, s.settings.autoLockSeconds == sec, { Bank.updateSettings { it.copy(autoLockSeconds = sec) } })
                    }
                }
            }
            Gap(16.dp)
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                SettingRow(Icons.Rounded.Lock, "Jetzt sperren", "Zurück zur PIN-Eingabe") { Bank.logout() }
            }
        }
    }

    if (changePin) ChangePinSheet(me) { changePin = false }
    if (editLimit) {
        var v by remember { mutableStateOf(if (me.dailyLimit > 0) Money.plain(me.dailyLimit).replace(".", "") else "") }
        ModalBottomSheet(onDismissRequest = { editLimit = false }, containerColor = O.c.surface) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
                Text("Tageslimit", style = MaterialTheme.typography.titleLarge, color = O.c.text)
                Text("Maximaler Betrag, den du pro Tag senden kannst. Leer = kein Limit.", color = O.c.muted, fontSize = 13.sp)
                Gap(14.dp)
                Field(v, { x -> v = x.filter { it.isDigit() || it == ',' }.take(10) }, "Limit", keyboard = KeyboardType.Decimal)
                Gap(16.dp)
                GradientButton("Speichern", Modifier.fillMaxWidth()) {
                    Bank.updateAccount(me.id) { it.copy(dailyLimit = Money.fromInput(v)) }
                    Toast.show("Tageslimit gespeichert")
                    editLimit = false
                }
            }
        }
    }
}

@Composable
private fun ChangePinSheet(me: Account, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var pin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val titles = listOf("Aktuelle PIN", "Neue PIN", "Neue PIN bestätigen")

    LaunchedEffect(pin) {
        if (pin.length < 4) return@LaunchedEffect
        when (step) {
            0 -> if (Security.check(pin, me)) { step = 1; pin = "" } else { error = true; delay(600); error = false; pin = "" }
            1 -> { newPin = pin; step = 2; pin = "" }
            else -> if (pin == newPin) {
                Bank.changePin(me.id, newPin)
                Toast.show("PIN geändert")
                onDone()
            } else { error = true; delay(600); error = false; pin = ""; step = 1 }
        }
    }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = O.c.surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(titles[step], style = MaterialTheme.typography.titleLarge, color = O.c.text)
            Gap(18.dp)
            PinDots(4, pin.length, error)
            Gap(20.dp)
            PinPad(onDigit = { if (pin.length < 4) pin += it }, onDelete = { pin = pin.dropLast(1) }, onBiometric = null)
            Spacer(Modifier.size(8.dp))
        }
    }
}
