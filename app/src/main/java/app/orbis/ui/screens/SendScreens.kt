package app.orbis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Bank
import app.orbis.data.Category
import app.orbis.data.Money
import app.orbis.data.Security
import app.orbis.data.Txn
import app.orbis.ui.Biometric
import app.orbis.ui.LocalNav
import app.orbis.ui.O
import app.orbis.ui.Route
import app.orbis.ui.Toast
import app.orbis.ui.components.Avatar
import app.orbis.ui.components.Field
import app.orbis.ui.components.Gap
import app.orbis.ui.components.GradientButton
import app.orbis.ui.components.OrbisSwitch
import app.orbis.ui.components.Panel
import app.orbis.ui.components.PinDots
import app.orbis.ui.components.PinPad
import app.orbis.ui.components.SoftButton
import app.orbis.ui.components.SubScreen
import app.orbis.ui.components.colorFor
import kotlinx.coroutines.delay

// =============================================================== Send

@Composable
fun SendScreen(route: Route.Send) {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    val context = LocalContext.current

    val initialParty = route.to?.uppercase()?.let { id -> knownParties(s, me.id).firstOrNull { it.id == id } ?: Party(id, Bank.nameFor(me.id, id), local = s.account(id) != null) }
    var party by remember { mutableStateOf(initialParty) }
    var amount by remember { mutableStateOf(route.amount?.let { Money.plain(it).replace(".", "").removeSuffix(",00") } ?: "") }
    var note by remember { mutableStateOf(route.note ?: "") }
    var category by remember { mutableStateOf(Category.OTHER.key) }
    var step by remember { mutableIntStateOf(if (initialParty == null) 0 else 1) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<Txn?>(null) }

    BackHandler(enabled = step in 1..2) { step--; error = null }

    fun execute() {
        val p = party ?: return
        when (val r = Bank.transfer(me.id, p.id, Money.fromInput(amount), note, category)) {
            is Bank.Result.Ok -> { done = r.txn; step = 3 }
            is Bank.Result.Error -> { error = r.message; step = 1 }
        }
    }

    SubScreen(
        when (step) {
            0 -> "Senden an"
            1 -> "Betrag"
            2 -> "Bestätigen"
            else -> "Gesendet"
        },
    ) {
        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "send") { st ->
            when (st) {
                0 -> RecipientPicker(s, me, "Wähle, wer das Geld bekommt") { p -> party = p; step = 1 }
                1 -> AmountEntry(
                    party = party, s = s, balance = me.balance,
                    amount = amount, onAmount = { amount = it; error = null },
                    note = note, onNote = { note = it },
                    category = category, onCategory = { category = it },
                    button = "Weiter", error = error,
                ) { step = 2 }
                2 -> ConfirmTransfer(
                    title = "Überweisung bestätigen",
                    lines = listOf(
                        "An" to "${party?.name} (${party?.id})",
                        "Betrag" to Money.format(Money.fromInput(amount), s.settings),
                        "Kategorie" to Category.of(category).let { "${it.emoji} ${it.label}" },
                        "Notiz" to note.ifBlank { "–" },
                        "Danach" to Money.format(me.balance - Money.fromInput(amount), s.settings),
                    ),
                    requirePin = me.pinForTransfers,
                    pinOk = { Security.check(it, me) },
                    biometric = me.biometric && Biometric.available(context),
                    onConfirm = { execute() },
                )
                else -> {
                    val t = done
                    val p = party
                    SuccessView(
                        "${Money.format(t?.amount ?: 0, s.settings)} gesendet",
                        "an ${p?.name ?: ""}${if (p?.local == false) "\nDie Empfänger-ID liegt nicht auf diesem Gerät." else ""}",
                    ) {
                        val isContact = s.contacts.any { it.owner == me.id && it.accountId == p?.id }
                        if (p != null && !isContact) {
                            SoftButton("Als Kontakt speichern", Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd) {
                                Bank.saveContact(me.id, p.id, p.name)
                                Toast.show("Kontakt gespeichert")
                            }
                            Gap(10.dp)
                        }
                        if (t != null) {
                            SoftButton("Beleg teilen", Modifier.fillMaxWidth(), icon = Icons.Rounded.Share) { share(context, receipt(t, me.id, s)) }
                            Gap(10.dp)
                        }
                        GradientButton("Fertig", Modifier.fillMaxWidth()) { nav.pop() }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmTransfer(
    title: String,
    lines: List<Pair<String, String>>,
    requirePin: Boolean,
    pinOk: (String) -> Boolean,
    biometric: Boolean,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            if (pinOk(pin)) onConfirm() else {
                wrong = true; delay(600); wrong = false; pin = ""
            }
        }
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Panel(Modifier.fillMaxWidth()) {
            Text(title, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Gap(10.dp)
            lines.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(k, color = O.c.muted, modifier = Modifier.weight(0.35f), fontSize = 14.sp)
                    Text(v, color = O.c.text, modifier = Modifier.weight(0.65f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (requirePin) {
            Text(if (wrong) "Falsche PIN" else "Mit PIN bestätigen", color = if (wrong) O.c.negative else O.c.muted)
            Gap(14.dp)
            PinDots(4, pin.length, wrong)
            Gap(16.dp)
            PinPad(
                onDigit = { if (pin.length < 4) pin += it },
                onDelete = { pin = pin.dropLast(1) },
                onBiometric = if (biometric) ({ Biometric.prompt(context, "Zahlung bestätigen") { onConfirm() } }) else null,
            )
        } else {
            GradientButton("Jetzt senden", Modifier.fillMaxWidth(), icon = Icons.Rounded.Check, onClick = onConfirm)
        }
        Gap(16.dp)
    }
}

// =============================================================== Request

@Composable
fun RequestScreen(route: Route.Request) {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    val context = LocalContext.current
    val initial = route.from?.let { id -> Party(id, Bank.nameFor(me.id, id), local = s.account(id) != null) }
    var party by remember { mutableStateOf(initial) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(if (initial == null) 0 else 1) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = step == 1) { step = 0 }

    SubScreen(if (step == 0) "Anfordern von" else if (step == 1) "Betrag anfordern" else "Angefordert") {
        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "req") { st ->
            when (st) {
                0 -> RecipientPicker(s, me, "Von wem möchtest du Geld?") { party = it; step = 1 }
                1 -> AmountEntry(
                    party = party, s = s, balance = null,
                    amount = amount, onAmount = { amount = it; error = null },
                    note = note, onNote = { note = it },
                    category = null, onCategory = null,
                    button = "Anfrage senden", error = error,
                ) {
                    val p = party ?: return@AmountEntry
                    val err = Bank.request(me.id, p.id, Money.fromInput(amount), note)
                    if (err == null) step = 2 else error = err
                }
                else -> {
                    val p = party
                    val cents = Money.fromInput(amount)
                    SuccessView(
                        "${Money.format(cents, s.settings)} angefordert",
                        if (p?.local == true) "${p?.name} sieht die Anfrage beim nächsten Anmelden."
                        else "Teile den Zahlungslink mit ${p?.name ?: "der Person"}.",
                    ) {
                        SoftButton("Zahlungslink teilen", Modifier.fillMaxWidth(), icon = Icons.Rounded.Share) {
                            share(context, "${me.name} bittet dich um ${Money.format(cents, s.settings)}${if (note.isNotBlank()) " für „$note“" else ""}.\n${payLink(me.id, cents, note)}")
                        }
                        Gap(10.dp)
                        GradientButton("Fertig", Modifier.fillMaxWidth()) { nav.pop() }
                    }
                }
            }
        }
    }
}

// =============================================================== Split the bill

@Composable
fun SplitScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    val parties = remember(s, me.id) { knownParties(s, me.id) }
    val chosen = remember { mutableStateListOf<String>() }
    var total by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var includeMe by remember { mutableStateOf(true) }
    var manualId by remember { mutableStateOf("") }

    val totalCents = Money.fromInput(total)
    val people = chosen.size + if (includeMe) 1 else 0
    val share = if (people > 0) totalCents / people else 0

    SubScreen("Rechnung teilen") {
        LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Field(total, { v -> total = v.filter { it.isDigit() || it == ',' }.take(10) }, "Gesamtbetrag", keyboard = KeyboardType.Decimal)
                    Gap(10.dp)
                    Field(note, { note = it.take(60) }, "Wofür? z. B. Pizza-Abend")
                    Gap(10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ich zahle meinen Anteil selbst", color = O.c.text, modifier = Modifier.weight(1f))
                        OrbisSwitch(includeMe) { includeMe = it }
                    }
                    Gap(10.dp)
                    Panel(Modifier.fillMaxWidth()) {
                        Text("Pro Person", color = O.c.muted, fontSize = 13.sp)
                        Text(Money.format(share, s.settings), color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("$people Personen · ${chosen.size} Anfragen", color = O.c.muted, fontSize = 13.sp)
                    }
                    Gap(14.dp)
                    Text("Mit wem?", color = O.c.text, fontWeight = FontWeight.SemiBold)
                    Gap(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(manualId, { manualId = it.uppercase().take(9) }, "Konto-ID hinzufügen", Modifier.weight(1f))
                        val ok = IdPattern.matches(manualId) && manualId != me.id && manualId !in chosen
                        Text(
                            "Add", color = if (ok) O.c.accent.a else O.c.muted, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = ok) { chosen.add(manualId); manualId = "" }.padding(12.dp),
                        )
                    }
                }
            }
            val extra = chosen.filter { id -> parties.none { it.id == id } }.map { Party(it, it) }
            items(parties + extra, key = { it.id }) { p ->
                val on = p.id in chosen
                Row(
                    Modifier.fillMaxWidth().clickable { if (on) chosen.remove(p.id) else chosen.add(p.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(p.name, colorFor(p.id), 42.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.name, color = O.c.text, fontWeight = FontWeight.SemiBold)
                        Text(p.id, color = O.c.muted, fontSize = 12.sp)
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (on) O.c.accent.a else O.c.surface2)
                            .border(1.dp, O.c.border, RoundedCornerShape(50)).padding(6.dp),
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = if (on) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.padding(0.dp))
                    }
                }
            }
        }
        GradientButton(
            "${chosen.size} Anfragen senden",
            Modifier.navigationBarsPadding().padding(20.dp).fillMaxWidth(),
            enabled = chosen.isNotEmpty() && share > 0,
        ) {
            val group = Bank.newGroupId()
            chosen.forEach { Bank.request(me.id, it, share, note.ifBlank { "Geteilte Rechnung" }, group) }
            Toast.show("${chosen.size} Anfragen über je ${Money.format(share, s.settings)} gesendet")
            nav.pop()
        }
    }
}

// =============================================================== Top up

@Composable
fun TopUpScreen() {
    val s = bankState()
    val me = currentAccount() ?: return
    val nav = LocalNav.current
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    SubScreen("Aufladen") {
        if (done) {
            SuccessView("Aufgeladen", "Neuer Kontostand: ${Money.format(me.balance, s.settings)}") {
                GradientButton("Fertig", Modifier.fillMaxWidth()) { nav.pop() }
            }
        } else {
            AmountEntry(
                party = null, s = s, balance = null,
                amount = amount, onAmount = { amount = it },
                note = note, onNote = { note = it },
                category = null, onCategory = null,
                button = "Einzahlen", error = null,
            ) {
                Bank.topUp(me.id, Money.fromInput(amount), note.ifBlank { "Einzahlung" })
                done = true
            }
        }
    }
}
