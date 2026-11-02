package app.orbis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Category
import app.orbis.data.Dates
import app.orbis.data.Money
import app.orbis.data.TxnKind
import app.orbis.ui.LocalNav
import app.orbis.ui.O
import app.orbis.ui.Route
import app.orbis.ui.components.Avatar
import app.orbis.ui.components.Bars
import app.orbis.ui.components.Donut
import app.orbis.ui.components.Gap
import app.orbis.ui.components.Panel
import app.orbis.ui.components.ProgressBar
import app.orbis.ui.components.SectionTitle
import app.orbis.ui.components.Slice
import app.orbis.ui.components.colorFor

@Composable
fun StatsTab() {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    var month by remember { mutableLongStateOf(Dates.startOfMonth(System.currentTimeMillis())) }
    val next = Dates.addMonths(month, 1)
    val isCurrent = next > System.currentTimeMillis()

    val txns = remember(s.txns, me.id, month) {
        s.txns.filter { (it.from == me.id || it.to == me.id) && it.time >= month && it.time < next }
    }
    // Money moved into savings goals is neither income nor spending.
    val spending = txns.filter { it.from == me.id && it.kind != TxnKind.GOAL_IN }
    val income = txns.filter { it.to == me.id && it.kind != TxnKind.GOAL_OUT }
    val spent = spending.sumOf { it.amount }
    val earned = income.sumOf { it.amount }
    val byCat = spending.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }.toList().sortedByDescending { it.second }

    val days = Dates.daysInMonth(month)
    val perWeek = remember(spending, month) {
        // Spending per week of the month (1–7, 8–14, …).
        val weeks = LongArray((days + 6) / 7)
        spending.forEach { t ->
            val d = ((t.time - month) / Dates.DAY).toInt().coerceIn(0, days - 1)
            weeks[d / 7] += t.amount
        }
        weeks.mapIndexed { i, v -> "W${i + 1}" to v }
    }
    val last7 = remember(s.txns, me.id) {
        val today = Dates.startOfDay(System.currentTimeMillis())
        (6 downTo 0).map { back ->
            val start = today - back * Dates.DAY
            Dates.weekday(start) to s.txns.filter { it.from == me.id && it.kind != TxnKind.GOAL_IN && it.time >= start && it.time < start + Dates.DAY }.sumOf { it.amount }
        }
    }
    val topPeople = spending.filter { it.kind == TxnKind.TRANSFER || it.kind == TxnKind.REQUEST || it.kind == TxnKind.RECURRING }
        .groupBy { it.to.orEmpty() }.map { (id, l) -> Triple(id, l.first().toName, l.sumOf { it.amount }) }
        .sortedByDescending { it.third }.take(5)
    val budgets = s.budgets.filter { it.owner == me.id }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomBarSpace)) {
        item {
            Column(Modifier.statusBarsPadding().padding(20.dp, 16.dp, 20.dp, 4.dp)) {
                Text("Statistik", style = MaterialTheme.typography.headlineLarge, color = O.c.text)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = Dates.addMonths(month, -1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Vorheriger Monat", tint = O.c.text)
                }
                Text(Dates.month(month), color = O.c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { if (!isCurrent) month = next }, enabled = !isCurrent) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Nächster Monat", tint = if (isCurrent) O.c.border else O.c.text)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Einnahmen", Money.format(earned, s.settings), O.c.positive, Modifier.weight(1f))
                StatBox("Ausgaben", Money.format(spent, s.settings), O.c.negative, Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Bilanz", Money.format(earned - spent, s.settings, signed = true), O.c.accent.a, Modifier.weight(1f))
                StatBox("Buchungen", "${txns.size}", O.c.accent.b, Modifier.weight(1f))
            }
        }
        item {
            SectionTitle("Ausgaben nach Kategorie")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                if (byCat.isEmpty()) {
                    Text("Keine Ausgaben in diesem Monat.", color = O.c.muted)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(
                            byCat.map { (k, v) -> Slice(v.toFloat(), Color(Category.of(k).color)) },
                            Modifier.size(150.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Gesamt", color = O.c.muted, fontSize = 12.sp)
                                Text(Money.plain(spent), color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            byCat.take(6).forEach { (k, v) ->
                                val c = Category.of(k)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color)))
                                    Text(c.label, color = O.c.text, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                                    Text("${(v * 100 / spent.coerceAtLeast(1))}%", color = O.c.muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Ausgaben pro Woche")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Bars(perWeek, O.c.accent.a, highlightLast = false)
            }
        }
        item {
            SectionTitle("Letzte 7 Tage")
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Text("Ø ${Money.format(last7.sumOf { it.second } / 7, s.settings)} pro Tag", color = O.c.muted, fontSize = 13.sp)
                Gap(12.dp)
                Bars(last7, O.c.accent.b)
            }
        }
        item {
            SectionTitle("Budgets", action = "Verwalten") { nav.push(Route.Budgets) }
            Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = if (budgets.isEmpty()) ({ nav.push(Route.Budgets) }) else null) {
                if (budgets.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, tint = O.c.accent.a)
                        Text("Monatsbudgets für Kategorien festlegen", color = O.c.text, modifier = Modifier.padding(start = 12.dp))
                    }
                } else {
                    budgets.forEachIndexed { i, b ->
                        if (i > 0) Gap(14.dp)
                        val c = Category.of(b.category)
                        val used = byCat.firstOrNull { it.first == b.category }?.second ?: 0L
                        val over = used > b.limit
                        Row {
                            Text("${c.emoji} ${c.label}", color = O.c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                "${Money.plain(used)} / ${Money.format(b.limit, s.settings)}",
                                color = if (over) O.c.negative else O.c.muted, fontSize = 13.sp,
                            )
                        }
                        Gap(6.dp)
                        ProgressBar(used.toFloat() / b.limit, if (over) O.c.negative else Color(c.color))
                    }
                }
            }
        }
        if (topPeople.isNotEmpty()) {
            item {
                SectionTitle("Top-Empfänger")
                Panel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    topPeople.forEachIndexed { i, (id, name, sum) ->
                        if (i > 0) Gap(12.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name, colorFor(id), 38.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(name, color = O.c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(id, color = O.c.muted, fontSize = 12.sp)
                            }
                            Text(Money.format(sum, s.settings), color = O.c.text, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier) {
    Panel(modifier, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(label, color = O.c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Gap(6.dp)
        Text(value, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
