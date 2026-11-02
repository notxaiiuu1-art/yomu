package app.orbis.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Category
import app.orbis.data.Dates
import app.orbis.data.Money
import app.orbis.data.Settings
import app.orbis.data.Txn
import app.orbis.data.TxnKind
import app.orbis.ui.O
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

// ---------------------------------------------------------------- Keypads

/** Amount keypad with a decimal comma; max two decimals and 9 digits before the comma. */
fun applyKey(current: String, key: String): String = when (key) {
    "⌫" -> current.dropLast(1)
    "," -> if (current.contains(',')) current else (current.ifEmpty { "0" } + ",")
    else -> {
        val parts = current.split(',')
        when {
            parts.size == 2 && parts[1].length >= 2 -> current
            parts.size == 1 && current.length >= 9 -> current
            current == "0" -> key
            else -> current + key
        }
    }
}

@Composable
fun NumPad(onKey: (String) -> Unit, modifier: Modifier = Modifier, leftKey: String = ",", onLeftIcon: (@Composable () -> Unit)? = null) {
    val tap = haptic()
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(leftKey, "0", "⌫"))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = key.isNotEmpty()) { tap(); onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            key == "⌫" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Löschen", tint = O.c.text)
                            key == "bio" && onLeftIcon != null -> onLeftIcon()
                            else -> Text(key, color = O.c.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinDots(length: Int, filled: Int, error: Boolean) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error) {
            repeat(3) {
                shake.animateTo(14f, tween(45)); shake.animateTo(-14f, tween(45))
            }
            shake.animateTo(0f, spring())
        }
    }
    Row(
        Modifier.graphicsLayer { translationX = shake.value },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(length) { i ->
            val on = i < filled
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            error -> O.c.negative
                            on -> O.c.accent.a
                            else -> O.c.surface2
                        },
                    ),
            )
        }
    }
}

@Composable
fun PinPad(onDigit: (String) -> Unit, onDelete: () -> Unit, onBiometric: (() -> Unit)?) {
    NumPad(
        onKey = { k ->
            when (k) {
                "⌫" -> onDelete()
                "bio" -> onBiometric?.invoke()
                else -> onDigit(k)
            }
        },
        leftKey = if (onBiometric != null) "bio" else "",
        onLeftIcon = { Icon(Icons.Rounded.Fingerprint, "Biometrie", tint = O.c.accent.a, modifier = Modifier.size(30.dp)) },
    )
}

// ---------------------------------------------------------------- Transactions

data class TxnView(val title: String, val subtitle: String, val signed: Long, val emoji: String, val color: Color)

@Composable
fun txnView(t: Txn, me: String): TxnView {
    val incoming = t.to == me
    val cat = Category.of(t.category)
    val title = when (t.kind) {
        TxnKind.TOPUP -> "Einzahlung"
        TxnKind.GOAL_IN -> t.toName
        TxnKind.GOAL_OUT -> t.fromName
        else -> if (incoming) t.fromName else t.toName
    }
    val kindLabel = when (t.kind) {
        TxnKind.TRANSFER -> if (incoming) "Erhalten" else "Gesendet"
        TxnKind.TOPUP -> "Aufgeladen"
        TxnKind.GOAL_IN -> "Gespart"
        TxnKind.GOAL_OUT -> "Entnommen"
        TxnKind.REQUEST -> if (incoming) "Anfrage bezahlt" else "Anfrage beglichen"
        TxnKind.RECURRING -> "Dauerauftrag"
    }
    val emoji = when (t.kind) {
        TxnKind.TOPUP -> "💰"
        TxnKind.GOAL_IN, TxnKind.GOAL_OUT -> "🐷"
        TxnKind.RECURRING -> "🔁"
        else -> cat.emoji
    }
    val sub = buildString {
        append(kindLabel)
        if (t.note.isNotBlank() && t.kind != TxnKind.GOAL_IN && t.kind != TxnKind.GOAL_OUT) append(" · ").append(t.note)
    }
    return TxnView(title, sub, if (incoming) t.amount else -t.amount, emoji, Color(cat.color))
}

@Composable
fun TxnRow(t: Txn, me: String, settings: Settings, hidden: Boolean = false, onClick: () -> Unit) {
    val v = txnView(t, me)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBubble(v.emoji, v.color, 46.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(v.title, color = O.c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(v.subtitle, color = O.c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hidden) "••••" else Money.format(v.signed, settings, signed = true),
                color = if (v.signed > 0) O.c.positive else O.c.text,
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
            Text(Dates.time(t.time), color = O.c.muted, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- Charts

data class Slice(val value: Float, val color: Color)

@Composable
fun Donut(slices: List<Slice>, modifier: Modifier = Modifier, thickness: Dp = 22.dp, center: @Composable () -> Unit = {}) {
    val anim = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { anim.animateTo(1f, tween(900)) }
    val track = O.c.surface2
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            var start = -90f
            slices.forEach { s ->
                val sweep = 360f * (s.value / total) * anim.value
                val gap = if (slices.size > 1) 2f else 0f
                drawArc(s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun Bars(values: List<Pair<String, Long>>, color: Color, modifier: Modifier = Modifier, highlightLast: Boolean = true) {
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val anim = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { anim.animateTo(1f, tween(800)) }
    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, (label, v) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val f = (v.toFloat() / max) * anim.value
                val c = if (!highlightLast || i == values.lastIndex) color else color.copy(alpha = 0.45f)
                val track = O.c.surface2
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val r = CornerRadius(size.width / 3, size.width / 3)
                    drawRoundRect(track, cornerRadius = r)
                    val h = size.height * f
                    if (h > 0f) drawRoundRect(c, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = r)
                }
                Spacer(Modifier.height(6.dp))
                Text(label, color = O.c.muted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun Ring(progress: Float, color: Color, size: Dp, thickness: Dp = 6.dp, content: @Composable () -> Unit = {}) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceIn(0f, 1f), tween(900)) }
    val track = O.c.surface2
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = thickness.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(s))
            drawArc(color, -90f, 360f * anim.value, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun LiveDot() {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(9.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Color(0xFFFF4D4D)))
}

// ---------------------------------------------------------------- QR

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, fg: Color = Color.Black) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull()
    }
    Canvas(modifier.aspectRatio(1f)) {
        val m = matrix ?: return@Canvas
        val cell = size.width / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.get(x, y)) {
                drawRoundRect(
                    fg,
                    topLeft = Offset(x * cell, y * cell),
                    size = Size(cell * 1.02f, cell * 1.02f),
                    cornerRadius = CornerRadius(cell * 0.25f),
                )
            }
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(O.c.border))
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))
