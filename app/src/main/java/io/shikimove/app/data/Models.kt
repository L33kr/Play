package io.shikimove.app.data

import com.google.gson.annotations.SerializedName

data class AnimeImage(val original: String? = null, val preview: String? = null)
data class Genre(val id: Long = 0, val name: String? = null, val russian: String? = null)
data class Anime(
    val id: Long = 0,
    val name: String = "",
    val russian: String? = null,
    val image: AnimeImage? = null,
    val kind: String? = null,
    val score: String? = null,
    val status: String? = null,
    val episodes: Int = 0,
    @SerializedName("episodes_aired") val episodesAired: Int = 0,
    @SerializedName("aired_on") val airedOn: String? = null,
    val description: String? = null,
    val genres: List<Genre>? = null,
) {
    val title: String get() = russian?.takeIf { it.isNotBlank() } ?: name
    val year: String get() = airedOn?.take(4) ?: "—"
    val format: String get() = when (kind) {
        "tv" -> "Сериал"; "movie" -> "Фильм"; "ova" -> "OVA"; "ona" -> "ONA"
        "special", "tv_special" -> "Спецвыпуск"; else -> kind?.uppercase() ?: "Аниме"
    }
    val statusLabel: String get() = when (status) {
        "ongoing" -> "Выходит"; "anons" -> "Анонс"; "released" -> "Вышло"; else -> ""
    }
}

data class Translation(val id: Long = 0, val title: String? = null, val type: String? = null)
data class VideoEpisode(val link: String = "")
data class VideoSeason(val title: String? = null, val episodes: Map<String, VideoEpisode>? = null)
data class EpisodeKey(val season: Int = 1, val episode: Int = 1)
data class VideoSource(
    val id: String = "",
    val link: String = "",
    val title: String? = null,
    @SerializedName("title_orig") val originalTitle: String? = null,
    @SerializedName("shikimori_id") val shikimoriId: String? = null,
    @SerializedName("episodes_count") val episodesCount: Int = 0,
    @SerializedName("last_episode") val lastEpisode: Int = 0,
    val translation: Translation? = null,
    val quality: String? = null,
    val seasons: Map<String, VideoSeason>? = null,
) {
    val translationLabel: String get() = translation?.title?.takeIf { it.isNotBlank() } ?: "Плеер"
    val episodeLimit: Int get() = if (lastEpisode > 0) lastEpisode else episodesCount.coerceAtLeast(1)
    val playlist: List<EpisodeKey> get() = seasons.orEmpty().flatMap { (season, value) ->
        val number = season.toIntOrNull() ?: return@flatMap emptyList()
        value.episodes.orEmpty().keys.mapNotNull { it.toIntOrNull()?.let { episode -> EpisodeKey(number, episode) } }
    }.sortedWith(compareBy<EpisodeKey> { if (it.season == 0) Int.MAX_VALUE else it.season }.thenBy { it.episode })
        .ifEmpty { if ("/video/" in link) listOf(EpisodeKey()) else emptyList() }
    fun episodeUrl(key: EpisodeKey): String? = seasons?.get(key.season.toString())?.episodes?.get(key.episode.toString())?.link
        ?: link.takeIf { "/video/" in it && key.episode == 1 }
    fun seasonLabel(season: Int): String = seasons?.get(season.toString())?.title?.takeIf(String::isNotBlank)
        ?: if (season == 0) "Спецвыпуски" else "Сезон $season"
}
data class SourceResponse(val total: Int = 0, val results: List<VideoSource>? = null)
data class SourceMatch(val sources: List<VideoSource>, val exact: Boolean)

enum class Shelf(val label: String) {
    PLANNED("В планах"), WATCHING("Смотрю"), COMPLETED("Просмотрено"), ON_HOLD("Отложено"), DROPPED("Брошено"), REWATCHING("Пересматриваю");
    val apiValue: String get() = name.lowercase(java.util.Locale.ROOT)
    companion object { fun fromApi(value: String?) = entries.find { it.apiValue == value } ?: PLANNED }
}
data class LibraryEntry(val anime: Anime, val shelf: Shelf, val changedAt: Long = System.currentTimeMillis(),
    val episodes: Int = 0, val score: Int = 0, val remoteRateId: Long? = null)
data class ShikiUser(val id: Long = 0, val nickname: String = "", val avatar: String? = null)
data class UserRate(val id: Long = 0, val status: String = "planned", val episodes: Int = 0, val score: Int = 0,
    val anime: Anime? = null, @SerializedName("target_id") val targetId: Long = 0)
data class PendingRate(val anime: Anime, val status: String? = null, val episodes: Int? = null,
    val score: Int? = null, val automatic: Boolean = false, val delete: Boolean = false, val revision: String = java.util.UUID.randomUUID().toString())
data class WatchProgress(
    val anime: Anime,
    val source: VideoSource,
    val episode: Int = 1,
    val seconds: Double = 0.0,
    val duration: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val season: Int = 1,
)
data class AppSettings(
    val shikiBase: String = "https://shikimori.io",
    val videoSearch: String = "https://api.andb.workers.dev/search",
)
