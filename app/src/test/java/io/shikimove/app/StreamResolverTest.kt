package io.shikimove.app

import io.shikimove.app.data.*
import io.shikimove.app.player.StreamResolver
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class StreamResolverTest {
    @Test fun `all public player rotations resolve without inventing quality URLs`() {
        val url = "//cdn.example.org/signed/path/480.mp4:hls:manifest.m3u8?expires=123&token=sample"
        val b64 = Base64.getEncoder().withoutPadding().encodeToString(url.toByteArray())
        for (shift in 0..25) {
            val encoded = b64.map { c -> when(c) { in 'A'..'Z' -> ('A'.code + (c - 'A' + shift) % 26).toChar(); in 'a'..'z' -> ('a'.code + (c - 'a' + shift) % 26).toChar(); else -> c } }.joinToString("")
            assertEquals("https:$url", StreamResolver.decodeStream(encoded))
        }
    }
    @Test fun `invalid and insecure streams rejected`() {
        assertNull(StreamResolver.decodeStream("http://cdn.example/a.m3u8"))
        assertNull(StreamResolver.decodeStream("https://user:pass@cdn.example/a.m3u8"))
        assertNull(StreamResolver.decodeStream("javascript:alert(1)"))
        assertNull(StreamResolver.decodeStream("a broken provider response"))
    }
    @Test fun `post endpoint can be encoded or plain but stays on player origin`() {
        assertEquals("/ftor", StreamResolver.postPath("$.ajax({type:\"POST\",url:atob(\"L2Z0b3I=\")"))
        assertEquals("/ftor", StreamResolver.postPath("$.ajax({type:\"POST\",url:\"/ftor\""))
        try { StreamResolver.postPath("$.ajax({type:\"POST\",url:\"//attacker.example\""); fail() } catch (_: ApiException) { }
    }
    @Test fun `episode gaps and specials remain explicit`() {
        val source = VideoSource(seasons = mapOf("0" to VideoSeason(episodes = mapOf("1" to VideoEpisode("special"))),
            "1" to VideoSeason(episodes = mapOf("3" to VideoEpisode("three"), "1" to VideoEpisode("one")))))
        assertEquals(listOf(EpisodeKey(1,1), EpisodeKey(1,3), EpisodeKey(0,1)), source.playlist)
        assertNull(source.episodeUrl(EpisodeKey(1,2)))
        assertEquals("special", source.episodeUrl(EpisodeKey(0,1)))
    }
}
