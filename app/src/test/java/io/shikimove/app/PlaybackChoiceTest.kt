package io.shikimove.app

import io.shikimove.app.data.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackChoiceTest {
    private val anime = Anime(id=1)
    private fun series(id: String = "serial-1", voice: Long = 7) = VideoSource(id=id, translation=Translation(voice), seasons=mapOf(
        "1" to VideoSeason(episodes=mapOf("1" to VideoEpisode("one"), "2" to VideoEpisode("two"))),
        "0" to VideoSeason(episodes=mapOf("1" to VideoEpisode("special")))))

    @Test fun `watch starts a film without an episode selector`() {
        val movie = VideoSource(id="film", link="//kodikplayer.com/video/1/hash/720p")
        assertEquals(PlaybackChoice(movie, EpisodeKey(1,1), 0.0), PlaybackChoice.choose(listOf(movie), null))
    }
    @Test fun `watch preserves voice season episode and position`() {
        val wanted = series("wanted", 15)
        val saved = WatchProgress(anime, wanted, episode=1, seconds=32.0, duration=1400.0, season=0)
        assertEquals(PlaybackChoice(wanted, EpisodeKey(0,1), 32.0), PlaybackChoice.choose(listOf(series(), wanted), saved))
    }
    @Test fun `an unavailable voice does not apply an unrelated saved position`() {
        val choice = PlaybackChoice.choose(listOf(series()), WatchProgress(anime, series("gone", 99), seconds=320.0))!!
        assertEquals(0.0, choice.seconds, 0.0)
        assertEquals("serial-1", choice.source.id)
    }
    @Test fun `finished episode advances within its season and finished film restarts`() {
        val source = series()
        assertEquals(EpisodeKey(1,2), PlaybackChoice.choose(listOf(source), WatchProgress(anime, source, seconds=1400.0,duration=1400.0))!!.episode)
        val movie = VideoSource(id="film",link="//kodikplayer.com/video/1/hash/720p")
        assertEquals(0.0, PlaybackChoice.choose(listOf(movie), WatchProgress(anime,movie,seconds=1400.0,duration=1400.0))!!.seconds, 0.0)
    }
    @Test fun `account episode count selects next available episode and empty playlists fail cleanly`() {
        assertEquals(EpisodeKey(1,2), PlaybackChoice.choose(listOf(series()), null, 1)!!.episode)
        assertNull(PlaybackChoice.choose(listOf(VideoSource(link="//kodikplayer.com/serial/1/hash/720p")), null))
    }
}
