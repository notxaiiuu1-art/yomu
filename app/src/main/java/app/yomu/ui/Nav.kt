package app.yomu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * A screen owns its state and a coroutine scope that lives exactly as long as the screen is on
 * the back stack, so going back to a list doesn't reload it.
 */
abstract class Screen {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Reader screens hide the system bars and get volume keys. */
    open val immersive: Boolean = false

    @Composable
    abstract fun Content()

    open fun onDispose() {
        scope.cancel()
    }
}

class Navigator(root: Screen) {
    val stack = mutableStateListOf(root)

    val current: Screen get() = stack[stack.lastIndex]

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex).onDispose()
        return true
    }

    fun replace(screen: Screen) {
        val old = stack.removeAt(stack.lastIndex)
        stack.add(screen)
        old.onDispose()
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
