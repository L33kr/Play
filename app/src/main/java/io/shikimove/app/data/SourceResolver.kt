package io.shikimove.app.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.Normalizer
import java.util.Locale

object SourceResolver {
    fun normalizedTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** An ID match always wins. Never silently choose the first unrelated search result. */
    fun match(results: List<VideoSource>, id: Long, titles: List<String>): SourceMatch {
        val safe = results.filter { safePlayerUrl(it.link) != null }
        val byId = safe.filter { it.shikimoriId?.toLongOrNull() == id }
        if (byId.isNotEmpty()) return SourceMatch(sort(byId), true)
        val names = titles.map(::normalizedTitle).filter { it.isNotBlank() }.toSet()
        val byName = safe.filter {
            // A known different ID must not match even if two adaptations share a name.
            it.shikimoriId.isNullOrBlank() && normalizedTitle(it.originalTitle.orEmpty()) in names
        }
        return SourceMatch(sort(byName), false)
    }

    private fun sort(sources: List<VideoSource>) = sources.distinctBy { it.id to it.translation?.id }
        .sortedWith(compareByDescending<VideoSource> { it.episodeLimit }.thenBy { it.translationLabel })

    fun safePlayerUrl(value: String): HttpUrl? {
        val url = (if (value.startsWith("//")) "https:$value" else value).toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (!trustedPlayerHost(url.host)) return null
        return url
    }

    fun trustedPlayerHost(host: String): Boolean = listOf("kodikplayer.com", "kodik.info", "kodik.cc", "kodik.biz")
        .any { host == it || host.endsWith(".$it") }

    fun playerUrl(source: VideoSource, episode: Int): String? = safePlayerUrl(source.link)?.newBuilder()
        ?.setQueryParameter("translations", "true")
        ?.setQueryParameter("episode", episode.coerceAtLeast(1).toString())
        ?.setQueryParameter("hide_selectors", "false")
        ?.build()?.toString()

    fun amoveUrl(animeId: Long, sourceId: String = ""): String = "https://amove.my.to/".toHttpUrlOrNull()!!
        .newBuilder().fragment("shikimori-$animeId" + if (sourceId.isNotBlank()) "&$sourceId" else "").build().toString()

    fun validatedSettings(shiki: String, search: String): AppSettings {
        fun https(s: String): HttpUrl {
            val u = s.trim().toHttpUrlOrNull()
            require(u != null && u.isHttps && u.username.isEmpty() && u.password.isEmpty() && u.fragment == null) {
                "Введите корректный HTTPS-адрес без логина и пароля"
            }
            return u
        }
        val base = https(shiki)
        require(base.encodedPath == "/" && base.query == null) { "Для Shikimori нужен только домен, без пути" }
        val endpoint = https(search)
        require(endpoint.query == null) { "Укажите адрес поиска без параметров запроса" }
        return AppSettings(base.toString().trimEnd('/'), endpoint.toString())
    }
}
