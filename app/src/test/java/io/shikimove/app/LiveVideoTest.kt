package io.shikimove.app

import io.shikimove.app.data.*
import io.shikimove.app.player.StreamResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

/** Opt in explicitly; regular CI is independent of third-party streaming uptime. */
class LiveVideoTest {
    @Test fun `resolve actual episode and read playable HLS segment`() = runBlocking {
        assumeTrue(System.getenv("SHIKIMOVE_LIVE_VIDEO") == "1")
        val builder = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS)
        System.getenv("HTTPS_PROXY")?.let { raw -> URI(raw).let { uri -> builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))) } }
        val client = builder.build()
        val result = AnimeApi({ AppSettings() }, client).sources(Anime(id=9253,name="Steins;Gate"))
        val source = result.sources.first { EpisodeKey(1,1) in it.playlist }
        val streams = StreamResolver(client).resolve(source, EpisodeKey(1,1))
        assertTrue(streams.qualities.isNotEmpty())
        val uri = streams.qualities[360] ?: streams.qualities.values.first()
        val hls = client.await(Request.Builder().url(uri).header("Referer", streams.referer).build()).use {
            assertTrue("Playlist HTTP ${it.code}", it.isSuccessful); it.body!!.string()
        }
        assertTrue(hls.startsWith("#EXTM3U"))
        val path = hls.lineSequence().first { it.isNotBlank() && !it.startsWith('#') }
        val segment = URI(uri).resolve(path).toString()
        client.await(Request.Builder().url(segment).header("Referer", streams.referer).build()).use {
            assertTrue("Segment HTTP ${it.code}", it.isSuccessful)
            val bytes = it.body!!.bytes(); assertTrue(bytes.size > 1000)
            // MPEG-TS sync bytes establish that this is media rather than an HTML error.
            assertEquals(0x47.toByte(), bytes[0]); assertEquals(0x47.toByte(), bytes[188])
        }
        println("Live video OK: ${streams.qualities.keys.sorted()}p, HLS and MPEG-TS verified")
    }
}
