package app.orbis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Account
import app.orbis.data.Bank
import app.orbis.data.Money
import app.orbis.data.Security
import app.orbis.ui.Biometric
import app.orbis.ui.O
import app.orbis.ui.Swatches
import app.orbis.ui.Toast
import app.orbis.ui.components.Avatar
import app.orbis.ui.components.Field
import app.orbis.ui.components.Gap
import app.orbis.ui.components.GradientButton
import app.orbis.ui.components.PinDots
import app.orbis.ui.components.PinPad
import app.orbis.ui.components.SoftButton
import app.orbis.ui.swatch
import kotlinx.coroutines.delay

private sealed interface AuthStep {
    data object Pick : AuthStep
    data class Pin(val id: String) : AuthStep
    data object Create : AuthStep
}

/**
 * Sign-in: pick an account on this device and enter its PIN, or open a new account.
 * With [lockFor] it only unlocks that one account (auto-lock after the app was in the background).
 */
@Composable
fun AuthScreen(lockFor: String? = null, onUnlocked: (String) -> Unit) {
    val state by Bank.state.collectAsState()
    val start: AuthStep = when {
        lockFor != null -> AuthStep.Pin(lockFor)
        state.accounts.isEmpty() -> AuthStep.Create
        state.accounts.size == 1 -> AuthStep.Pin(state.accounts.first().id)
        state.settings.lastAccount != null && state.account(state.settings.lastAccount) != null -> AuthStep.Pin(state.settings.lastAccount!!)
        else -> AuthStep.Pick
    }
    var step by remember { mutableStateOf(start) }
    if (state.accounts.isEmpty() && step !is AuthStep.Create) step = AuthStep.Create

    Box(
        Modifier
            .fillMaxSize()
            .background(O.c.bg)
            .clickable(enabled = false) {},
    ) {
        Backdrop()
        AnimatedContent(step, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "auth") { s ->
            when (s) {
                AuthStep.Pick -> PickAccount(
                    accounts = state.accounts,
                    onPick = { step = AuthStep.Pin(it) },
                    onCreate = { step = AuthStep.Create },
                )
                is AuthStep.Pin -> {
                    val acc = state.account(s.id)
                    if (acc == null) {
                        LaunchedEffect(Unit) { step = AuthStep.Pick }
                    } else {
                        PinLogin(
                            acc = acc,
                            canSwitch = lockFor == null,
                            onSwitch = { step = AuthStep.Pick },
                            onOk = { onUnlocked(acc.id) },
                        )
                    }
                }
                AuthStep.Create -> CreateAccount(
                    canCancel = state.accounts.isNotEmpty(),
                    onCancel = { step = AuthStep.Pick },
                    onCreated = { onUnlocked(it) },
                )
            }
        }
    }
}

@Composable
private fun Backdrop() {
    val accent = O.c.accent
    val t = rememberInfiniteTransition(label = "bg")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "shift")
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            Brush.radialGradient(listOf(accent.a.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * (0.1f + 0.3f * shift), size.height * 0.12f), radius = size.width * 0.8f),
            radius = size.width * 0.8f, center = Offset(size.width * (0.1f + 0.3f * shift), size.height * 0.12f),
        )
        drawCircle(
            Brush.radialGradient(listOf(accent.b.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * (0.9f - 0.3f * shift), size.height * 0.9f), radius = size.width * 0.9f),
            radius = size.width * 0.9f, center = Offset(size.width * (0.9f - 0.3f * shift), size.height * 0.9f),
        )
    }
}

@Composable
fun Logo(size: Int = 72) {
    val accent = O.c.accent
    val t = rememberInfiniteTransition(label = "logo")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000)), label = "rot")
    Canvas(Modifier.size(size.dp)) {
        val r = this.size.minDimension / 2 * 0.72f
        drawCircle(Brush.linearGradient(listOf(accent.a, accent.b)), radius = r, style = Stroke(this.size.minDimension * 0.11f))
        val a = Math.toRadians(rot.toDouble())
        val c = Offset(center.x + (r * kotlin.math.cos(a)).toFloat(), center.y + (r * kotlin.math.sin(a)).toFloat())
        drawCircle(accent.b, radius = this.size.minDimension * 0.09f, center = c)
    }
}

@Composable
private fun PickAccount(accounts: List<Account>, onPick: (String) -> Unit, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Gap(30.dp)
        Logo()
        Gap(16.dp)
        Text("Orbis", style = MaterialTheme.typography.headlineLarge, color = O.c.text)
        Text("Wähle dein Konto", color = O.c.muted)
        Gap(28.dp)
        accounts.forEach { acc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(O.c.surface)
                    .border(1.dp, O.c.border, RoundedCornerShape(22.dp))
                    .clickable { onPick(acc.id) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(acc.name, acc.colorIndex, 48.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(acc.name, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(acc.id, color = O.c.muted, fontSize = 13.sp)
                }
            }
        }
        Gap(12.dp)
        SoftButton("Neues Konto eröffnen", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, onClick = onCreate)
    }
}

