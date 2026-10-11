package eu.kanade.tachiyomi

import app.yomu.BuildConfig

/** Some extensions put the host version into their User-Agent or feature checks. */
@Suppress("unused")
object AppInfo {
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE
    fun getVersionName(): String = BuildConfig.VERSION_NAME
}
