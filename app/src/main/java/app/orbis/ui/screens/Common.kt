package app.orbis.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Account
import app.orbis.data.Bank
import app.orbis.data.BankState
import app.orbis.data.Category
import app.orbis.data.Dates
import app.orbis.data.Money
import app.orbis.data.Txn
import app.orbis.data.TxnKind
import app.orbis.ui.LocalNav
import app.orbis.ui.O
import app.orbis.ui.Route
import app.orbis.ui.Toast
import app.orbis.ui.components.EmojiBubble
import app.orbis.ui.components.Gap
import app.orbis.ui.components.SoftButton
import app.orbis.ui.components.txnView

@Composable
fun bankState(): BankState {
    val s by Bank.state.collectAsState()
    return s
}

@Composable
fun currentAccount(): Account? {
    val s by Bank.state.collectAsState()
    val id by Bank.session.collectAsState()
    return s.account(id)
}

fun share(context: Context, text: String, title: String = "Teilen") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
}

fun copy(context: Context, text: String, label: String = "Kopiert") {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.show("$label ✓")
}

fun payLink(id: String, amount: Long? = null, note: String? = null): String = buildString {
    append("orbis://pay?to=").append(id)
    if (amount != null && amount > 0) append("&amount=").append(Money.plain(amount).replace(".", ""))
    if (!note.isNullOrBlank()) append("&note=").append(android.net.Uri.encode(note))
}

fun receipt(t: Txn, me: String, s: BankState): String {
    val signed = if (t.to == me) t.amount else -t.amount
    return buildString {
        appendLine("🧾 Orbis Beleg")
        appendLine("Betrag: ${Money.format(signed, s.settings, signed = true)}")
        appendLine("Von: ${t.fromName}${t.from?.let { " ($it)" } ?: ""}")
        appendLine("An: ${t.toName}${t.to?.let { " ($it)" } ?: ""}")
        appendLine("Datum: ${Dates.full(t.time)}")
        if (t.note.isNotBlank()) appendLine("Notiz: ${t.note}")
        append("Beleg-Nr.: ${t.id.take(8).uppercase()}")
    }
}

@Composable
fun TxnSheet(t: Txn, me: String, onDismiss: () -> Unit) {
    val s = bankState()
    val nav = LocalNav.current
    val context = LocalContext.current
    val v = txnView(t, me)
    val cat = Category.of(t.category)
    val other = if (t.to == me) t.from else t.to
    val otherName = if (t.to == me) t.fromName else t.toName
    val isPerson = (t.kind == TxnKind.TRANSFER || t.kind == TxnKind.REQUEST || t.kind == TxnKind.RECURRING) && other != null
    val known = other != null && (s.account(other) != null && s.contacts.any { it.owner == me && it.accountId == other })

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = O.c.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EmojiBubble(v.emoji, v.color, 64.dp)
            Gap(12.dp)
            Text(v.title, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Gap(4.dp)
            Text(
                Money.format(v.signed, s.settings, signed = true),
                color = if (v.signed > 0) O.c.positive else O.c.text,
                fontWeight = FontWeight.Bold, fontSize = 34.sp,
            )
            Text(Dates.full(t.time), color = O.c.muted, fontSize = 14.sp)
            Gap(18.dp)
            DetailLine("Von", t.fromName + (t.from?.let { "  ·  $it" } ?: ""))
            DetailLine("An", t.toName + (t.to?.let { "  ·  $it" } ?: ""))
            DetailLine("Kategorie", "${cat.emoji} ${cat.label}")
            if (t.note.isNotBlank()) DetailLine("Notiz", t.note)
            DetailLine("Beleg-Nr.", t.id.take(8).uppercase())
            Gap(18.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Beleg", Modifier.weight(1f), icon = Icons.Rounded.Share) { share(context, receipt(t, me, s), "Beleg teilen") }
                if (isPerson && other != null) {
                    SoftButton("Nochmal", Modifier.weight(1f), icon = Icons.Rounded.Replay) {
                        onDismiss()
                        nav.push(Route.Send(to = other, amount = if (t.from == me) t.amount else null, note = t.note))
                    }
                }
            }
            if (isPerson && other != null && !known && s.contacts.none { it.owner == me && it.accountId == other }) {
                Gap(10.dp)
                SoftButton("Als Kontakt speichern", Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd) {
                    Bank.saveContact(me, other, otherName)
                    Toast.show("Kontakt gespeichert")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, color = O.c.muted, fontSize = 14.sp, modifier = Modifier.weight(0.35f))
        Text(value, color = O.c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.65f))
    }
}