@Composable
private fun PinLogin(acc: Account, canSwitch: Boolean, onSwitch: () -> Unit, onOk: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var blockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val bioOk = acc.biometric && Biometric.available(context)

    LaunchedEffect(acc.id) {
        if (bioOk) Biometric.prompt(context, "Orbis entsperren") { onOk() }
    }
    LaunchedEffect(blockedUntil) {
        while (System.currentTimeMillis() < blockedUntil) {
            now = System.currentTimeMillis(); delay(250)
        }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            if (Security.check(pin, acc)) {
                onOk()
            } else {
                error = true
                attempts++
                if (attempts >= 5) {
                    blockedUntil = System.currentTimeMillis() + 30_000
                    attempts = 0
                }
                delay(600)
                pin = ""
                error = false
            }
        }
    }
    val blocked = now < blockedUntil

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Gap(24.dp)
        Avatar(acc.name, acc.colorIndex, 76.dp)
        Gap(14.dp)
        Text("Hallo, ${acc.name.substringBefore(' ')}", style = MaterialTheme.typography.headlineSmall, color = O.c.text)
        Text(acc.id, color = O.c.muted, fontSize = 14.sp)
        Gap(30.dp)
        Text(
            if (blocked) "Zu viele Versuche – warte ${((blockedUntil - now) / 1000) + 1}s" else "PIN eingeben",
            color = if (blocked) O.c.negative else O.c.muted,
        )
        Gap(18.dp)
        PinDots(4, pin.length, error)
        Spacer(Modifier.weight(1f))
        PinPad(
            onDigit = { if (!blocked && pin.length < 4) pin += it },
            onDelete = { pin = pin.dropLast(1) },
            onBiometric = if (bioOk) ({ Biometric.prompt(context, "Orbis entsperren") { onOk() } }) else null,
        )
        Gap(10.dp)
        if (canSwitch) {
            Text(
                "Konto wechseln",
                color = O.c.accent.a,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onSwitch).padding(12.dp),
            )
        }
    }
}

@Composable
private fun CreateAccount(canCancel: Boolean, onCancel: () -> Unit, onCreated: (String) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(0) }
    var start by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            Toast.show(if (text != null && Bank.importBackup(text)) "Backup wiederhergestellt" else "Backup konnte nicht gelesen werden")
        }
    }

    BackHandler(enabled = page > 0 || canCancel) {
        when {
            page > 0 -> { page--; pin = ""; pin2 = "" }
            canCancel -> onCancel()
        }
    }

    LaunchedEffect(pin2) {
        if (pin2.length == 4) {
            if (pin2 == pin) {
                val acc = Bank.createAccount(name, pin, Money.fromInput(start), color)
                Toast.show("Willkommen bei Orbis!")
                onCreated(acc.id)
            } else {
                mismatch = true
                delay(700)
                mismatch = false
                pin2 = ""
            }
        }
    }

    when (page) {
        0 -> Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Gap(30.dp)
            Logo(84)
            Gap(18.dp)
            Text("Willkommen bei Orbis", style = MaterialTheme.typography.headlineLarge, color = O.c.text, textAlign = TextAlign.Center)
            Gap(6.dp)
            Text(
                "Dein Wallet für Überweisungen, Anfragen, Sparziele und mehr – alles sicher auf deinem Gerät.",
                color = O.c.muted, textAlign = TextAlign.Center,
            )
            Gap(30.dp)
            Avatar(name.ifBlank { "?" }, color, 72.dp)
            Gap(16.dp)
            Field(name, { name = it.take(24) }, "Dein Name")
            Gap(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatches.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(3.dp, if (i == color) O.c.text else Color.Transparent, CircleShape)
                            .clickable { color = i },
                    )
                }
            }
            Gap(14.dp)
            Field(start, { v -> start = v.filter { it.isDigit() || it == ',' }.take(10) }, "Startguthaben (optional)", keyboard = KeyboardType.Decimal)
            Gap(24.dp)
            GradientButton("Weiter", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { page = 1 }
            Gap(12.dp)
            if (canCancel) {
                SoftButton("Abbrechen", Modifier.fillMaxWidth(), onClick = onCancel)
            } else {
                SoftButton("Backup wiederherstellen", Modifier.fillMaxWidth(), icon = Icons.Rounded.Restore) {
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
        }
        else -> Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Gap(40.dp)
            Box(Modifier.size(64.dp).clip(CircleShape).background(swatch(color).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text("🔐", fontSize = 30.sp)
            }
            Gap(16.dp)
            val confirming = pin.length == 4
            Text(if (confirming) "PIN bestätigen" else "PIN festlegen", style = MaterialTheme.typography.headlineSmall, color = O.c.text)
            Text(
                if (mismatch) "Die PINs stimmen nicht überein" else "4 Ziffern, mit denen du Orbis entsperrst",
                color = if (mismatch) O.c.negative else O.c.muted,
            )
            Gap(26.dp)
            PinDots(4, if (confirming) pin2.length else pin.length, mismatch)
            Spacer(Modifier.weight(1f))
            PinPad(
                onDigit = { d ->
                    if (pin.length < 4) pin += d else if (pin2.length < 4) pin2 += d
                },
                onDelete = { if (pin.length == 4 && pin2.isNotEmpty()) pin2 = pin2.dropLast(1) else if (pin2.isEmpty()) pin = pin.dropLast(1) },
                onBiometric = null,
            )
            Gap(16.dp)
        }
    }
}
