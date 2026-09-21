package io.shikimove.app.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shikimove", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _user = MutableStateFlow(read("account", ShikiUser::class.java))
    val user = _user.asStateFlow()
    private fun key(name: String) = _user.value?.let { "${name}_user_${it.id}" } ?: name
    private val _library = MutableStateFlow(read(key("library"), Array<LibraryEntry>::class.java)?.toList().orEmpty())
    private val _history = MutableStateFlow(readHistory())
    private val _pending = MutableStateFlow(read(key("pending"), Array<PendingRate>::class.java)?.toList().orEmpty())
    private val _settings = MutableStateFlow(read("settings", AppSettings::class.java) ?: AppSettings())
    val library = _library.asStateFlow()
    val history = _history.asStateFlow()
    val pending = _pending.asStateFlow()
    val settings = _settings.asStateFlow()
    private fun <T> read(key: String, type: Class<T>): T? = runCatching { gson.fromJson(prefs.getString(key, null), type) }.getOrNull()
    private fun write(name: String, value: Any) { prefs.edit().putString(key(name), gson.toJson(value)).commit() }

    private fun readHistory(): List<WatchProgress> {
        val raw = prefs.getString(key("history"), null) ?: return emptyList()
        return runCatching {
            com.google.gson.JsonParser.parseString(raw).asJsonArray.map { item ->
                val progress = gson.fromJson(item, WatchProgress::class.java)
                if (item.asJsonObject.has("season")) progress else progress.copy(season = 1)
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized fun account(value: ShikiUser?) {
        val previous = _user.value?.id
        _user.value = value
        prefs.edit().putString("account", gson.toJson(value)).commit()
        if (previous == value?.id) return
        _library.value = read(key("library"), Array<LibraryEntry>::class.java)?.toList().orEmpty()
        _history.value = readHistory()
        _pending.value = read(key("pending"), Array<PendingRate>::class.java)?.toList().orEmpty()
    }

    @Synchronized fun setShelf(anime: Anime, shelf: Shelf?) {
        val old = _library.value.find { it.anime.id == anime.id }
        _library.value = _library.value.filterNot { it.anime.id == anime.id }.let { list ->
            if (shelf == null) list else listOf((old ?: LibraryEntry(anime, shelf)).copy(shelf = shelf, changedAt = System.currentTimeMillis())) + list
        }
        write("library", _library.value)
        queue(anime) { previous -> if (shelf == null) PendingRate(anime, delete = true)
            else (previous?.takeUnless { it.delete } ?: PendingRate(anime)).copy(status = shelf.apiValue, automatic = false, revision = revision()) }
    }

    @Synchronized fun setScore(anime: Anime, score: Int) {
        val old = _library.value.find { it.anime.id == anime.id } ?: return
        val value = score.coerceIn(0, 10)
        _library.value = _library.value.map { if (it.anime.id == anime.id) old.copy(score = value) else it }
        write("library", _library.value)
        queue(anime) { (it?.takeUnless(PendingRate::delete) ?: PendingRate(anime)).copy(score = value, revision = revision()) }
    }

    @Synchronized fun watched(anime: Anime, episode: Int) {
        val old = _library.value.find { it.anime.id == anime.id }
        if (episode <= (old?.episodes ?: 0)) return
        val count = if (anime.episodes > 0) episode.coerceAtMost(anime.episodes) else episode
        val shelf = if (anime.episodes > 0 && count >= anime.episodes) Shelf.COMPLETED else
            old?.shelf?.takeIf { it == Shelf.REWATCHING || it == Shelf.COMPLETED } ?: Shelf.WATCHING
        _library.value = listOf((old ?: LibraryEntry(anime, shelf)).copy(shelf = shelf, episodes = count, changedAt = System.currentTimeMillis())) + _library.value.filterNot { it.anime.id == anime.id }
        write("library", _library.value)
        queue(anime) { (it?.takeUnless(PendingRate::delete) ?: PendingRate(anime)).copy(status = it?.status?.takeIf { _ -> !it.automatic } ?: shelf.apiValue,
            episodes = maxOf(count, it?.episodes ?: 0), automatic = it?.let { pending -> pending.automatic || pending.status == null } ?: true, revision = revision()) }
    }

    private fun queue(anime: Anime, change: (PendingRate?) -> PendingRate) {
        if (_user.value == null) return
        _pending.value = _pending.value.filterNot { it.anime.id == anime.id } + change(_pending.value.find { it.anime.id == anime.id })
        write("pending", _pending.value)
    }
    @Synchronized fun acknowledge(owner: Long, mutation: PendingRate) {
        if (_user.value?.id != owner) return
        _pending.value = _pending.value.filterNot { it.revision == mutation.revision }
        write("pending", _pending.value)
    }
    @Synchronized fun applyRemote(owner: Long, rates: List<UserRate>) {
        if (_user.value?.id != owner) return
        val pendingIds = _pending.value.map { it.anime.id }.toSet()
        val remote = rates.mapNotNull { rate -> rate.anime?.let { LibraryEntry(it, Shelf.fromApi(rate.status), episodes = rate.episodes, score = rate.score, remoteRateId = rate.id) } }
        _library.value = _library.value.filter { it.anime.id in pendingIds } + remote.filterNot { it.anime.id in pendingIds }
        write("library", _library.value)
    }
    @Synchronized fun saveProgress(progress: WatchProgress) {
        _history.value = (listOf(progress.copy(updatedAt = System.currentTimeMillis())) + _history.value.filterNot { it.anime.id == progress.anime.id }).take(200)
        prefs.edit().putString(key("history"), gson.toJson(_history.value)).apply()
    }
    @Synchronized fun clearHistory() { _history.value = emptyList(); prefs.edit().remove(key("history")).apply() }
    @Synchronized fun setSettings(value: AppSettings) { _settings.value = value; prefs.edit().putString("settings", gson.toJson(value)).apply() }
    private fun revision() = java.util.UUID.randomUUID().toString()
    companion object {
        @Volatile private var instance: LocalStore? = null
        fun get(context: Context): LocalStore = instance ?: synchronized(this) { instance ?: LocalStore(context).also { instance = it } }
    }
}
