package app.orbis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Bank
import app.orbis.data.Category
import app.orbis.data.Contact
import app.orbis.data.Dates
import app.orbis.data.Interval
import app.orbis.data.Money
import app.orbis.data.PayRequest
import app.orbis.data.RequestStatus
import app.orbis.data.TxnKind
import app.orbis.ui.LocalNav
import app.orbis.ui.O
import app.orbis.ui.Route
import app.orbis.ui.Toast
import app.orbis.ui.components.Avatar
import app.orbis.ui.components.Chip
import app.orbis.ui.components.EmojiBubble
import app.orbis.ui.components.Empty
import app.orbis.ui.components.Field
import app.orbis.ui.components.Gap
import app.orbis.ui.components.GradientButton
import app.orbis.ui.components.LiveDot
import app.orbis.ui.components.Panel
import app.orbis.ui.components.Pill
import app.orbis.ui.components.QrCode
import app.orbis.ui.components.SectionTitle
import app.orbis.ui.components.SoftButton
import app.orbis.ui.components.SubScreen
import app.orbis.ui.components.colorFor
import kotlinx.coroutines.delay

// =============================================================== Live feed

@Composable
fun LiveScreen() {
    val s = bankState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    val feed = remember(s.txns) { s.txns.filter { it.kind == TxnKind.TRANSFER || it.kind == TxnKind.REQUEST || it.kind == TxnKind.RECURRING }.sortedByDescending { it.time }.take(200) }
    val today = Dates.startOfDay(now)
    val todayFeed = feed.filter { it.time >= today }
    val anon = s.accounts.filter { it.anonymousInLive }.map { it.id }.toSet()
    fun shown(id: String?, name: String) = if (id != null && id in anon) "Anonym" else name

    SubScreen("Live-Transaktionen", actions = {
        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveDot()
            Text(" LIVE", color = Color(0xFFFF4D4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                Text("Heute", color = O.c.muted, fontSize = 12.sp)
                Text("${todayFeed.size}", color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Panel(Modifier.weight(1.6f), padding = PaddingValues(14.dp)) {
                Text("Volumen heute", color = O.c.muted, fontSize = 12.sp)
                Text(Money.format(todayFeed.sumOf { it.amount }, s.settings), color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
            }
        }
        if (feed.isEmpty()) {
            Empty("📡", "Noch ruhig hier", "Sobald jemand Geld sendet, erscheint es hier in Echtzeit.")
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(feed, key = { it.id }) { t ->
                val fresh = now - t.time < 60_000
                Row(
                    Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (fresh) O.c.accent.a.copy(alpha = 0.12f) else O.c.surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EmojiBubble(Category.of(t.category).emoji, Color(Category.of(t.category).color), 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            "${shown(t.from, t.fromName)} → ${shown(t.to, t.toName)}",
                            color = O.c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(Dates.relative(t.time, now), color = O.c.muted, fontSize = 12.sp)
                    }
                    Text(Money.format(t.amount, s.settings), color = O.c.text, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================== Requests overview

@Composable
fun RequestsScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    val incoming = s.requests.filter { it.payer == me.id && it.status == RequestStatus.PENDING }.sortedByDescending { it.time }
    val outgoing = s.requests.filter { it.requester == me.id && it.status == RequestStatus.PENDING }.sortedByDescending { it.time }
    val done = s.requests.filter { (it.payer == me.id || it.requester == me.id) && it.status != RequestStatus.PENDING }.sortedByDescending { it.time }.take(30)

    SubScreen("Anfragen", actions = {
        IconButton(onClick = { nav.push(Route.Request()) }) { Icon(Icons.Rounded.Add, "Neue Anfrage", tint = O.c.text) }
    }) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle("An dich (${incoming.size})") }
            if (incoming.isEmpty()) item { Text("Keine offenen Anfragen.", color = O.c.muted, modifier = Modifier.padding(horizontal = 20.dp)) }
            items(incoming, key = { it.id }) { r ->
                Panel(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()) { IncomingRequest(r, s) }
            }
            item { SectionTitle("Von dir (${outgoing.size})") }
            if (outgoing.isEmpty()) item { Text("Du wartest auf keine Zahlung.", color = O.c.muted, modifier = Modifier.padding(horizontal = 20.dp)) }
            items(outgoing, key = { it.id }) { r -> OutgoingRequest(r, s.account(r.payer) != null) }
            if (done.isNotEmpty()) {
                item { SectionTitle("Erledigt") }
                items(done, key = { it.id }) { r -> DoneRequest(r, me.id) }
            }
        }
    }
}

@Composable
private fun OutgoingRequest(r: PayRequest, local: Boolean) {
    val s = bankState()
    val context = LocalContext.current
    Panel(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.payerName, colorFor(r.payer), 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.payerName, color = O.c.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(r.note.ifBlank { Dates.relative(r.time) }, color = O.c.muted, fontSize = 13.sp, maxLines = 1)
            }
            Pill(Money.format(r.amount, s.settings), O.c.accent.a)
        }
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Zurückziehen", Modifier.weight(1f)) {
                Bank.setRequestStatus(r.id, RequestStatus.CANCELLED); Toast.show("Anfrage zurückgezogen")
            }
            if (local) {
                SoftButton("Erinnern", Modifier.weight(1f), icon = Icons.Rounded.Share) {
                    share(context, "Erinnerung: ${r.requesterName} wartet auf ${Money.format(r.amount, s.settings)} (${r.note.ifBlank { "Anfrage" }}).")
                }
            } else {
                SoftButton("Erhalten", Modifier.weight(1f)) {
                    Bank.markRequestPaidExternally(r.id); Toast.show("Als bezahlt markiert")
                }
            }
        }
    }
}

@Composable
private fun DoneRequest(r: PayRequest, me: String) {
    val s = bankState()
    val mine = r.requester == me
    val (label, color) = when (r.status) {
        RequestStatus.PAID -> "Bezahlt" to O.c.positive
        RequestStatus.DECLINED -> "Abgelehnt" to O.c.negative
        RequestStatus.CANCELLED -> "Zurückgezogen" to O.c.muted
        RequestStatus.PENDING -> "Offen" to O.c.warning
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val name = if (mine) r.payerName else r.requesterName
        Avatar(name, colorFor(if (mine) r.payer else r.requester), 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text((if (mine) "Von " else "An ") + name, color = O.c.text, fontSize = 14.sp, maxLines = 1)
            Text(Money.format(r.amount, s.settings) + " · " + Dates.shortDay(r.time), color = O.c.muted, fontSize = 12.sp)
        }
        Pill(label, color)
    }
}

// =============================================================== Contacts

@Composable
fun ContactsScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var adding by remember { mutableStateOf(false) }
    val list = s.contacts.filter { it.owner == me.id }
        .filter { query.isBlank() || it.name.contains(query, true) || it.accountId.contains(query, true) }
        .sortedWith(compareByDescending<Contact> { it.favorite }.thenBy { it.name.lowercase() })
    val others = s.accounts.filter { a -> a.id != me.id && s.contacts.none { it.owner == me.id && it.accountId == a.id } }

    SubScreen("Kontakte", actions = {
        IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "Kontakt hinzufügen", tint = O.c.text) }
    }) {
        SearchBox(query, { query = it }, "Kontakte durchsuchen")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (list.isEmpty()) item { Empty("👥", "Keine Kontakte", "Füge Kontakte über + oder nach einer Überweisung hinzu.") }
            items(list, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.name, colorFor(c.accountId), 46.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(c.name, color = O.c.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(c.accountId + if (s.account(c.accountId) != null) " · auf diesem Gerät" else "", color = O.c.muted, fontSize = 13.sp)
                    }
                    IconButton(onClick = { Bank.toggleFavorite(c.id) }) {
                        Icon(if (c.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorit", tint = if (c.favorite) O.c.warning else O.c.muted)
                    }
                    IconButton(onClick = { nav.push(Route.Send(to = c.accountId)) }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "Senden", tint = O.c.accent.a)
                    }
                }
            }
            if (others.isNotEmpty() && query.isBlank()) {
                item { SectionTitle("Andere Konten auf diesem Gerät") }
                items(others, key = { "a" + it.id }) { a ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(a.name, colorFor(a.id), 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(a.name, color = O.c.text, fontWeight = FontWeight.SemiBold)
                            Text(a.id, color = O.c.muted, fontSize = 13.sp)
                        }
                        Text(
                            "Hinzufügen", color = O.c.accent.a, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { Bank.saveContact(me.id, a.id, a.name); Toast.show("${a.name} hinzugefügt") }.padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        ContactSheet(me.id, editing) { adding = false; editing = null }
    }
}

@Composable
private fun ContactSheet(owner: String, contact: Contact?, onDone: () -> Unit) {
    val nav = LocalNav.current
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var id by remember { mutableStateOf(contact?.accountId ?: "") }
    val valid = IdPattern.matches(id) && id != owner
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = O.c.surface) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
            Text(if (contact == null) "Neuer Kontakt" else "Kontakt bearbeiten", style = MaterialTheme.typography.titleLarge, color = O.c.text)
            Gap(14.dp)
            Field(name, { name = it.take(30) }, "Name")
            Gap(10.dp)
            Field(id, { id = it.uppercase().trim().take(9) }, "Konto-ID", error = if (id.isNotEmpty() && !valid) "Format: AB-12CDE" else null)
            Gap(18.dp)
            GradientButton("Speichern", Modifier.fillMaxWidth(), enabled = valid) {
                Bank.saveContact(owner, id, name, id = contact?.id)
                Toast.show("Gespeichert")
                onDone()
            }
            if (contact != null) {
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Anfordern", Modifier.weight(1f)) { onDone(); nav.push(Route.Request(from = contact.accountId)) }
                    SoftButton("Löschen", Modifier.weight(1f), icon = Icons.Rounded.Delete, color = O.c.negative) {
                        Bank.deleteContact(contact.id); Toast.show("Kontakt gelöscht"); onDone()
                    }
                }
            }
        }
    }
}

