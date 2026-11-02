package app.orbis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Bank
import app.orbis.data.Dates
import app.orbis.data.Money
import app.orbis.ui.LocalNav
import app.orbis.ui.O
import app.orbis.ui.Route
import app.orbis.ui.components.Avatar
import app.orbis.ui.components.Divider
import app.orbis.ui.components.Gap
import app.orbis.ui.components.Panel
import app.orbis.ui.components.SectionTitle
import app.orbis.ui.components.SettingRow

@Composable
fun ProfileTab() {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    val context = LocalContext.current
    val mine = s.txns.filter { it.from == me.id || it.to == me.id }
    val contacts = s.contacts.count { it.owner == me.id }
    val chevron: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = O.c.muted) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = BottomBarSpace),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(me.name, me.colorIndex, 92.dp)
            Gap(12.dp)
            Text(me.name, style = MaterialTheme.typography.headlineSmall, color = O.c.text)
            if (me.bio.isNotBlank()) Text(me.bio, color = O.c.muted, fontSize = 14.sp)
            Gap(8.dp)
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(O.c.surface2).clickable { copy(context, me.id, "Konto-ID kopiert") }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(me.id, color = O.c.text, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.ContentCopy, null, tint = O.c.muted, modifier = Modifier.padding(start = 8.dp).size(16.dp))
            }
            Gap(6.dp)
            Text("Mitglied seit ${Dates.shortDay(me.createdAt)}${Dates.month(me.createdAt).takeLast(4)}", color = O.c.muted, fontSize = 12.sp)
        }

        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniPanel("Buchungen", "${mine.size}", Modifier.weight(1f))
            MiniPanel("Kontakte", "$contacts", Modifier.weight(1f))
            MiniPanel("Guthaben", if (s.settings.hideBalance) "••••" else Money.format(me.balance, s.settings), Modifier.weight(1.4f))
        }

        SectionTitle("Konto")
        Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
            SettingRow(Icons.Rounded.QrCode2, "Mein QR-Code", "Zum Bezahlen scannen lassen", trailing = chevron) { nav.push(Route.MyQr) }
            Divider()
            SettingRow(Icons.Rounded.Share, "Zahlungslink teilen", "Andere können dir direkt senden", trailing = chevron) {
                share(context, "Sende mir ${s.settings.currencyName} mit Orbis: ${payLink(me.id)}\nKonto-ID: ${me.id}")
            }
            Divider()
            SettingRow(Icons.Rounded.Contacts, "Kontakte", "$contacts gespeichert", trailing = chevron) { nav.push(Route.Contacts) }
            Divider()
            SettingRow(Icons.Rounded.Repeat, "Daueraufträge", "${s.recurring.count { it.owner == me.id && it.active }} aktiv", trailing = chevron) { nav.push(Route.Recurring) }
            Divider()
            SettingRow(Icons.Rounded.PieChart, "Budgets", "Ausgaben im Griff behalten", trailing = chevron) { nav.push(Route.Budgets) }
        }

        SectionTitle("App")
        Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
            SettingRow(Icons.Rounded.Palette, "Einstellungen", "Design, Währung, Daten", trailing = chevron) { nav.push(Route.Settings) }
            Divider()
            SettingRow(Icons.Rounded.Security, "Sicherheit", "PIN, Biometrie, Limits", trailing = chevron) { nav.push(Route.Security) }
        }

        Gap(16.dp)
        Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
            SettingRow(Icons.Rounded.SwapHoriz, "Konto wechseln", "${s.accounts.size} Konto/Konten auf diesem Gerät", trailing = chevron) {
                Bank.updateSettings { it.copy(lastAccount = null) }
                Bank.logout()
            }
            Divider()
            SettingRow(Icons.AutoMirrored.Rounded.Logout, "Abmelden", tint = O.c.negative) { Bank.logout() }
        }
        Gap(20.dp)
        Text(
            "Orbis · Version ${app.orbis.BuildConfig.VERSION_NAME}",
            color = O.c.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun MiniPanel(label: String, value: String, modifier: Modifier) {
    Panel(modifier, padding = PaddingValues(14.dp)) {
        Text(value, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        Text(label, color = O.c.muted, fontSize = 12.sp)
    }
}
