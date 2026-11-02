package app.orbis

import android.app.Application
import app.orbis.data.Bank

class OrbisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Bank.init(this)
    }
}