// =============================================================== Standing orders

@Composable
fun RecurringScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    var adding by remember { mutableStateOf(false) }
    val list = s.recurring.filter { it.owner == me.id }.sortedBy { it.nextRun }
    val monthly = list.filter { it.active }.sumOf {
        when (it.interval) {
            Interval.DAILY -> it.amount * 30
            Interval.WEEKLY -> it.amount * 4
            Interval.MONTHLY -> it.amount
        }
    }
    SubScreen("Daueraufträge", actions = {
        IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "Neuer Dauerauftrag", tint = O.c.text) }
    }) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Panel(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Pro Monat ca.", color = O.c.muted, fontSize = 13.sp)
                    Text(Money.format(monthly, s.settings), color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Text("Fällige Aufträge laufen automatisch, sobald du die App öffnest.", color = O.c.muted, fontSize = 12.sp)
                }
            }
            if (list.isEmpty()) item { Empty("🔁", "Keine Daueraufträge", "Regelmäßige Zahlungen wie Miete oder Taschengeld automatisch senden.") }
            items(list, key = { it.id }) { r ->
                Panel(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(r.toName, colorFor(r.toId), 42.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(r.toName, color = O.c.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${r.interval.label} · nächste: ${Dates.shortDay(r.nextRun)}", color = O.c.muted, fontSize = 12.sp)
                            if (r.note.isNotBlank()) Text(r.note, color = O.c.muted, fontSize = 12.sp, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(r.amount, s.settings), color = O.c.text, fontWeight = FontWeight.Bold)
                            Pill(if (r.active) "Aktiv" else "Pausiert", if (r.active) O.c.positive else O.c.muted)
                        }
                    }
                    Gap(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton(if (r.active) "Pausieren" else "Fortsetzen", Modifier.weight(1f), icon = if (r.active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) {
                            Bank.toggleRecurring(r.id)
                        }
                        SoftButton("Löschen", Modifier.weight(1f), icon = Icons.Rounded.Delete, color = O.c.negative) {
                            Bank.deleteRecurring(r.id); Toast.show("Dauerauftrag gelöscht")
                        }
                    }
                    if (r.runs > 0) Text("${r.runs}× ausgeführt", color = O.c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    if (adding) RecurringSheet(me.id) { adding = false }
}

@Composable
private fun RecurringSheet(owner: String, onDone: () -> Unit) {
    val s = bankState()
    val parties = remember(s, owner) { knownParties(s, owner) }
    var to by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf(Interval.MONTHLY) }
    var category by remember { mutableStateOf(Category.HOME.key) }
    var startToday by remember { mutableStateOf(false) }
    val cents = Money.fromInput(amount)
    val valid = IdPattern.matches(to) && to != owner && cents > 0
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = O.c.surface) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
            Text("Neuer Dauerauftrag", style = MaterialTheme.typography.titleLarge, color = O.c.text)
            Gap(12.dp)
            if (parties.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(parties, key = { it.id }) { p -> Chip(p.name, to == p.id, { to = p.id }) }
                }
                Gap(10.dp)
            }
            Field(to, { to = it.uppercase().trim().take(9) }, "Empfänger-ID")
            Gap(10.dp)
            Field(amount, { v -> amount = v.filter { it.isDigit() || it == ',' }.take(10) }, "Betrag", keyboard = KeyboardType.Decimal)
            Gap(10.dp)
            Field(note, { note = it.take(60) }, "Verwendungszweck")
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Interval.entries.forEach { i -> Chip(i.label, interval == i, { interval = i }) }
            }
            Gap(10.dp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries) { c -> Chip(c.label, category == c.key, { category = c.key }, leading = c.emoji) }
            }
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Erste Zahlung heute", color = O.c.text, modifier = Modifier.weight(1f))
                app.orbis.ui.components.OrbisSwitch(startToday) { startToday = it }
            }
            Gap(18.dp)
            GradientButton("Dauerauftrag anlegen", Modifier.fillMaxWidth(), enabled = valid) {
                val now = System.currentTimeMillis()
                val first = if (startToday) now else if (interval == Interval.MONTHLY) Dates.addMonths(now, 1) else now + interval.days * Dates.DAY
                Bank.createRecurring(owner, to, cents, note, category, interval, first)
                val ran = Bank.processRecurring()
                Toast.show(if (ran > 0) "Angelegt und erste Zahlung gesendet" else "Dauerauftrag angelegt")
                onDone()
            }
        }
    }
}

