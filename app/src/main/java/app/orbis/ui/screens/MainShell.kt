package app.orbis.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.ui.LocalNav
import app.orbis.ui.O
import app.orbis.ui.Tab
import app.orbis.ui.components.haptic

/** Space the floating bottom bar takes, so scrolling lists can leave room for it. */
val BottomBarSpace = 110.dp

@Composable
fun MainShell() {
    val nav = LocalNav.current
    val tab = nav.tab.value
    Box(Modifier.fillMaxSize().background(O.c.bg)) {
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "tab") { t ->
            when (t) {
                Tab.HOME -> HomeTab()
                Tab.HISTORY -> HistoryTab()
                Tab.STATS -> StatsTab()
                Tab.GOALS -> GoalsTab()
                Tab.PROFILE -> ProfileTab()
            }
        }
        BottomBar(tab) { nav.tab.value = it }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val tap = haptic()
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(O.c.surface.copy(alpha = 0.97f))
                .border(1.dp, O.c.border, shape)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val icon: ImageVector = when (t) {
                    Tab.HOME -> Icons.Rounded.Home
                    Tab.HISTORY -> Icons.AutoMirrored.Rounded.ReceiptLong
                    Tab.STATS -> Icons.Rounded.BarChart
                    Tab.GOALS -> Icons.Rounded.Savings
                    Tab.PROFILE -> Icons.Rounded.Person
                }
                val selected = t == current
                val bg by animateColorAsState(if (selected) O.c.accent.a.copy(alpha = 0.16f) else Color.Transparent, label = "bg")
                val fg by animateColorAsState(if (selected) O.c.accent.a else O.c.muted, label = "fg")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tap(); onSelect(t) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Icon(icon, t.label, tint = fg, modifier = Modifier.size(24.dp))
                    }
                    Text(t.label, color = fg, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}
