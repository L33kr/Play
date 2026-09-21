package io.shikimove.app

import io.shikimove.app.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AnimeApiTest {
    private fun api(server: MockWebServer) = AnimeApi(settings = { AppSettings(server.url("/").toString().trimEnd('/'), server.url("/search").toString()) }, minRequestGap = 0)

    @Test fun `search containing spaces Cyrillic and ampersand stays a single query parameter`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            api(server).catalog("Врата Штейна & Steins Gate", "popular", 1)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("Врата Штейна & Steins Gate", request.requestUrl!!.queryParameter("search"))
            assertEquals("true", request.requestUrl!!.queryParameter("censored"))
        }
    }
    @Test fun `provider empty ID response triggers encoded title fallback and correct Origin`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"total":0,"results":[]}"""))
            server.enqueue(MockResponse().setBody("""{"total":1,"results":[{"id":"s1","link":"//kodikplayer.com/serial/1/hash/720p","title_orig":"Steins;Gate","shikimori_id":"9253"}]}"""))
            val result = api(server).sources(Anime(id = 9253, name = "Steins;Gate"))
            assertEquals(1, result.sources.size)
            val first = server.takeRequest(2, TimeUnit.SECONDS)!!
            val second = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("9253", first.requestUrl!!.queryParameter("shikimori_id"))
            assertEquals("true", first.requestUrl!!.queryParameter("with_episodes_data"))
            assertEquals("Steins;Gate", second.requestUrl!!.queryParameter("title"))
            assertEquals("https://amove.my.to", second.getHeader("Origin"))
        }
    }
    @Test fun `provider outage is an error and does not trigger misleading title fallback`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            try { api(server).sources(Anime(id = 9253, name = "Steins;Gate")); fail("Expected error") }
            catch (e: ApiException) { assertTrue(e.message!!.contains("503")) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun `HTML challenge yields readable API error`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>Checking your browser</html>"))
            try { api(server).catalog("", "popular", 1); fail("Expected error") }
            catch (e: ApiException) { assertTrue(e.message!!.contains("браузере")) }
        }
    }
}
