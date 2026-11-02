package app.orbis.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.Bank
import app.orbis.ui.LocalNav
import app.orbis.ui.O
import app.orbis.ui.swatch

@Composable
fun haptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    return {
        if (Bank.state.value.settings.haptics) h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/** Screen with a back button and a title; content scrolls on its own. */
@Composable
fun SubScreen(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().background(O.c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.pop() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = O.c.text)
            }
            Text(
                title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = O.c.text,
                modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(O.c.surface)
            .border(1.dp, O.c.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = O.c.text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, color = O.c.accent.a, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
fun Avatar(name: String, colorIndex: Int, size: Dp = 44.dp, emoji: String? = null) {
    val initials = name.split(" ", "-").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    val c = swatch(colorIndex)
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji ?: initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

/** Stable color for any account ID, so the same person always gets the same avatar color. */
fun colorFor(id: String): Int = id.fold(7) { acc, ch -> acc * 31 + ch.code }.let { if (it < 0) -it else it }

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val tap = haptic()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(if (enabled) O.c.gradient else Brush.linearGradient(listOf(O.c.surface2, O.c.surface2)))
            .clickable(enabled = enabled) { tap(); onClick() }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color.White else O.c.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) Color.White else O.c.muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SoftButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = O.c.text,
    onClick: () -> Unit,
) {
    val tap = haptic()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(O.c.surface2)
            .clickable { tap(); onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun IconBubble(icon: ImageVector, tint: Color, size: Dp = 44.dp, background: Color = tint.copy(alpha = 0.15f)) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.36f)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun EmojiBubble(emoji: String, color: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.36f)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.45f).sp)
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = O.c.accent.a,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, tint, 40.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = O.c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = O.c.muted, fontSize = 13.sp)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun OrbisSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = O.c.accent.a,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = O.c.surface2,
            uncheckedBorderColor = O.c.border,
            uncheckedThumbColor = O.c.muted,
        ),
    )
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = O.c.accent.a,
            unfocusedBorderColor = O.c.border,
            focusedLabelColor = O.c.accent.a,
            unfocusedLabelColor = O.c.muted,
            cursorColor = O.c.accent.a,
            focusedTextColor = O.c.text,
            unfocusedTextColor = O.c.text,
        ),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, leading: String? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) O.c.accent.a.copy(alpha = 0.18f) else O.c.surface2)
            .border(1.dp, if (selected) O.c.accent.a else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(leading, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (selected) O.c.accent.a else O.c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (selected && leading == null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.Check, null, tint = O.c.accent.a, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "progress")
    LinearProgressIndicator(
        progress = { p },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)),
        color = color,
        trackColor = O.c.surface2,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun Empty(emoji: String, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = O.c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, color = O.c.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Outline(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.border(BorderStroke(1.dp, O.c.border), RoundedCornerShape(18.dp))) { content() }
}
