package app.yomu.core

import java.text.Normalizer
import java.util.Locale

/** One comic in the library, with everything the search can look at. */
data class SearchDoc(
    val key: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    /** Genres/tags given by the source. */
    val genres: List<String> = emptyList(),
    /** Hashtags the user added. */
    val userTags: List<String> = emptyList(),
    /** Text that read-aloud found on the comic's pages (speech bubbles, narration). */
    val comicText: List<String> = emptyList(),
)

/**
 * Search over the library, entirely offline.
 *
 * - `#tag` words filter by hashtag (source genres + own hashtags). Spelling, case, accents,
 *   spaces and dashes don't matter (`#sliceoflife` = "Slice of Life"), and related words count
 *   (`#fun` finds "Comedy", `#liebe` finds "Romance").
 * - Everything else is a text search in one of two modes:
 *   - [Mode.NAME]: the title (word starts, small typos allowed), the author as a weaker match.
 *   - [Mode.STORY]: description, genres/hashtags and the text inside the comic. Finds the word
 *     with its endings ("fun" → "funny", not "funeral") and, weaker, related words
 *     ("fun" → "hilarious", "lustig", "Comedy").
 */
object LibrarySearch {

    enum class Mode { NAME, STORY }

    enum class Where(val label: String) {
        TITLE("Name"),
        AUTHOR("Autor"),
        DESCRIPTION("Beschreibung"),
        COMIC("Im Comic"),
        TAG("Hashtag"),
    }

    data class Query(
        /** Hashtags as typed, without the '#'. */
        val tags: List<String>,
        /** The free text, trimmed. */
        val text: String,
    ) {
        val isEmpty get() = tags.isEmpty() && text.isBlank()
    }

    /** A piece of text with the matched words marked (ranges are into [text]). */
    data class Snippet(val text: String, val highlights: List<IntRange>)

    data class Hit(
        val doc: SearchDoc,
        val score: Int,
        val where: Where?,
        val snippet: Snippet?,
        /** The comic's hashtags that matched (display form). */
        val matchedTags: List<String>,
    )

    data class TagCount(val tag: String, val count: Int)

    // ------------------------------------------------------------------ parsing

    fun parse(raw: String): Query {
        val tags = mutableListOf<String>()
        val words = mutableListOf<String>()
        for (token in raw.split(Regex("\\s+"))) {
            if (token.startsWith("#")) {
                // "#fun,#action" and "#fun#action" both work.
                token.split(Regex("[#,;]+")).map { it.trim() }.filter { tagKey(it).isNotEmpty() }.forEach { tags += it }
            } else if (token.isNotBlank()) {
                words += token
            }
        }
        return Query(tags.distinctBy { tagKey(it) }, words.joinToString(" ").trim())
    }

    // ------------------------------------------------------------------ normalising

    /** Lower case, no accents, ß → ss. Keeps the length of plain ASCII text. */
    fun fold(s: String): String {
        val lower = s.lowercase(Locale.ROOT).replace("ß", "ss")
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(MARKS, "")
    }

    private val MARKS = Regex("\\p{Mn}+")

    /** Hashtag identity: folded, letters and digits only ("Sci-Fi" → "scifi"). */
    fun tagKey(tag: String): String = fold(tag).filter { it.isLetterOrDigit() }

    /** Splits a source's genre string into single genres. */
    fun splitGenres(genre: String?): List<String> =
        genre.orEmpty().split(',', ';', '|', '\n').map { it.trim() }.filter { tagKey(it).isNotEmpty() }
            .distinctBy { tagKey(it) }

    /** Splits user input like "#fun lustig, Slice-of-Life" into hashtags. Spaces separate tags. */
    fun splitUserTags(input: String): List<String> =
        input.split(Regex("[\\s,;#]+")).map { it.trim() }.filter { tagKey(it).isNotEmpty() }.distinctBy { tagKey(it) }

    // ------------------------------------------------------------------ related words

