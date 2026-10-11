@file:Suppress("unused")

package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/** A source that can turn a web link into a manga or chapter (used for "open link in app"). */
interface ResolvableSource : Source {
    fun getUriType(uri: String): UriType
    suspend fun getManga(uri: String): SManga?
    suspend fun getChapter(uri: String): SChapter?
}

sealed interface UriType {
    data object Manga : UriType
    data object Chapter : UriType
    data object Unknown : UriType
}
