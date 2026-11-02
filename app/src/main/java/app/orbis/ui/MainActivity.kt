package app.orbis.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.orbis.data.Bank
import app.orbis.data.Money
import app.orbis.ui.screens.AuthScreen
import app.orbis.ui.screens.BudgetsScreen
import app.orbis.ui.screens.ContactsScreen
import app.orbis.ui.screens.LiveScreen
import app.orbis.ui.screens.MainShell
import app.orbis.ui.screens.MyQrScreen
import app.orbis.ui.screens.RecurringScreen
import app.orbis.ui.screens.RequestScreen
import app.orbis.ui.screens.RequestsScreen
import app.orbis.ui.screens.SecurityScreen
import app.orbis.ui.screens.SendScreen
import app.orbis.ui.screens.SettingsScreen
import app.orbis.ui.screens.SplitScreen
import app.orbis.ui.screens.TopUpScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val navigator = Navigator()
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L

    /** A payment link that arrived while nobody was signed in. */
    private var pendingLink: Route? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleLink(intent)

        setContent {
            val state by Bank.state.collectAsState()
            val session by Bank.session.collectAsState()
            val settings = state.settings

            LaunchedEffect(settings.blockScreenshots) {
                if (settings.blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            OrbisTheme(settings.theme, settings.accent) {
                val c = O.c
                LaunchedEffect(c.dark) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !c.dark
                        isAppearanceLightNavigationBars = !c.dark
                    }
                }
                CompositionLocalProvider(LocalNav provides navigator) {
                    Box(Modifier.fillMaxSize().background(c.bg)) {
                        val me = state.account(session)
                        if (me == null) {
                            AuthScreen(onUnlocked = { id ->
                                Bank.login(id)
                                navigator.reset()
                                pendingLink?.let { navigator.push(it) }
                                pendingLink = null
                            })
                        } else {
                            // While locked, the app itself is not composed at all, so nothing behind
                            // the PIN screen can be seen or tapped. The navigation stack survives.
                            if (locked) {
                                AuthScreen(lockFor = me.id, onUnlocked = { locked = false })
                            } else {
                                AppContent()
                            }
                        }
                        ToastHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val limit = Bank.state.value.settings.autoLockSeconds
        if (stoppedAt > 0 && Bank.session.value != null && limit >= 0 &&
            SystemClock.elapsedRealtime() - stoppedAt > limit * 1000L
        ) {
            locked = true
        }
        if (Bank.session.value != null) {
            val n = Bank.processRecurring()
            if (n > 0) Toast.show("$n Dauerauftrag/-aufträge ausgeführt")
        }
    }

    private fun handleLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme != "orbis") return
        val to = uri.getQueryParameter("to") ?: return
        val amount = uri.getQueryParameter("amount")?.let { Money.parse(it) }
        val route = Route.Send(to = to, amount = amount, note = uri.getQueryParameter("note"))
        if (Bank.session.value != null) navigator.push(route) else pendingLink = route
    }
}

@Composable
private fun AppContent() {
    val nav = LocalNav.current
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
    BackHandler(enabled = nav.stack.size == 1 && nav.tab.value != Tab.HOME) { nav.tab.value = Tab.HOME }

    val depth = remember { intArrayOf(1) }
    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            val forward = nav.stack.size >= depth[0]
            depth[0] = nav.stack.size
            if (forward) {
                (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220))) togetherWith fadeOut(tween(160))
            } else {
                fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(200)))
            }
        },
        label = "nav",
    ) { route ->
        when (route) {
            Route.Main -> MainShell()
            is Route.Send -> SendScreen(route)
            is Route.Request -> RequestScreen(route)
            Route.Split -> SplitScreen()
            Route.TopUp -> TopUpScreen()
            Route.Live -> LiveScreen()
            Route.Contacts -> ContactsScreen()
            Route.Recurring -> RecurringScreen()
            Route.Budgets -> BudgetsScreen()
            Route.Settings -> SettingsScreen()
            Route.Security -> SecurityScreen()
            Route.MyQr -> MyQrScreen()
            Route.Requests -> RequestsScreen()
        }
    }
}

@Composable
private fun ToastHost() {
    val msg by Toast.message.collectAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(msg) {
        if (msg != null) {
            visible = true
            delay(2400)
            visible = false
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Text(
                msg?.first.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xEE1E1E2E))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}
