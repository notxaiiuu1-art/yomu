package app.yomu.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.yomu.Yomu
import app.yomu.ui.screens.HomeScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class MainActivity : ComponentActivity() {

    private val navigator = Navigator(HomeScreen())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyHighRefreshRate()

        setContent {
            YomuTheme {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    val current = navigator.current
                    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }

                    LaunchedEffect(current) { setImmersive(current.immersive) }

                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        val depth = remember { IntArray(1) }
                        AnimatedContent(
                            targetState = current,
                            transitionSpec = {
                                val forward = navigator.stack.size >= depth[0]
                                depth[0] = navigator.stack.size
                                if (forward) {
                                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                                } else {
                                    fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                                }
                            },
                            label = "nav",
                        ) { screen ->
                            screen.Content()
                        }
                    }
                }
            }
        }
    }

    /** The Pixel 8a panel goes up to 120 Hz; ask for it so scrolling stays buttery. */
    private fun applyHighRefreshRate() {
        if (!Yomu.prefs.force120Hz.get()) return
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }

    private fun setImmersive(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            if (Yomu.prefs.keepScreenOn.get()) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (navigator.current.immersive && Yomu.prefs.volumeKeys.get()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    volumeEvents.tryEmit(1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    volumeEvents.tryEmit(-1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private val volumeEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)

        /** +1 = next page, -1 = previous page. */
        val volumeKeys: SharedFlow<Int> = volumeEvents
    }
}
