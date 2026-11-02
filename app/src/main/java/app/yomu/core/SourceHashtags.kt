package app.yomu.core

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * `#tag` in a source search: each hashtag switches on the source's own genre filter that fits
 * (same matching as the library: `#fun` → "Comedy", `#sliceoflife` → "Slice of Life").
 * Hashtags the source has no genre for are searched as normal words instead.
 */
object SourceHashtags {

    class Prepared(
        /** Text to send to the source. */
        val text: String,
        val filters: FilterList,
        /** Hashtag → the source's genre it switched on. */
        val matched: List<Pair<String, String>>,
        /** Hashtags this source has no genre for (searched as text). */
        val unmatched: List<String>,
    ) {
        /** One line for the screen, or null when there were no hashtags. */
        fun note(): String? {
            if (matched.isEmpty() && unmatched.isEmpty()) return null
            val parts = matched.map { (tag, genre) ->
                if (LibrarySearch.tagKey(tag) == LibrarySearch.tagKey(genre)) "#$genre ✓" else "#$tag → $genre"
            } + unmatched.map { "kein Genre „$it“, als Text gesucht" }
            return parts.joinToString(" · ")
        }
    }

    /** A filter entry that can be switched on, e.g. one genre checkbox. */
    private class Option(val label: String, val owner: Any, val switchOn: () -> Unit)

    /** [filters] must be a fresh list from the source; it is changed in place. */
    fun prepare(raw: String, filters: FilterList): Prepared {
        val query = LibrarySearch.parse(raw)
        if (query.tags.isEmpty()) return Prepared(raw.trim(), filters, emptyList(), emptyList())

        val options = options(filters)
        val used = HashSet<Any>()
        val matched = mutableListOf<Pair<String, String>>()
        val unmatched = mutableListOf<String>()
        for (tag in query.tags) {
            val best = options
                .filter { it.owner !in used }
                .map { it to LibrarySearch.tagScore(it.label, tag) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first
            if (best == null) {
                unmatched += tag
            } else {
                best.switchOn()
                used += best.owner
                matched += tag to best.label
            }
        }
        val text = (listOf(query.text) + unmatched).filter { it.isNotBlank() }.joinToString(" ")
        return Prepared(text, filters, matched, unmatched)
    }

    private fun isExclusion(name: String): Boolean {
        val n = LibrarySearch.fold(name)
        return listOf("exclu", "without", "blacklist", "ausschl", "ohne", "hide", "ausblend").any { it in n }
    }

    private fun options(filters: List<*>): List<Option> = buildList {
        for (f in filters) {
            when (f) {
                // Never switch on an "exclude genres" list by accident.
                is Filter.Group<*> -> if (!isExclusion(f.name)) addAll(options(f.state))
                is Filter.TriState -> add(Option(f.name, f) { f.state = Filter.TriState.STATE_INCLUDE })
                is Filter.CheckBox -> add(Option(f.name, f) { f.state = true })
                // A drop-down (e.g. "Genre: Alle / Action / Comedy …"): only one value can be picked.
                is Filter.Select<*> -> f.values.forEachIndexed { i, v ->
                    if (i > 0) add(Option(v.toString(), f) { f.state = i })
                }
                else -> Unit
            }
        }
    }
}
