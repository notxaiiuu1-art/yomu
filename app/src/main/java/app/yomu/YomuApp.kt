package app.yomu

import android.app.Application
import android.content.Context
import app.yomu.core.Library
import app.yomu.core.Prefs
import app.yomu.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType

/** App-wide singletons. Small enough that a hand-written locator beats a DI framework. */
object Yomu {
    lateinit var app: Application
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var network: NetworkHelper
        private set
    lateinit var extensions: ExtensionManager
        private set
    lateinit var library: Library
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun init(application: Application) {
        app = application
        prefs = Prefs(application)
        network = NetworkHelper(application)
        library = Library(application)
        extensions = ExtensionManager(application, network, prefs)
    }
}

class YomuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Yomu.init(this)

        // Extensions look these up through Injekt, exactly as in other Tachiyomi-family apps.
        Injekt.addSingleton(fullType<Application>(), this)
        Injekt.addSingleton(fullType<Context>(), this)
        Injekt.addSingletonFactory(fullType<NetworkHelper>()) { Yomu.network }
        Injekt.addSingletonFactory(fullType<Json>()) { Yomu.json }
        Injekt.addSingletonFactory(fullType<ProtoBuf>()) { ProtoBuf }
        Injekt.addSingletonFactory(fullType<JavaScriptEngine>()) { JavaScriptEngine(this) }

        Yomu.scope.launch {
            Yomu.extensions.loadInstalled()
            Yomu.extensions.refreshCatalog()
        }
    }
}
