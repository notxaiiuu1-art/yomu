package app.yomu.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.yomu.core.Prefs
import dalvik.system.DelegateLastClassLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class InstalledExtension(
    val pkg: String,
    val name: String,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val nsfw: Boolean,
    val sources: List<CatalogueSource>,
    val error: String? = null,
)

/**
 * Installs extension APKs privately (no system installer prompt), verifies they are signed
 * with the key the repository publishes, and loads their sources with a class loader.
 */
class ExtensionManager(
    private val context: Context,
    private val network: NetworkHelper,
    private val prefs: Prefs,
) {
    private val dir = File(context.filesDir, "exts").apply { mkdirs() }
    private val catalogCache = File(context.filesDir, "catalog.json")
    private val loadLock = Mutex()

    private val _catalog = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val catalog: StateFlow<List<CatalogEntry>> = _catalog.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installed: StateFlow<List<InstalledExtension>> = _installed.asStateFlow()

    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError: StateFlow<String?> = _catalogError.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    init {
        runCatching {
            if (catalogCache.exists()) {
                _catalog.value = RepoParser.json.decodeFromString(catalogCache.readText())
            }
        }
    }

    fun allSources(): List<CatalogueSource> = _installed.value.flatMap { it.sources }

    fun source(id: Long): CatalogueSource? = allSources().firstOrNull { it.id == id }

    // ---------------------------------------------------------------- catalogue

    suspend fun refreshCatalog() = withContext(Dispatchers.IO) {
        val result = mutableListOf<CatalogEntry>()
        var error: String? = null
        for (repo in prefs.repos.get()) {
            try {
                result += fetchRepo(repo, repo, depth = 0)
            } catch (e: Exception) {
                error = "Repo konnte nicht geladen werden: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        if (result.isNotEmpty()) {
            val unique = result.distinctBy { it.pkg }.sortedBy { it.name.lowercase() }
            _catalog.value = unique
            runCatching { catalogCache.writeText(RepoParser.json.encodeToString(unique)) }
        }
        _catalogError.value = error
    }

    private suspend fun download(url: String): ByteArray =
        network.client.newCall(GET(url)).awaitSuccess().use { it.body.bytes() }

    private suspend fun fetchRepo(url: String, repoKey: String, depth: Int): List<CatalogEntry> {
        check(depth < 3) { "Zu viele Weiterleitungen" }
        return when (val parsed = RepoParser.parse(download(url))) {
            is RepoParser.Parsed.Redirect -> {
                parsed.meta?.signingKeyFingerprint?.takeIf { it.isNotBlank() }?.let(::trustKey)
                fetchRepo(parsed.url, repoKey, depth + 1)
            }
            is RepoParser.Parsed.Store -> {
                parsed.store.signingKey.takeIf { it.isNotBlank() }?.let(::trustKey)
                val list = parsed.store.extensionList
                    ?: parsed.store.extensionListUrl?.let { RepoParser.parseList(download(it)) }
                    ?: ProtoExtensionList()
                RepoParser.toCatalog(list, repoKey)
            }
            is RepoParser.Parsed.Legacy -> {
                val base = url.substringBeforeLast('/')
                runCatching {
                    val repoJson = RepoParser.json.decodeFromString<LegacyRepoJson>(download("$base/repo.json").decodeToString())
                    repoJson.meta?.signingKeyFingerprint?.takeIf { it.isNotBlank() }?.let(::trustKey)
                }
                RepoParser.legacyToCatalog(parsed.entries, base, repoKey)
            }
        }
    }

    private fun trustKey(key: String) {
        val current = prefs.trustedSignatures.get()
        if (key !in current) prefs.trustedSignatures.set(current + key)
    }

    // ---------------------------------------------------------------- install / remove

    suspend fun install(entry: CatalogEntry): Result<Unit> = withContext(Dispatchers.IO) {
        _busy.update { it + entry.pkg }
        try {
            val tmp = File(context.cacheDir, "${entry.pkg}.apk")
            tmp.delete()
            tmp.writeBytes(download(entry.apkUrl))

            val info = archiveInfo(tmp) ?: error("APK ist ungültig")
            check(info.packageName == entry.pkg) { "Falsches Paket: ${info.packageName}" }
            check(isExtension(info)) { "Keine Erweiterung" }
            val signatures = signatures(info)
            check(signatures.any { it in prefs.trustedSignatures.get() }) { "Signatur passt nicht zum Repo" }

            val target = File(dir, "${entry.pkg}.ext")
            if (target.exists()) {
                target.setWritable(true)
                target.delete()
            }
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            // Android 14+ refuses to load writable dex files.
            target.setReadOnly()

            val loaded = load(target)
            _installed.update { list -> (list.filterNot { it.pkg == entry.pkg } + loaded).sortedBy { it.name.lowercase() } }
            loaded.error?.let { error(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _busy.update { it - entry.pkg }
        }
    }

    fun uninstall(pkg: String) {
        File(dir, "$pkg.ext").apply { setWritable(true); delete() }
        _installed.update { list -> list.filterNot { it.pkg == pkg } }
    }

    // ---------------------------------------------------------------- loading

    suspend fun loadInstalled() = loadLock.withLock {
        withContext(Dispatchers.IO) {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".ext") }.orEmpty()
            val loaded = coroutineScope {
                files.map { file -> async { runCatching { load(file) }.getOrNull() } }.awaitAll()
            }
            _installed.value = loaded.filterNotNull().sortedBy { it.name.lowercase() }
        }
    }

    private fun archiveInfo(file: File): PackageInfo? {
        val flags = PackageManager.GET_META_DATA or
            PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_SIGNING_CERTIFICATES
        val info = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.PackageInfoFlags.of(flags.toLong()),
        ) ?: return null
        // getPackageArchiveInfo leaves these empty on Android 13+.
        info.applicationInfo?.let {
            if (it.sourceDir == null) it.sourceDir = file.absolutePath
            if (it.publicSourceDir == null) it.publicSourceDir = file.absolutePath
        }
        return info
    }

    private fun isExtension(info: PackageInfo) =
        info.reqFeatures.orEmpty().any { it.name == "tachiyomi.extension" }

    private fun signatures(info: PackageInfo): List<String> {
        val signing = info.signingInfo ?: return emptyList()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { sha256(it.toByteArray()) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun load(file: File): InstalledExtension {
        if (file.canWrite()) file.setReadOnly()
        val info = archiveInfo(file) ?: error("APK ist ungültig")
        val app = info.applicationInfo ?: error("APK ist ungültig")
        val meta = app.metaData
        val pkg = info.packageName
        val name = meta?.getString("tachiyomix.name")
            ?: context.packageManager.getApplicationLabel(app).toString().removePrefix("Tachiyomi: ")
        val versionName = info.versionName.orEmpty()
        val nsfw = when {
            meta == null -> false
            meta.containsKey("tachiyomix.contentWarning") -> meta.getInt("tachiyomix.contentWarning") == 2
            else -> meta.getInt("tachiyomi.extension.nsfw") == 1
        }

        fun failed(reason: String) = InstalledExtension(pkg, name, info.longVersionCode, versionName, "", nsfw, emptyList(), reason)

        if (meta == null || !isExtension(info)) return failed("Keine Erweiterung")
        if (signatures(info).none { it in prefs.trustedSignatures.get() }) return failed("Nicht vertrauenswürdig")

        val lib = meta.getFloat("tachiyomix.extensionLib").takeIf { it != 0f }?.toString()?.toDoubleOrNull()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (lib == null || lib < 1.4 || lib > 1.6) return failed("Erweiterungs-Version $lib wird nicht unterstützt")

        val classes = meta.getString("tachiyomi.extension.class") ?: return failed("Beschädigt")
        val loader = DelegateLastClassLoader(app.sourceDir, null, context.classLoader)

        val sources = try {
            classes.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                .map { if (it.startsWith(".")) pkg + it else it }
                .flatMap { className ->
                    when (val obj = Class.forName(className, false, loader).getDeclaredConstructor().newInstance()) {
                        is Source -> listOf(obj)
                        is SourceFactory -> obj.createSources()
                        else -> emptyList()
                    }
                }
                .filterIsInstance<CatalogueSource>()
        } catch (e: Throwable) {
            return failed("Fehler beim Laden: ${e.message ?: e.javaClass.simpleName}")
        }

        val langs = sources.map { it.lang }.toSet()
        return InstalledExtension(
            pkg = pkg,
            name = name,
            versionCode = info.longVersionCode,
            versionName = versionName,
            lang = langs.singleOrNull() ?: "all",
            nsfw = nsfw,
            sources = sources,
        )
    }
}
