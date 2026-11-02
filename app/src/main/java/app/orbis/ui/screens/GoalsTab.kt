package app.orbis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Bank
import app.orbis.data.Goal
import app.orbis.data.Money
import app.orbis.ui.O
import app.orbis.ui.Swatches
import app.orbis.ui.Toast
import app.orbis.ui.components.Empty
import app.orbis.ui.components.Field
import app.orbis.ui.components.Gap
import app.orbis.ui.components.GradientButton
import app.orbis.ui.components.Panel
import app.orbis.ui.components.Ring
import app.orbis.ui.components.SoftButton
import app.orbis.ui.swatch

private val GoalEmojis = listOf("🎯", "🏖️", "🎮", "🚗", "🏠", "💻", "📱", "🎁", "✈️", "🎓", "💍", "🐶", "🎸", "👟", "🛟", "⭐")

@Composable
fun GoalsTab() {
    val s = bankState()
    val me = currentAccount() ?: return
    val goals = s.goals.filter { it.owner == me.id }
    var creating by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    val totalSaved = goals.sumOf { it.saved }
    val totalTarget = goals.sumOf { it.target }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomBarSpace)) {
        item {
            Row(Modifier.statusBarsPadding().padding(20.dp, 16.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sparziele", style = MaterialTheme.typography.headlineLarge, color = O.c.text)
                    Text("${goals.size} Ziele", color = O.c.muted)
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(O.c.gradient).clickable { creating = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Neues Sparziel", tint = Color.White) }
            }
        }
        if (goals.isNotEmpty()) {
            item {
                Panel(Modifier.padding(16.dp, 8.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(if (totalTarget > 0) totalSaved.toFloat() / totalTarget else 0f, O.c.accent.a, 74.dp, 9.dp) {
                            Text("${if (totalTarget > 0) totalSaved * 100 / totalTarget else 0}%", color = O.c.text, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.padding(start = 18.dp)) {
                            Text("Insgesamt gespart", color = O.c.muted, fontSize = 13.sp)
                            Text(Money.format(totalSaved, s.settings), color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("von ${Money.format(totalTarget, s.settings)}", color = O.c.muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            item { Empty("🐷", "Noch keine Sparziele", "Tippe auf + und spare auf etwas, das dir wichtig ist.", Modifier.padding(top = 40.dp)) }
        }
        items(goals, key = { it.id }) { g -> GoalCard(g, s.settings) { open = g.id } }
    }

    if (creating) CreateGoalSheet(me.id) { creating = false }
    open?.let { id ->
        val g = goals.firstOrNull { it.id == id }
        if (g == null) open = null else GoalSheet(g, me.balance) { open = null }
    }
}

@Composable
private fun GoalCard(g: Goal, settings: app.orbis.data.Settings, onClick: () -> Unit) {
    val p = if (g.target > 0) g.saved.toFloat() / g.target else 0f
    val color = swatch(g.colorIndex)
    Panel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(p, color, 62.dp, 7.dp) { Text(g.emoji, fontSize = 24.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(g.name, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Money.format(g.saved, settings)} von ${Money.format(g.target, settings)}", color = O.c.muted, fontSize = 13.sp)
                if (g.saved >= g.target) Text("🎉 Ziel erreicht!", color = O.c.positive, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                else Text("Noch ${Money.format(g.target - g.saved, settings)}", color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("${(p * 100).toInt().coerceAtMost(999)}%", color = O.c.text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CreateGoalSheet(owner: String, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(GoalEmojis.first()) }
    var color by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = O.c.surface) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
            Text("Neues Sparziel", style = MaterialTheme.typography.titleLarge, color = O.c.text)
            Gap(14.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalEmojis.forEach { e ->
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (e == emoji) O.c.accent.a.copy(alpha = 0.2f) else O.c.surface2)
                            .border(2.dp, if (e == emoji) O.c.accent.a else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 22.sp) }
                }
            }
            Gap(14.dp)
            Field(name, { name = it.take(30) }, "Name, z. B. Urlaub")
            Gap(10.dp)
            Field(target, { v -> target = v.filter { it.isDigit() || it == ',' }.take(10) }, "Zielbetrag", keyboard = KeyboardType.Decimal)
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatches.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(c)
                            .border(3.dp, if (i == color) O.c.text else Color.Transparent, CircleShape)
                            .clickable { color = i },
                    )
                }
            }
            Gap(20.dp)
            val amount = Money.fromInput(target)
            GradientButton("Sparziel erstellen", Modifier.fillMaxWidth(), enabled = name.isNotBlank() && amount > 0) {
                Bank.createGoal(owner, name, emoji, amount, color)
                Toast.show("Sparziel „$name“ erstellt")
                onDone()
            }
        }
    }
}

@Composable
private fun GoalSheet(g: Goal, balance: Long, onDone: () -> Unit) {
    val s = bankState()
    var amount by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val cents = Money.fromInput(amount)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = O.c.surface) {
        Column(
            Modifier.navigationBarsPadding().imePadding().padding(horizontal = 22.dp).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ring(if (g.target > 0) g.saved.toFloat() / g.target else 0f, swatch(g.colorIndex), 110.dp, 10.dp) {
                Text(g.emoji, fontSize = 40.sp)
            }
            Gap(12.dp)
            Text(g.name, style = MaterialTheme.typography.titleLarge, color = O.c.text)
            Text("${Money.format(g.saved, s.settings)} von ${Money.format(g.target, s.settings)}", color = O.c.muted)
            Gap(16.dp)
            Field(amount, { v -> amount = v.filter { it.isDigit() || it == ',' }.take(10) }, "Betrag", keyboard = KeyboardType.Decimal)
            Text("Verfügbar: ${Money.format(balance, s.settings)}", color = O.c.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Gap(10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500L, 1000L, 2500L, 5000L).forEach { q ->
                    Text(
                        "+${Money.plain(q).removeSuffix(",00")}",
                        color = O.c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(O.c.surface2)
                            .clickable { amount = Money.plain(Money.fromInput(amount) + q).replace(".", "") }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Gap(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Entnehmen", Modifier.weight(1f), icon = Icons.Rounded.Remove) {
                    val err = Bank.moveGoal(g.id, -cents)
                    Toast.show(err ?: "${Money.format(cents, s.settings)} entnommen")
                    if (err == null) amount = ""
                }
                GradientButton("Einzahlen", Modifier.weight(1f), enabled = cents > 0, icon = Icons.Rounded.Add) {
                    val err = Bank.moveGoal(g.id, cents)
                    if (err == null) {
                        amount = ""
                        Toast.show(if (g.saved + cents >= g.target && g.saved < g.target) "🎉 Ziel erreicht!" else "${Money.format(cents, s.settings)} gespart")
                    } else Toast.show(err)
                }
            }
            Gap(10.dp)
            SoftButton(
                if (confirmDelete) "Wirklich löschen? Guthaben geht zurück" else "Sparziel löschen",
                Modifier.fillMaxWidth(), icon = Icons.Rounded.Delete, color = O.c.negative,
            ) {
                if (confirmDelete) {
                    Bank.deleteGoal(g.id)
                    Toast.show("Sparziel gelöscht")
                    onDone()
                } else confirmDelete = true
            }
        }
    }
}
