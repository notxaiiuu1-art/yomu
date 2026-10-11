package app.yomu.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import app.yomu.Yomu

/**
 * In-app browser sharing cookies and User-Agent with the extension HTTP client. Solving a
 * Cloudflare check or logging in here makes the source work in the app afterwards.
 */
class WebViewActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()

        setContent {
            YomuTheme {
                var title by remember { mutableStateOf(url) }
                var webView by remember { mutableStateOf<WebView?>(null) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) { Icon(Icons.Filled.Close, "Schließen") }
                            },
                            actions = {
                                IconButton(onClick = { webView?.reload() }) { Icon(Icons.Filled.Refresh, "Neu laden") }
                            },
                        )
                    },
                ) { padding ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = Yomu.network.defaultUserAgentProvider()
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        title = view.title?.takeIf { it.isNotBlank() } ?: url.orEmpty()
                                        CookieManager.getInstance().flush()
                                    }
                                }
                                loadUrl(url)
                                webView = this
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"

        fun intent(context: Context, url: String) =
            Intent(context, WebViewActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
