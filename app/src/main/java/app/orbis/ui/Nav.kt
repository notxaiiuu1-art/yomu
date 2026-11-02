package app.orbis.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

enum class Tab(val label: String) { HOME("Start"), HISTORY("Verlauf"), STATS("Statistik"), GOALS("Ziele"), PROFILE("Profil") }

sealed interface Route {
    data object Main : Route
    data class Send(val to: String? = null, val amount: Long? = null, val note: String? = null) : Route
    data class Request(val from: String? = null) : Route
    data object Split : Route
    data object TopUp : Route
    data object Live : Route
    data object Contacts : Route
    data object Recurring : Route
    data object Budgets : Route
    data object Settings : Route
    data object Security : Route
    data object MyQr : Route
    data object Requests : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Main)
    val tab = mutableStateOf(Tab.HOME)

    val current: Route get() = stack.last()

    fun push(r: Route) {
        stack.add(r)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun reset(tab: Tab = Tab.HOME) {
        stack.clear()
        stack.add(Route.Main)
        this.tab.value = tab
    }
}

val LocalNav = staticCompositionLocalOf { Navigator() }

/** Short status messages shown as a floating pill. */
object Toast {
    val message = MutableStateFlow<Pair<String, Long>?>(null)
    fun show(text: String) {
        message.value = text to System.nanoTime()
    }
}
