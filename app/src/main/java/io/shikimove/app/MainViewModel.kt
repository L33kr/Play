package io.shikimove.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.shikimove.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class CatalogState(
    val query: String = "", val filter: String = "popular", val items: List<Anime> = emptyList(),
    val loading: Boolean = false, val loadingMore: Boolean = false, val hasMore: Boolean = true,
    val error: String? = null, val page: Int = 1,
)
data class DetailState(val anime: Anime, val loading: Boolean = true, val sourcesLoading: Boolean = true,
    val sources: SourceMatch? = null, val error: String? = null, val sourceError: String? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val store = LocalStore.get(application)
    val account = AccountRepository.get(application)
    private val api = AnimeApi(settings = { store.settings.value })
    private val _catalog = MutableStateFlow(CatalogState())
    val catalog = _catalog.asStateFlow()
    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail = _detail.asStateFlow()
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var sourceJob: Job? = null
    init { search() }

    fun search(query: String = _catalog.value.query, filter: String = _catalog.value.filter, debounce: Boolean = false) {
        catalogJob?.cancel()
        _catalog.value = CatalogState(query = query, filter = filter, loading = true)
        catalogJob = viewModelScope.launch {
            if (debounce) delay(450)
            try {
                val items = api.catalog(query, filter, 1)
                _catalog.update { it.copy(items = items, loading = false, hasMore = items.size == 24) }
            } catch (e: Exception) { rethrowCancellation(e); _catalog.update { it.copy(loading = false, error = message(e)) } }
        }
    }

    fun more() {
        val snapshot = _catalog.value
        if (snapshot.loading || snapshot.loadingMore || !snapshot.hasMore) return
        catalogJob = viewModelScope.launch {
            _catalog.update { it.copy(loadingMore = true, error = null) }
            try {
                val items = api.catalog(snapshot.query, snapshot.filter, snapshot.page + 1)
                _catalog.update { it.copy(items = (it.items + items).distinctBy(Anime::id), page = it.page + 1,
                    loadingMore = false, hasMore = items.size == 24) }
            } catch (e: Exception) { rethrowCancellation(e); _catalog.update { it.copy(loadingMore = false, error = message(e)) } }
        }
    }

    fun open(anime: Anime) {
        detailJob?.cancel(); sourceJob?.cancel()
        _detail.value = DetailState(anime)
        detailJob = viewModelScope.launch {
            try {
                val full = api.anime(anime.id)
                _detail.update { it?.copy(anime = full, loading = false) }
            } catch (e: Exception) { rethrowCancellation(e); _detail.update { it?.copy(loading = false, error = message(e)) } }
        }
        loadSources(anime)
    }

    fun loadSources(anime: Anime? = _detail.value?.anime) {
        val target = anime ?: return
        sourceJob?.cancel()
        _detail.update { it?.copy(sourcesLoading = true, sourceError = null) }
        sourceJob = viewModelScope.launch {
            try {
                val result = api.sources(target)
                _detail.update { it?.copy(sourcesLoading = false, sources = result) }
            } catch (e: Exception) { rethrowCancellation(e); _detail.update { it?.copy(sourcesLoading = false, sourceError = message(e)) } }
        }
    }

    fun close() { detailJob?.cancel(); sourceJob?.cancel(); _detail.value = null }
    fun settings(value: AppSettings) { store.setSettings(value); close(); search() }

    private fun rethrowCancellation(e: Exception) { if (e is CancellationException) throw e }
    private fun message(e: Exception) = if (e is ApiException) e.message.orEmpty() else if (e is IOException)
        "Не удалось подключиться. Проверьте интернет и доступность сервиса." else "Не удалось прочитать данные. Попробуйте ещё раз."
}
