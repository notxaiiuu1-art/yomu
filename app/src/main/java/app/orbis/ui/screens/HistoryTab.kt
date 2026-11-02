package app.orbis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import app.orbis.data.Category
import app.orbis.data.Dates
import app.orbis.data.Money
import app.orbis.data.Txn
import app.orbis.data.TxnKind
import app.orbis.ui.O
import app.orbis.ui.components.Chip
import app.orbis.ui.components.Empty
import app.orbis.ui.components.TxnRow

private enum class Filter(val label: String) { ALL("Alle"), IN("Eingang"), OUT("Ausgang"), GOALS("Sparen"), AUTO("Daueraufträge") }

@Composable
fun HistoryTab() {
    val s = bankState()
    val me = currentAccount() ?: return
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var category by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Txn?>(null) }

    val list = remember(s.txns, me.id, query, filter, category) {
        s.txns.asSequence()
            .filter { it.from == me.id || it.to == me.id }
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.IN -> it.to == me.id && it.kind != TxnKind.GOAL_OUT
                    Filter.OUT -> it.from == me.id && it.kind != TxnKind.GOAL_IN
                    Filter.GOALS -> it.kind == TxnKind.GOAL_IN || it.kind == TxnKind.GOAL_OUT
                    Filter.AUTO -> it.kind == TxnKind.RECURRING
                }
            }
            .filter { category == null || it.category == category }
            .filter {
                query.isBlank() || listOf(it.fromName, it.toName, it.note, it.from.orEmpty(), it.to.orEmpty(), Money.plain(it.amount))
                    .any { f -> f.contains(query.trim(), ignoreCase = true) }
            }
            .sortedByDescending { it.time }
            .toList()
    }
    val grouped = remember(list) { list.groupBy { Dates.startOfDay(it.time) } }
    val inSum = list.filter { it.to == me.id }.sumOf { it.amount }
    val outSum = list.filter { it.from == me.id }.sumOf { it.amount }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomBarSpace)) {
        item {
            Column(Modifier.statusBarsPadding().padding(20.dp, 16.dp, 20.dp, 8.dp)) {
                Text("Verlauf", style = MaterialTheme.typography.headlineLarge, color = O.c.text)
                Text("${list.size} Buchungen", color = O.c.muted)
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(O.c.surface)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = O.c.muted)
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (query.isEmpty()) Text("Name, Notiz, Betrag, ID…", color = O.c.muted)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = O.c.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(O.c.accent.a),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Rounded.Close, "Leeren", tint = O.c.muted, modifier = Modifier.size(22.dp).clickable { query = "" })
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries) { f -> Chip(f.label, filter == f, { filter = f }) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries) { c ->
                    Chip(c.label, category == c.key, { category = if (category == c.key) null else c.key }, leading = c.emoji)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SumBox("Eingang", "+" + Money.format(inSum, s.settings), O.c.positive, Modifier.weight(1f))
                SumBox("Ausgang", "−" + Money.format(outSum, s.settings), O.c.negative, Modifier.weight(1f))
            }
        }
        if (list.isEmpty()) {
            item { Empty("🔎", "Nichts gefunden", "Ändere die Suche oder die Filter.") }
        }
        grouped.forEach { (day, txns) ->
            item(key = "h$day") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)) {
                    Text(Dates.dayHeader(day), color = O.c.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    val net = txns.sumOf { if (it.to == me.id) it.amount else -it.amount }
                    Text(Money.format(net, s.settings, signed = true), color = O.c.muted, fontSize = 13.sp)
                }
            }
            items(txns, key = { it.id }) { t ->
                TxnRow(t, me.id, s.settings, s.settings.hideBalance) { selected = t }
            }
        }
    }
    selected?.let { t -> TxnSheet(t, me.id) { selected = null } }
}

@Composable
private fun SumBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.12f)).padding(14.dp)) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}
