package app.orbis.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Account
import app.orbis.data.BankState
import app.orbis.data.Category
import app.orbis.data.Money
import app.orbis.ui.O
import app.orbis.ui.components.Avatar
import app.orbis.ui.components.Chip
import app.orbis.ui.components.Gap
import app.orbis.ui.components.GradientButton
import app.orbis.ui.components.NumPad
import app.orbis.ui.components.applyKey
import app.orbis.ui.components.colorFor

/** Someone money can go to or come from. */
data class Party(val id: String, val name: String, val favorite: Boolean = false, val local: Boolean = false)

val IdPattern = Regex("^[A-Z0-9]{1,3}-[A-Z0-9]{5}$")

/** Everyone the signed-in account knows: contacts, other accounts on this device, recent partners. */
fun knownParties(s: BankState, me: String): List<Party> {
    val out = LinkedHashMap<String, Party>()
    s.contacts.filter { it.owner == me }.sortedByDescending { it.favorite }.forEach {
        out[it.accountId] = Party(it.accountId, it.name, it.favorite, s.account(it.accountId) != null)
    }
    s.txns.filter { it.from == me && it.to != null }.sortedByDescending { it.time }.forEach { t ->
        val id = t.to!!
        if (id !in out && id != me) out[id] = Party(id, t.toName, local = s.account(id) != null)
    }
    s.txns.filter { it.to == me && it.from != null }.sortedByDescending { it.time }.forEach { t ->
        val id = t.from!!
        if (id !in out && id != me) out[id] = Party(id, t.fromName, local = s.account(id) != null)
    }
    s.accounts.filter { it.id != me }.forEach {
        if (it.id !in out) out[it.id] = Party(it.id, it.name, local = true)
    }
    return out.values.toList()
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, hint: String) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(O.c.surface)
            .border(1.dp, O.c.border, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = O.c.muted)
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (value.isEmpty()) Text(hint, color = O.c.muted)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = O.c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(O.c.accent.a),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(Icons.Rounded.Close, "Leeren", tint = O.c.muted, modifier = Modifier.size(22.dp).clickable { onChange("") })
    }
}

@Composable
fun RecipientPicker(s: BankState, me: Account, title: String, onPick: (Party) -> Unit) {
    var query by remember { mutableStateOf("") }
    val parties = remember(s, me.id) { knownParties(s, me.id) }
    val q = query.trim()
    val filtered = parties.filter { q.isEmpty() || it.name.contains(q, true) || it.id.contains(q, true) }
    val typedId = q.uppercase()
    val canUseTyped = IdPattern.matches(typedId) && typedId != me.id && parties.none { it.id == typedId }

    Column(Modifier.fillMaxSize()) {
        Text(title, color = O.c.muted, modifier = Modifier.padding(horizontal = 20.dp))
        SearchBox(query, { query = it }, "Name oder Konto-ID (z. B. OR-12ABC)")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (canUseTyped) {
                item {
                    val local = s.account(typedId)
                    PartyRow(Party(typedId, local?.name ?: typedId, local = local != null), subtitle = if (local != null) typedId else "Neue Konto-ID") {
                        onPick(Party(typedId, local?.name ?: typedId, local = local != null))
                    }
                }
            }
            val favs = filtered.filter { it.favorite }
            if (favs.isNotEmpty() && q.isEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(favs, key = { it.id }) { p ->
                            Column(
                                Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(p) }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(p.name, colorFor(p.id), 56.dp)
                                Gap(6.dp)
                                Text(p.name.substringBefore(' '), color = O.c.text, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty() && !canUseTyped) {
                item {
                    Text(
                        if (q.isEmpty()) "Noch niemand hier. Gib eine Konto-ID ein." else "Keine Treffer. Konto-IDs sehen so aus: ${me.id}",
                        color = O.c.muted, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center,
                    )
                }
            }
            items(filtered, key = { it.id }) { p ->
                PartyRow(p, subtitle = p.id + if (p.local) " · auf diesem Gerät" else "") { onPick(p) }
            }
        }
    }
}

@Composable
fun PartyRow(p: Party, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name, colorFor(p.id), 46.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(p.name, color = O.c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = O.c.muted, fontSize = 13.sp)
        }
        if (p.favorite) Icon(Icons.Rounded.Star, null, tint = O.c.warning, modifier = Modifier.size(18.dp))
    }
}

/** Amount keypad step with note and category. */
@Composable
fun AmountEntry(
    party: Party?,
    s: BankState,
    balance: Long?,
    amount: String,
    onAmount: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    category: String?,
    onCategory: ((String) -> Unit)?,
    button: String,
    error: String?,
    onContinue: () -> Unit,
) {
    var editingNote by remember { mutableStateOf(false) }
    val cents = Money.fromInput(amount)
    val over = balance != null && cents > balance
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        if (party != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(party.name, colorFor(party.id), 40.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(party.name, color = O.c.text, fontWeight = FontWeight.SemiBold)
                    Text(party.id, color = O.c.muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            (if (amount.isEmpty()) "0" else amount) + " " + s.settings.currencySymbol,
            color = if (over) O.c.negative else O.c.text,
            fontSize = if (amount.length > 8) 44.sp else 56.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
        )
        Text(
            when {
                error != null -> error
                over -> "Nicht genug Guthaben"
                balance != null -> "Verfügbar: ${Money.format(balance, s.settings)}"
                else -> " "
            },
            color = if (error != null || over) O.c.negative else O.c.muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 14.sp,
        )
        Gap(12.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf(500L, 1000L, 2000L, 5000L).forEach { q ->
                Text(
                    Money.plain(q).removeSuffix(",00"),
                    color = O.c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(50)).background(O.c.surface2)
                        .clickable { onAmount(Money.plain(q).removeSuffix(",00").replace(".", "")) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        Gap(10.dp)
        // Note
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(O.c.surface)
                .clickable { editingNote = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Edit, null, tint = O.c.muted, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                if (note.isEmpty()) Text("Notiz hinzufügen", color = O.c.muted)
                BasicTextField(
                    note, { onNote(it.take(80)) }, singleLine = true,
                    textStyle = TextStyle(color = O.c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(O.c.accent.a),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (onCategory != null) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries.filter { it != Category.SALARY }) { c ->
                    Chip(c.label, category == c.key, { onCategory(c.key) }, leading = c.emoji)
                }
            }
        } else Gap(10.dp)
        NumPad(onKey = { onAmount(applyKey(amount, it)) }, modifier = Modifier.padding(horizontal = 16.dp))
        Gap(10.dp)
        GradientButton(button, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), enabled = cents > 0 && !over, onClick = onContinue)
        Gap(12.dp)
    }
}

/** Big animated check mark with a title, used after money moved. */
@Composable
fun SuccessView(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(120.dp).scale(scale.value).clip(CircleShape).background(O.c.gradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
        Gap(24.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = O.c.text, textAlign = TextAlign.Center)
        Gap(6.dp)
        Text(subtitle, color = O.c.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        content()
    }
}