    /**
     * Groups of words that mean (nearly) the same, English and German, as folded tag keys.
     * Used for hashtags and for "related" matches in story mode.
     */
    private val GROUPS: List<Set<String>> = listOf(
        setOf("comedy", "comedic", "humor", "humour", "humorous", "funny", "fun", "lustig", "witzig", "spass", "komodie", "gag", "gags", "hilarious", "joke", "jokes", "witz", "witze"),
        setOf("romance", "romantic", "romantik", "romantisch", "romanze", "love", "liebe", "lovestory", "liebesgeschichte", "verliebt"),
        setOf("action", "fight", "fights", "fighting", "kampf", "kampfe", "battle", "battles", "schlacht"),
        setOf("horror", "scary", "creepy", "gruselig", "grusel", "unheimlich"),
        setOf("fantasy", "magic", "magical", "magie", "magisch", "zauber", "zauberei"),
        setOf("sliceoflife", "alltag", "everyday", "everydaylife"),
        setOf("drama", "dramatic", "dramatisch"),
        setOf("adventure", "adventures", "abenteuer"),
        setOf("mystery", "mysteries", "mysterious", "mysterium", "geheimnis", "geheimnisvoll", "ratsel", "detective", "detektiv", "krimi"),
        setOf("scifi", "sciencefiction"),
        setOf("sports", "sport"),
        setOf("school", "schoollife", "highschool", "schule", "schulleben"),
        setOf("isekai", "reincarnation", "reincarnated", "wiedergeburt", "wiedergeboren"),
        setOf("tragedy", "tragic", "tragodie", "tragisch", "sad", "traurig"),
        setOf("thriller", "suspense", "spannung", "spannend"),
        setOf("psychological", "psychologisch", "psycho"),
        setOf("supernatural", "ubernaturlich"),
        setOf("martialarts", "kampfkunst", "kampfkunste", "kungfu", "wuxia", "murim"),
        setOf("historical", "historisch", "history", "geschichte"),
        setOf("music", "musik"),
        setOf("cooking", "kochen", "food", "essen", "gourmet"),
        setOf("mecha", "robot", "robots", "roboter"),
        setOf("cute", "suss", "niedlich", "wholesome"),
        setOf("vampire", "vampires", "vampir"),
        setOf("zombie", "zombies"),
        setOf("demon", "demons", "damon", "damonen"),
        setOf("revenge", "rache"),
        setOf("war", "krieg"),
        setOf("crime", "verbrechen"),
        setOf("family", "familie"),
        setOf("friendship", "freundschaft", "friends", "freunde"),
        setOf("monster", "monsters"),
        setOf("cultivation", "kultivierung"),
        setOf("dungeon", "dungeons"),
        setOf("game", "games", "gaming", "spiel", "spiele", "videogame", "videogames"),
        setOf("police", "polizei"),
        setOf("medical", "medizin", "doctor", "arzt"),
        setOf("office", "buro", "officeworkers"),
        setOf("villainess", "schurkin"),
    )

    private val GROUP_OF: Map<String, Int> = buildMap {
        GROUPS.forEachIndexed { i, g -> g.forEach { put(it, i) } }
    }

    /** The group of [key], also trying the key without a plural/inflection ending. */
    private fun groupOf(key: String): Int? = GROUP_OF[key] ?: stems(key).firstNotNullOfOrNull { GROUP_OF[it] }

    /** [key] with a word ending removed ("battles" → "battle", "funny" → "fun"). */
    private fun stems(key: String): List<String> =
        ENDINGS.filter { key.endsWith(it) && endingOk(key.length - it.length, it) }.map { key.dropLast(it.length) }

    private fun related(key: String): Set<String> = groupOf(key)?.let { GROUPS[it] }.orEmpty() - key

    // ------------------------------------------------------------------ hashtags

    private fun docTags(doc: SearchDoc): List<String> = (doc.genres + doc.userTags).distinctBy { tagKey(it) }

    /** How well a comic's tag [tag] answers the query tag [q]: 3 same, 2 related, 1 starts with, 0 no. */
    fun tagScore(tag: String, q: String): Int {
        val t = tagKey(tag)
        val k = tagKey(q)
        if (t.isEmpty() || k.isEmpty()) return 0
        if (t == k) return 3
        val gt = groupOf(t)
        if (gt != null && gt == groupOf(k)) return 2
        // Same word with an ending ("#sport" → "Sports"), or half typed ("#fant" → "Fantasy").
        if (wordMatches(t, k) || wordMatches(k, t)) return 2
        if (k.length >= 4 && t.startsWith(k)) return 1
        return 0
    }

    /** All hashtags in [docs], most used first. */
    fun allTags(docs: List<SearchDoc>): List<TagCount> {
        val display = HashMap<String, String>()
        val count = HashMap<String, Int>()
        for (doc in docs) {
            for (tag in docTags(doc)) {
                val k = tagKey(tag)
                display.putIfAbsent(k, tag)
                count[k] = (count[k] ?: 0) + 1
            }
        }
        return count.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { TagCount(display.getValue(it.key), it.value) }
    }

    /** Hashtags that fit a half-typed tag, for suggestions while typing `#…`. */
    fun suggestTags(docs: List<SearchDoc>, partial: String): List<TagCount> {
        val k = tagKey(partial)
        val all = allTags(docs)
        if (k.isEmpty()) return all
        return all.filter { tagKey(it.tag).startsWith(k) || tagScore(it.tag, partial) >= 2 }
            .sortedByDescending { if (tagKey(it.tag).startsWith(k)) 1 else 0 }
    }

    // ------------------------------------------------------------------ search

