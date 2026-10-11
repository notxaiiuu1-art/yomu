package app.yomu.extension

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Extension repository index ("index v2"), as served at e.g.
 * https://github.com/keiyoushi/extensions/raw/repo/index.pb — a gzip-compressed protobuf.
 * Every field has a default so unknown or missing fields never break parsing.
 */
@Serializable
class ProtoStore(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: ProtoContact? = null,
    @ProtoNumber(101) val extensionList: ProtoExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
)

@Serializable
class ProtoContact(
    @ProtoNumber(1) val website: String = "",
    @ProtoNumber(2) val discord: String? = null,
)

@Serializable
class ProtoExtensionList(@ProtoNumber(1) val extensions: List<ProtoExtension> = emptyList())

@Serializable
class ProtoExtension(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val packageName: String = "",
    @ProtoNumber(3) val resources: ProtoResources? = null,
    @ProtoNumber(4) val extensionLib: String = "",
    @ProtoNumber(5) val versionCode: Long = 0,
    @ProtoNumber(6) val versionName: String = "",
    /** 1 = safe, 2 = mixed, 3 = NSFW. */
    @ProtoNumber(7) val contentWarning: Int = 0,
    @ProtoNumber(8) val sources: List<ProtoSource> = emptyList(),
)

@Serializable
class ProtoResources(
    @ProtoNumber(1) val apkUrl: String = "",
    @ProtoNumber(2) val iconUrl: String = "",
)

@Serializable
class ProtoSource(
    @ProtoNumber(1) val id: Long = 0,
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val language: String = "",
    @ProtoNumber(4) val homeUrl: String = "",
    @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
    @ProtoNumber(7) val message: String? = null,
)

/** Legacy `repo.json`, which may point to an index v2 file. */
@Serializable
class LegacyRepoJson(
    @SerialName("index_v2") val indexV2: String? = null,
    val meta: LegacyMeta? = null,
)

@Serializable
class LegacyMeta(
    val name: String = "",
    val website: String = "",
    val signingKeyFingerprint: String = "",
)

/** Legacy `index.min.json` entry. */
@Serializable
class LegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String = "all",
    val code: Long,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<LegacySource> = emptyList(),
)

@Serializable
class LegacySource(
    val name: String,
    val lang: String,
    val id: String,
    val baseUrl: String = "",
)

/** App-side model of a repository and its catalogue. */
@Serializable
data class Repo(
    val url: String,
    val name: String,
    val badge: String,
    val signingKey: String,
    val website: String,
)

@Serializable
data class CatalogEntry(
    val name: String,
    val pkg: String,
    val apkUrl: String,
    val iconUrl: String,
    val lib: Double,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val contentWarning: ContentWarning,
    val sources: List<CatalogSource>,
    val repoUrl: String,
)

@Serializable
data class CatalogSource(val id: Long, val name: String, val lang: String, val baseUrl: String)

@Serializable
enum class ContentWarning { SAFE, MIXED, NSFW }

object RepoParser {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val proto = ProtoBuf

    fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
        val gzip = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        return if (gzip) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
    }

    sealed interface Parsed {
        class Store(val store: ProtoStore) : Parsed
        class Redirect(val url: String, val meta: LegacyMeta?) : Parsed
        class Legacy(val entries: List<LegacyExtension>) : Parsed
    }

    fun parse(raw: ByteArray): Parsed {
        val bytes = gunzipIfNeeded(raw)
        val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }
        return when (first) {
            '['.code.toByte() -> Parsed.Legacy(json.decodeFromString(bytes.decodeToString()))
            '{'.code.toByte() -> {
                val text = bytes.decodeToString()
                val legacy = runCatching { json.decodeFromString<LegacyRepoJson>(text) }.getOrNull()
                if (legacy?.indexV2 != null) {
                    Parsed.Redirect(legacy.indexV2, legacy.meta)
                } else {
                    Parsed.Store(json.decodeFromString<ProtoStore>(text))
                }
            }
            else -> Parsed.Store(proto.decodeFromByteArray(ProtoStore.serializer(), bytes))
        }
    }

    fun parseList(raw: ByteArray): ProtoExtensionList {
        val bytes = gunzipIfNeeded(raw)
        return if (bytes.firstOrNull() == '{'.code.toByte()) {
            json.decodeFromString(ProtoExtensionList.serializer(), bytes.decodeToString())
        } else {
            proto.decodeFromByteArray(ProtoExtensionList.serializer(), bytes)
        }
    }

    fun toCatalog(list: ProtoExtensionList, repoUrl: String): List<CatalogEntry> = list.extensions.map { e ->
        val langs = e.sources.map { it.language }.toSet()
        CatalogEntry(
            name = e.name.removePrefix("Tachiyomi: "),
            pkg = e.packageName,
            apkUrl = e.resources?.apkUrl.orEmpty(),
            iconUrl = e.resources?.iconUrl.orEmpty(),
            lib = e.extensionLib.toDoubleOrNull() ?: 0.0,
            versionCode = e.versionCode,
            versionName = e.versionName,
            lang = langs.singleOrNull() ?: "all",
            contentWarning = when (e.contentWarning) {
                2 -> ContentWarning.MIXED
                3 -> ContentWarning.NSFW
                else -> ContentWarning.SAFE
            },
            sources = e.sources.map { CatalogSource(it.id, it.name, it.language, it.homeUrl) },
            repoUrl = repoUrl,
        )
    }

    fun legacyToCatalog(list: List<LegacyExtension>, baseUrl: String, repoUrl: String) = list.map { e ->
        CatalogEntry(
            name = e.name.removePrefix("Tachiyomi: "),
            pkg = e.pkg,
            apkUrl = "$baseUrl/apk/${e.apk}",
            iconUrl = "$baseUrl/icon/${e.pkg}.png",
            lib = e.version.substringBeforeLast('.').toDoubleOrNull() ?: 0.0,
            versionCode = e.code,
            versionName = e.version,
            lang = e.lang,
            contentWarning = if (e.nsfw == 1) ContentWarning.NSFW else ContentWarning.SAFE,
            sources = e.sources.map { CatalogSource(it.id.toLongOrNull() ?: 0L, it.name, it.lang, it.baseUrl) },
            repoUrl = repoUrl,
        )
    }
}
