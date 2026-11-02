package app.orbis.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Account
import app.orbis.data.Bank
import app.orbis.data.BankState
import app.orbis.data.Dates
import app.orbis.data.Money
import app.orbis.data.PayRequest
import app.orbis.data.RequestStatus
import app.orbis.data.Txn
import app.orbis.data.TxnKind
import app.orbis.ui.LocalNav
import app.orbis.ui.O
import app.orbis.ui.Route
import app.orbis.ui.Tab
import app.orbis.ui.Toast
import app.orbis.ui.components.Avatar
import app.orbis.ui.components.Empty
import app.orbis.ui.components.Gap
import app.orbis.ui.components.LiveDot
import app.orbis.ui.components.Panel
import app.orbis.ui.components.Pill
import app.orbis.ui.components.ProgressBar
import app.orbis.ui.components.SectionTitle
import app.orbis.ui.components.TxnRow
import app.orbis.ui.components.colorFor
import app.orbis.ui.components.haptic
import app.orbis.ui.swatch
import java.util.Calendar

@Composable
fun HomeTab() {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    var selected by remember { mutableStateOf<Txn?>(null) }

    val mine = remember(s.txns, me.id) { s.txns.filter { it.from == me.id || it.to == me.id }.sortedByDescending { it.time } }
    val incoming = s.requests.filter { it.payer == me.id && it.status == RequestStatus.PENDING }.sortedByDescending { it.time }
    val outgoing = s.requests.filter { it.requester == me.id && it.status == RequestStatus.PENDING }
    val favorites = s.contacts.filter { it.owner == me.id && it.favorite }
    val goals = s.goals.filter { it.owner == me.id }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomBarSpace)) {
        item { Header(me, incoming.size) }
        item { BalanceCard(me, s, mine) }
        item { QuickActions() }

        item {
            SectionTitle("Anfragen", action = if (incoming.size + outgoing.size > 0) "Alle" else null) { nav.push(Route.Requests) }
            RequestsPanel(incoming, outgoing.size, s)
        }

        if (favorites.isNotEmpty()) {
            item {
                SectionTitle("Favoriten", action = "Kontakte") { nav.push(Route.Contacts) }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(favorites, key = { it.id }) { c ->
                        Column(
                            Modifier.width(66.dp).clip(RoundedCornerShape(16.dp)).clickable { nav.push(Route.Send(to = c.accountId)) }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(c.name, colorFor(c.accountId), 54.dp)
                            Gap(6.dp)
                            Text(c.name.substringBefore(' '), color = O.c.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Sparziele", action = "Alle") { nav.tab.value = Tab.GOALS }
            if (goals.isEmpty()) {
                Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { nav.tab.value = Tab.GOALS }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🐷", fontSize = 28.sp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text("Erstes Sparziel anlegen", color = O.c.text, fontWeight = FontWeight.SemiBold)
                            Text("Leg Geld für Wünsche zur Seite", color = O.c.muted, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(goals, key = { it.id }) { g ->
                        Panel(Modifier.width(170.dp), onClick = { nav.tab.value = Tab.GOALS }) {
                            Text(g.emoji, fontSize = 26.sp)
                            Gap(8.dp)
                            Text(g.name, color = O.c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (s.settings.hideBalance) "••••" else Money.format(g.saved, s.settings),
                                color = O.c.muted, fontSize = 13.sp,
                            )
                            Gap(10.dp)
                            ProgressBar(if (g.target > 0) g.saved.toFloat() / g.target else 0f, swatch(g.colorIndex))
                        }
                    }
                }
            }
        }

        item { LiveBanner(s) }

        item { SectionTitle("Letzte Aktivität", action = if (mine.isNotEmpty()) "Alle" else null) { nav.tab.value = Tab.HISTORY } }
        if (mine.isEmpty()) {
            item { Empty("🌱", "Noch keine Buchungen", "Sende oder empfange dein erstes Geld.") }
        } else {
            items(mine.take(6), key = { it.id }) { t ->
                TxnRow(t, me.id, s.settings, s.settings.hideBalance) { selected = t }
            }
        }
    }

    selected?.let { t -> TxnSheet(t, me.id) { selected = null } }
}

@Composable
private fun Header(me: Account, pending: Int) {
    val nav = LocalNav.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Hallo"
        in 18..22 -> "Guten Abend"
        else -> "Gute Nacht"
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(CircleShape).clickable { nav.tab.value = Tab.PROFILE }) { Avatar(me.name, me.colorIndex, 46.dp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("$greet 👋", color = O.c.muted, fontSize = 13.sp)
            Text(me.name, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { nav.push(Route.MyQr) }) { Icon(Icons.Rounded.QrCode2, "Mein QR-Code", tint = O.c.text) }
        Box {
            IconButton(onClick = { nav.push(Route.Requests) }) { Icon(Icons.Rounded.Notifications, "Anfragen", tint = O.c.text) }
            if (pending > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(18.dp).clip(CircleShape).background(O.c.negative),
                    contentAlignment = Alignment.Center,
                ) { Text("$pending", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun BalanceCard(me: Account, s: BankState, mine: List<Txn>) {
    val context = LocalContext.current
    val hidden = s.settings.hideBalance
    val monthStart = Dates.startOfMonth(System.currentTimeMillis())
    val monthTx = mine.filter { it.time >= monthStart }
    val inMonth = monthTx.filter { it.to == me.id && it.kind != TxnKind.GOAL_OUT }.sumOf { it.amount }
    val outMonth = monthTx.filter { it.from == me.id && it.kind != TxnKind.GOAL_IN }.sumOf { it.amount }
    val saved = s.goals.filter { it.owner == me.id }.sumOf { it.saved }
    val animated by animateFloatAsState(me.balance.toFloat(), tween(800), label = "bal")
    val accent = O.c.accent

    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(accent.a, accent.b))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.width * 0.45f, center = Offset(size.width * 0.95f, -size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.3f, center = Offset(size.width * 0.1f, size.height * 1.1f))
        }
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kontostand", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Betrag verbergen",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp).clip(CircleShape).clickable { Bank.updateSettings { it.copy(hideBalance = !it.hideBalance) } }.padding(6.dp),
                )
            }
            AnimatedContent(hidden, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "hide") { h ->
                Text(
                    if (h) "•••••• ${s.settings.currencySymbol}" else Money.format(if (kotlin.math.abs(animated - me.balance.toFloat()) < 1f) me.balance else animated.toLong(), s.settings),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 38.sp, maxLines = 1,
                )
            }
            Gap(6.dp)
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).clickable { copy(context, me.id, "Konto-ID kopiert") }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(me.id, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ContentCopy, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Gap(18.dp)
            Row(Modifier.fillMaxWidth()) {
                MiniStat("Eingang", if (hidden) "••••" else "+" + Money.format(inMonth, s.settings), Modifier.weight(1f))
                MiniStat("Ausgang", if (hidden) "••••" else "−" + Money.format(outMonth, s.settings), Modifier.weight(1f))
                MiniStat("Gespart", if (hidden) "••••" else Money.format(saved, s.settings), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class Action(val label: String, val icon: ImageVector, val route: Route)

@Composable
private fun QuickActions() {
    val nav = LocalNav.current
    val tap = haptic()
    val actions = listOf(
        Action("Senden", Icons.AutoMirrored.Rounded.Send, Route.Send()),
        Action("Anfordern", Icons.AutoMirrored.Rounded.CallReceived, Route.Request()),
        Action("Aufladen", Icons.Rounded.AddCard, Route.TopUp),
        Action("Teilen", Icons.AutoMirrored.Rounded.CallSplit, Route.Split),
        Action("QR-Code", Icons.Rounded.QrCode2, Route.MyQr),
        Action("Kontakte", Icons.Rounded.Contacts, Route.Contacts),
        Action("Daueraufträge", Icons.Rounded.Repeat, Route.Recurring),
        Action("Live", Icons.Rounded.Sensors, Route.Live),
    )
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { a ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { tap(); nav.push(a.route) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(54.dp).clip(RoundedCornerShape(19.dp)).background(O.c.surface).border(1.dp, O.c.border, RoundedCornerShape(19.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(a.icon, null, tint = O.c.accent.a, modifier = Modifier.size(24.dp)) }
                        Gap(6.dp)
                        Text(a.label, color = O.c.text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun RequestsPanel(incoming: List<PayRequest>, outgoingCount: Int, s: BankState) {
    val nav = LocalNav.current
    Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        if (incoming.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✅", fontSize = 22.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Keine offenen Anfragen", color = O.c.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (outgoingCount > 0) "Du wartest auf $outgoingCount Zahlung(en)" else "Alles erledigt.",
                        color = O.c.muted, fontSize = 13.sp,
                    )
                }
            }
        } else {
            incoming.take(3).forEachIndexed { i, r ->
                if (i > 0) Gap(12.dp)
                IncomingRequest(r, s)
            }
            if (incoming.size > 3) {
                Gap(8.dp)
                Text("+${incoming.size - 3} weitere", color = O.c.accent.a, modifier = Modifier.clickable { nav.push(Route.Requests) })
            }
        }
    }
}

@Composable
fun IncomingRequest(r: PayRequest, s: BankState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(r.requesterName, colorFor(r.requester), 42.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(r.requesterName, color = O.c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.note.ifBlank { Dates.relative(r.time) }, color = O.c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Pill(Money.format(r.amount, s.settings), O.c.warning)
    }
    Gap(10.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Ablehnen", color = O.c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(O.c.surface2)
                .clickable { Bank.setRequestStatus(r.id, RequestStatus.DECLINED); Toast.show("Anfrage abgelehnt") }
                .padding(vertical = 11.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "Bezahlen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(O.c.gradient)
                .clickable {
                    when (val res = Bank.payRequest(r.id)) {
                        is Bank.Result.Ok -> Toast.show("Bezahlt: ${Money.format(r.amount, s.settings)}")
                        is Bank.Result.Error -> Toast.show(res.message)
                    }
                }
                .padding(vertical = 11.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun LiveBanner(s: BankState) {
    val nav = LocalNav.current
    val today = Dates.startOfDay(System.currentTimeMillis())
    val count = s.txns.count { it.time >= today }
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(O.c.text)
            .clickable { nav.push(Route.Live) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot()
        Spacer(Modifier.width(10.dp))
        Text("Live-Transaktionen", color = O.c.bg, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("$count heute", color = O.c.bg.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}