    fun search(docs: List<SearchDoc>, query: Query, mode: Mode): List<Hit> {
        if (query.isEmpty) return docs.sortedBy { fold(it.title) }.map { Hit(it, 0, null, null, emptyList()) }

        val results = mutableListOf<Hit>()
        for (doc in docs) {
            // Every hashtag of the query has to match.
            var tagPoints = 0
            val matchedTags = mutableListOf<String>()
            var tagsOk = true
            for (q in query.tags) {
                val best = docTags(doc).maxByOrNull { tagScore(it, q) }
                val score = best?.let { tagScore(it, q) } ?: 0
                if (score == 0) {
                    tagsOk = false
                    break
                }
                tagPoints += score * 10
                matchedTags += best!!
            }
            if (!tagsOk) continue

            if (query.text.isBlank()) {
                results += Hit(doc, tagPoints, if (matchedTags.isEmpty()) null else Where.TAG, null, matchedTags.distinct())
                continue
            }
            val text = when (mode) {
                Mode.NAME -> matchName(doc, query.text)
                Mode.STORY -> matchStory(doc, query.text)
            } ?: continue
            results += text.copy(score = text.score + tagPoints, matchedTags = (matchedTags + text.matchedTags).distinct())
        }
        return results.sortedWith(compareByDescending<Hit> { it.score }.thenBy { fold(it.doc.title) })
    }

    // ------------------------------------------------------------------ name mode

    private fun matchName(doc: SearchDoc, text: String): Hit? {
        val q = fold(text).trim()
        val terms = words(q).map { it.value }
        if (terms.isEmpty()) return null

        val title = fold(doc.title).trim()
        val compactTitle = title.filter { it.isLetterOrDigit() }
        val compactQuery = q.filter { it.isLetterOrDigit() }
        val titleWords = words(title).map { it.value }

        val score = when {
            title == q || (compactQuery.isNotEmpty() && compactTitle == compactQuery) -> 100
            title.startsWith(q) -> 85
            terms.all { t -> titleWords.any { it.startsWith(t) } } -> 70
            compactQuery.length >= 2 && compactTitle.contains(compactQuery) -> 50
            terms.all { t -> titleWords.any { it.startsWith(t) || closeEnough(it, t) } } -> 30
            else -> 0
        }
        if (score > 0) return Hit(doc, score, Where.TITLE, highlight(doc.title, terms, fuzzy = true), emptyList())

        val author = doc.author?.takeIf { it.isNotBlank() } ?: return null
        val authorWords = words(fold(author)).map { it.value }
        if (terms.all { t -> authorWords.any { it.startsWith(t) } }) {
            return Hit(doc, 20, Where.AUTHOR, highlight(author, terms, fuzzy = false), emptyList())
        }
        return null
    }

    /** One typo (swap, missing, extra or wrong letter) in a word of 4+ letters. */
    private fun closeEnough(word: String, term: String): Boolean {
        if (term.length < 4) return false
        // Compare with the start of the word too, so a half-typed word can have a typo.
        val candidates = listOf(word, word.take(term.length), word.take(term.length + 1), word.take(term.length - 1))
        return candidates.any { it.length >= 3 && withinOneEdit(it, term) }
    }