// =============================================================== Budgets

@Composable
fun BudgetsScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    val monthStart = Dates.startOfMonth(System.currentTimeMillis())
    val spent = s.txns.filter { it.from == me.id && it.time >= monthStart && it.kind != TxnKind.GOAL_IN }
        .groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
    var editing by remember { mutableStateOf<Category?>(null) }

    SubScreen("Budgets") {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "Lege für jede Kategorie ein Monatslimit fest. ${Dates.month(monthStart)}:",
                    color = O.c.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(Category.entries.filter { it != Category.SALARY }) { c ->
                val b = s.budgets.firstOrNull { it.owner == me.id && it.category == c.key }
                val used = spent[c.key] ?: 0L
                Panel(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), onClick = { editing = c }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiBubble(c.emoji, Color(c.color), 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(c.label, color = O.c.text, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (b == null) "Kein Limit · ${Money.format(used, s.settings)} ausgegeben"
                                else "${Money.format(used, s.settings)} von ${Money.format(b.limit, s.settings)}",
                                color = if (b != null && used > b.limit) O.c.negative else O.c.muted, fontSize = 13.sp,
                            )
                        }
                    }
                    if (b != null) {
                        Gap(10.dp)
                        app.orbis.ui.components.ProgressBar(used.toFloat() / b.limit, if (used > b.limit) O.c.negative else Color(c.color))
                    }
                }
            }
        }
    }
    editing?.let { c ->
        var value by remember(c) { mutableStateOf(s.budgets.firstOrNull { it.owner == me.id && it.category == c.key }?.let { Money.plain(it.limit).replace(".", "") } ?: "") }
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = O.c.surface) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
                Text("${c.emoji} Budget für ${c.label}", style = MaterialTheme.typography.titleLarge, color = O.c.text)
                Gap(14.dp)
                Field(value, { v -> value = v.filter { it.isDigit() || it == ',' }.take(10) }, "Monatslimit", keyboard = KeyboardType.Decimal)
                Gap(16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Entfernen", Modifier.weight(1f), color = O.c.negative) {
                        Bank.setBudget(me.id, c.key, 0); editing = null
                    }
                    GradientButton("Speichern", Modifier.weight(1f), enabled = Money.fromInput(value) > 0) {
                        Bank.setBudget(me.id, c.key, Money.fromInput(value)); Toast.show("Budget gespeichert"); editing = null
                    }
                }
            }
        }
    }
}

