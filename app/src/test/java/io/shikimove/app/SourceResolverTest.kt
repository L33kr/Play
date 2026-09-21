package io.shikimove.app

import com.google.gson.Gson
import io.shikimove.app.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class SourceResolverTest {
    private fun source(id: String? = "9253", title: String = "Steins;Gate", link: String = "//kodikplayer.com/serial/1/hash/720p") =
        VideoSource(id = "serial-1", link = link, originalTitle = title, shikimoriId = id, episodesCount = 26, lastEpisode = 24)

    @Test fun `exact IDs survive numeric and string JSON types`() {
        for (id in listOf("9253", "\"9253\"")) {
            val source = Gson().fromJson("""{"id":"x","link":"//kodikplayer.com/serial/1/hash/720p","shikimori_id":$id} """, VideoSource::class.java)
            assertTrue(SourceResolver.match(listOf(source), 9253, listOf("irrelevant")).exact)
        }
    }
    @Test fun `empty or wrong ID results never play an arbitrary first result`() {
        assertTrue(SourceResolver.match(emptyList(), 9253, listOf("Steins;Gate")).sources.isEmpty())
        assertTrue(SourceResolver.match(listOf(source("9999")), 9253, listOf("Steins;Gate")).sources.isEmpty())
    }
    @Test fun `fallback normalizes Unicode spaces case and punctuation without selecting another show`() {
        val matched = SourceResolver.match(listOf(source(null, "ＳＴＥＩＮＳ : Gate"), source(null, "Other anime")), 9253, listOf("Steins;Gate"))
        assertEquals(1, matched.sources.size)
        assertFalse(matched.exact)
    }
    @Test fun `player query preserves existing parameters without double question marks`() {
        val url = SourceResolver.playerUrl(source(link="https://kodikplayer.com/serial/1/hash/720p?foo=bar&episode=2"), 8)!!.toHttpUrl()
        assertEquals("bar", url.queryParameter("foo"))
        assertEquals(listOf("8"), url.queryParameterValues("episode"))
        assertEquals("true", url.queryParameter("translations"))
    }
    @Test fun `unsafe player links are rejected`() {
        listOf("javascript:alert(1)", "http://kodikplayer.com/serial/1", "https://kodikplayer.com.evil.test/serial/1", "https://evil.test/?kodikplayer.com", "https://user:pass@kodikplayer.com/serial/1").forEach {
            assertNull(it, SourceResolver.safePlayerUrl(it))
        }
    }
    @Test fun `last episode takes priority over total count including specials`() {
        assertEquals(24, source().episodeLimit)
    }
    @Test fun `custom endpoint validation requires HTTPS and clean base`() {
        assertEquals("https://shikimori.io", SourceResolver.validatedSettings(" https://shikimori.io/ ", "https://api.andb.workers.dev/search").shikiBase)
        for (invalid in listOf("http://shikimori.io", "https://shikimori.io/api", "https://login:password@shikimori.io")) {
            assertThrows(IllegalArgumentException::class.java) { SourceResolver.validatedSettings(invalid, "https://api.andb.workers.dev/search") }
        }
    }
}