    private fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val diff = a.indices.filter { a[it] != b[it] }
            if (diff.size == 1) return true
            // Two neighbouring letters swapped.
            return diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]]
        }
        val (short, long) = if (a.length < b.length) a to b else b to a
        var i = 0
        while (i < short.length && short[i] == long[i]) i++
        return short.substring(i) == long.substring(i + 1)
    }

    // ------------------------------------------------------------------ story mode

    /** Endings a word of 4+ letters may have and still count as the same word (English + German). */
    private val ENDINGS = setOf(
        "s", "es", "e", "n", "en", "er", "ern", "ers", "em", "y", "ny", "ly", "ily", "ed", "d", "ing", "ning",
        "nier", "niest", "nies", "ier", "iest", "ies", "est", "st", "t", "te", "ten", "ig", "ige", "igen", "iger",
        "lich", "liche", "lichen", "licher", "ful", "al", "ness",
    )

    /** Three-letter words are easy to confuse ("war" / "warn"), so they only take these endings. */
    private val SHORT_ENDINGS = setOf("s", "es", "ly", "ny", "nier", "niest", "nies", "ning", "ness")

    private fun endingOk(stemLength: Int, ending: String): Boolean = when {
        stemLength >= 4 -> ending in ENDINGS
        stemLength == 3 -> ending in SHORT_ENDINGS
        else -> false
    }

    /** "fun" matches "fun", "funny", "funnier"; not "funeral", "fund" or "function". */
    private fun wordMatches(word: String, term: String): Boolean {
        if (word == term) return true
        if (!word.startsWith(term)) return false
        return endingOk(term.length, word.substring(term.length))
    }

    private val STOPWORDS = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "is", "it", "on", "with",
        "der", "die", "das", "den", "dem", "des", "und", "oder", "ein", "eine", "einen", "ist", "im", "in", "mit", "zu",
    )

    private class Field(val where: Where, val text: String)

    private fun matchStory(doc: SearchDoc, text: String): Hit? {
        val all = words(fold(text)).map { it.value }
        if (all.isEmpty()) return null
        // Small words only count when they're all there is.
        val terms = all.filter { it !in STOPWORDS }.ifEmpty { all }

        val fields = buildList {
            doc.description?.takeIf { it.isNotBlank() }?.let { add(Field(Where.DESCRIPTION, it)) }
            doc.comicText.filter { it.isNotBlank() }.forEach { add(Field(Where.COMIC, it)) }
        }
        val tags = docTags(doc)

        var score = 0
        val matchedTags = mutableListOf<String>()
        var bestField: Field? = null
        var bestFieldPoints = 0
        val highlightTerms = mutableSetOf<String>()

        for (term in terms) {
            val rel = related(term)
            var termPoints = 0

            // In the description / the comic itself.
            for (field in fields) {
                val fieldWords = words(fold(field.text)).map { it.value }
                val direct = fieldWords.count { wordMatches(it, term) }
                val near = if (rel.isEmpty()) 0 else fieldWords.count { w -> rel.any { wordMatches(w, it) } }
                if (direct == 0 && near == 0) continue
                val weight = if (field.where == Where.DESCRIPTION) 1.0 else 0.8
                // The word itself counts much more than a related word.
                val points = ((minOf(direct, 3) * 20 + minOf(near, 3) * 6) * weight).toInt().coerceAtLeast(1)
                termPoints += points
                if (points > bestFieldPoints || (direct > 0 && bestField == null)) {
                    bestFieldPoints = points
                    bestField = field
                }
                highlightTerms += term
                highlightTerms += rel
            }

            // In the genres / hashtags.
            val tagHit = tags.maxByOrNull { tagScore(it, term) }?.takeIf { tagScore(it, term) >= 2 }
            if (tagHit != null) {
                termPoints += if (tagScore(tagHit, term) == 3) 25 else 12
                matchedTags += tagHit
            }

            if (termPoints == 0) return null // every word has to be found somewhere
            score += termPoints
        }

        // The words next to each other, as typed, is worth extra.
        val phrase = fold(text).trim()
        if (terms.size > 1 && fields.any { fold(it.text).contains(phrase) }) score += 30

        val field = bestField
        return if (field != null) {
            Hit(doc, score, field.where, snippet(field.text, highlightTerms), matchedTags.distinct())
        } else {
            Hit(doc, score, Where.TAG, null, matchedTags.distinct())
        }
    }

    // ------------------------------------------------------------------ text helpers

    /** Words of [s] (letters/digits), with their positions. */
    private fun words(s: String): List<MatchResult> = WORD.findAll(s).toList()

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    /** Positions of words in [original] matching any of [terms] (terms are folded). */
    private fun matchRanges(original: String, terms: Collection<String>, prefix: Boolean, fuzzy: Boolean): List<IntRange> =
        words(original).filter { m ->
            val w = fold(m.value)
            terms.any { t ->
                if (prefix) w.startsWith(t) || (fuzzy && closeEnough(w, t)) else wordMatches(w, t)
            }
        }.map { it.range }

    private fun highlight(original: String, terms: List<String>, fuzzy: Boolean): Snippet =
        Snippet(original, matchRanges(original, terms, prefix = true, fuzzy = fuzzy))

    /** About [radius] characters around the first match, cut at word boundaries. */
    private fun snippet(original: String, terms: Collection<String>, radius: Int = 60): Snippet {
        val clean = original.replace(Regex("\\s+"), " ").trim()
        val ranges = matchRanges(clean, terms, prefix = false, fuzzy = false)
        val first = ranges.firstOrNull() ?: return Snippet(clean.take(radius * 2).let { if (clean.length > it.length) "$it …" else it }, emptyList())
        var start = (first.first - radius).coerceAtLeast(0)
        var end = (first.last + 1 + radius).coerceAtMost(clean.length)
        if (start > 0) clean.indexOf(' ', start).takeIf { it in start until first.first }?.let { start = it + 1 }
        if (end < clean.length) clean.lastIndexOf(' ', end).takeIf { it > first.last }?.let { end = it }
        val prefix = if (start > 0) "… " else ""
        val suffix = if (end < clean.length) " …" else ""
        val body = clean.substring(start, end)
        val shift = prefix.length - start
        val inside = ranges.filter { it.first >= start && it.last < end }.map { (it.first + shift)..(it.last + shift) }
        return Snippet(prefix + body + suffix, inside)
    }
}