// =============================================================== My QR code

@Composable
fun MyQrScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    val cents = Money.fromInput(amount)
    val link = payLink(me.id, cents.takeIf { it > 0 })
    SubScreen("Mein QR-Code") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Gap(8.dp)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(O.c.gradient).padding(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(me.name, me.colorIndex, 56.dp)
                    Gap(8.dp)
                    Text(me.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(me.id, color = Color.White.copy(alpha = 0.85f))
                    Gap(16.dp)
                    Box(Modifier.size(240.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp)) {
                        QrCode(link, Modifier.fillMaxSize())
                    }
                    if (cents > 0) {
                        Gap(12.dp)
                        Text("Betrag: ${Money.format(cents, s.settings)}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Gap(16.dp)
            Text(
                "Lass den Code scannen: Wer Orbis hat, landet direkt beim Senden an dich.",
                color = O.c.muted, textAlign = TextAlign.Center, fontSize = 14.sp,
            )
            Gap(14.dp)
            Field(amount, { v -> amount = v.filter { it.isDigit() || it == ',' }.take(10) }, "Betrag festlegen (optional)", keyboard = KeyboardType.Decimal)
            Gap(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("ID kopieren", Modifier.weight(1f)) { copy(context, me.id, "Konto-ID kopiert") }
                GradientButton("Teilen", Modifier.weight(1f), icon = Icons.Rounded.Share) {
                    share(context, "Sende mir ${s.settings.currencyName} mit Orbis${if (cents > 0) " (${Money.format(cents, s.settings)})" else ""}: $link\nKonto-ID: ${me.id}")
                }
            }
            Gap(24.dp)
        }
    }
}
