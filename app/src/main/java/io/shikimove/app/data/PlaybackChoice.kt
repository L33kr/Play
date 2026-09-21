package io.shikimove.app.data

/** Selects a valid starting point for the single Watch action on the detail screen. */
data class PlaybackChoice(val source: VideoSource, val episode: EpisodeKey, val seconds: Double) {
    companion object {
        fun choose(sources: List<VideoSource>, progress: WatchProgress?, watchedEpisodes: Int = 0): PlaybackChoice? {
            val playable = sources.filter { it.playlist.isNotEmpty() }
            val savedSource = progress?.let { saved ->
                playable.find { it.id == saved.source.id && it.id.isNotBlank() }
                    ?: saved.source.translation?.id?.let { id -> playable.find { it.translation?.id == id } }
            }
            val source = savedSource ?: playable.firstOrNull() ?: return null
            val savedEpisode = progress?.let { EpisodeKey(it.season, it.episode) }
            if (savedSource != null && savedEpisode in source.playlist) {
                val seconds = progress!!.seconds.takeIf { it.isFinite() && it >= 0 } ?: 0.0
                // A finished film or last episode starts over instead of opening on the end frame.
                val ended = progress.duration > 0 && seconds >= progress.duration - 1
                val next = source.playlist.filter { it.season == savedEpisode!!.season }
                    .let { it.getOrNull(it.indexOf(savedEpisode) + 1) }
                return PlaybackChoice(source, if (ended) next ?: savedEpisode!! else savedEpisode!!, if (ended) 0.0 else seconds)
            }
            val nextUnwatched = EpisodeKey(1, watchedEpisodes.coerceAtLeast(0) + 1)
            return PlaybackChoice(source, nextUnwatched.takeIf { it in source.playlist } ?: source.playlist.first(), 0.0)
        }
    }
}
