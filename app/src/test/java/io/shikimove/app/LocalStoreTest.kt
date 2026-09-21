package io.shikimove.app

import android.content.Context
import com.google.gson.Gson
import io.shikimove.app.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalStoreTest {
    private val context get() = RuntimeEnvironment.getApplication() as Context
    private val anime = Anime(id=9253, name="Steins;Gate", episodes=24)
    @Before fun clean() { context.getSharedPreferences("shikimove", Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun `guest data and pending account mutations never cross account boundaries`() {
        val store = LocalStore(context)
        store.setShelf(anime, Shelf.PLANNED)
        assertTrue(store.pending.value.isEmpty())
        store.account(ShikiUser(10,"first")); assertTrue(store.library.value.isEmpty())
        store.setShelf(anime, Shelf.WATCHING); assertEquals(1, store.pending.value.size)
        store.account(ShikiUser(20,"second")); assertTrue(store.library.value.isEmpty()); assertTrue(store.pending.value.isEmpty())
        store.account(ShikiUser(10,"first")); assertEquals("watching", store.pending.value.single().status)
        store.account(null); assertEquals(Shelf.PLANNED, store.library.value.single().shelf)
    }
    @Test fun `acknowledging old network request preserves newer local edit`() {
        val store = LocalStore(context); store.account(ShikiUser(10,"first")); store.setShelf(anime, Shelf.WATCHING)
        val inFlight = store.pending.value.single()
        store.setScore(anime, 9); store.acknowledge(10, inFlight)
        assertEquals(9, store.pending.value.single().score)
        store.applyRemote(10, listOf(UserRate(id=99,status="planned",score=3,anime=anime)))
        assertEquals(Shelf.WATCHING, store.library.value.single().shelf); assertEquals(9, store.library.value.single().score)
    }
    @Test fun `queue persists across process recreation and ignores another owner acknowledgement`() {
        val first = LocalStore(context); first.account(ShikiUser(10,"first")); first.watched(anime, 4)
        val restored = LocalStore(context); assertEquals(4, restored.pending.value.single().episodes)
        restored.acknowledge(20, restored.pending.value.single()); assertEquals(1, restored.pending.value.size)
    }
    @Test fun `old history without season migrates to main season and retains position`() {
        val progress = WatchProgress(anime, VideoSource(id="old"), episode=7,seconds=312.0)
        val json = Gson().toJsonTree(progress).asJsonObject; json.remove("season")
        context.getSharedPreferences("shikimove", Context.MODE_PRIVATE).edit().putString("history", "[$json]").commit()
        val restored = LocalStore(context).history.value.single()
        assertEquals(1, restored.season); assertEquals(7, restored.episode); assertEquals(312.0, restored.seconds, 0.0)
    }
}
