package io.shikimove.app

import io.shikimove.app.data.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AccountApiTest {
    @Test fun `updating score preserves unrelated remote status and notes`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""[{"id":99,"episodes":12,"status":"completed","score":7}]"""))
            server.enqueue(MockResponse().setBody("{}"))
            AccountApi("session=test", server.url("/").toString(), gap = 0).push(42, PendingRate(Anime(id=9253), score=9))
            val get = server.takeRequest(); val patch = server.takeRequest()
            assertEquals("9253", get.requestUrl!!.queryParameter("target_id"))
            assertEquals("PATCH", patch.method); assertEquals("/api/v2/user_rates/99", patch.path)
            val fields = JsonParser.parseString(patch.body.readUtf8()).asJsonObject.getAsJsonObject("user_rate")
            assertEquals(setOf("score"), fields.keySet()); assertEquals(9, fields["score"].asInt)
            assertEquals("session=test", patch.getHeader("Cookie"))
        }
    }
    @Test fun `automatic watched progress never moves remote episode count backwards`() {
        val fields = AccountApi.mutationFields(42, PendingRate(Anime(id=9253), episodes=3), UserRate(id=99,episodes=12))
        assertEquals(12, fields["episodes"])
    }
    @Test fun `new entry has owner and target and explicit zero score`() {
        val fields = AccountApi.mutationFields(42, PendingRate(Anime(id=9253), status="watching",score=0), null)
        assertEquals(42L, fields["user_id"]); assertEquals(9253L, fields["target_id"]); assertEquals(0, fields["score"])
    }
    @Test fun `redirect does not forward account cookie to another origin`() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", second.url("/stolen")))
            try { AccountApi("session=test", first.url("/").toString(), gap=0).whoami(); fail() } catch (_: SessionExpired) { }
            assertEquals(0, second.requestCount)
        } }
    }
    @Test fun `delete retries safely after remote record is gone`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            AccountApi("session=test", server.url("/").toString(), gap=0).push(42, PendingRate(Anime(id=9253), delete=true))
            assertEquals(1, server.requestCount)
        }
    }
}
