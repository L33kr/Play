package io.shikimove.app.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(message: String) : IOException(message)

class AnimeApi(
    private val settings: () -> AppSettings,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build(),
    private val minRequestGap: Long = 650,
) {
    private val gson = Gson()
    private val shikiMutex = Mutex()
    private var lastRequest = 0L

    suspend fun catalog(query: String, filter: String, page: Int): List<Anime> {
        val url = settings().shikiBase.toHttpUrl().newBuilder().addPathSegments("api/animes")
            .addQueryParameter("limit", "24").addQueryParameter("page", page.toString())
            .addQueryParameter("censored", "true")
            .addQueryParameter("order", if (query.isBlank()) "popularity" else "ranked")
        if (query.isNotBlank()) url.addQueryParameter("search", query.trim())
        if (filter in setOf("ongoing", "anons")) url.addQueryParameter("status", filter)
        if (filter == "movie") url.addQueryParameter("kind", "movie")
        return shikiRequest {
            parse(get(url.build()), Array<Anime>::class.java).toList().filter { it.id > 0 }
        }
    }

    suspend fun anime(id: Long): Anime = shikiRequest {
        parse(get(settings().shikiBase.toHttpUrl().newBuilder().addPathSegments("api/animes/$id").build()), Anime::class.java)
    }

    suspend fun sources(anime: Anime): SourceMatch {
        val byId = search("shikimori_id", anime.id.toString())
        val match = SourceResolver.match(byId, anime.id, listOf(anime.name, anime.title))
        if (match.sources.isNotEmpty()) return match
        // Fallback happens only after a successful empty/mismatched response, never after an HTTP failure.
        val byTitle = search("title", anime.name.ifBlank { anime.title })
        return SourceResolver.match(byTitle, anime.id, listOf(anime.name, anime.title))
    }

    private suspend fun search(key: String, value: String): List<VideoSource> {
        val url = settings().videoSearch.toHttpUrl().newBuilder().addQueryParameter(key, value)
            .addQueryParameter("with_episodes_data", "true").build()
        val response = parse(get(url, video = true), SourceResponse::class.java)
        return response.results.orEmpty()
    }

    private suspend fun <T> shikiRequest(block: suspend () -> T): T = shikiMutex.withLock {
        val now = System.nanoTime() / 1_000_000
        delay((minRequestGap - (now - lastRequest)).coerceAtLeast(0))
        lastRequest = System.nanoTime() / 1_000_000
        block()
    }

    private inline fun <reified T> parse(body: String, type: Class<T>): T = try {
        gson.fromJson(body, type) ?: throw ApiException("Сервис вернул пустой ответ")
    } catch (error: JsonParseException) {
        throw ApiException("Сервис вернул неожиданный ответ. Возможно, требуется проверка в браузере.")
    }

    private suspend fun get(url: HttpUrl, video: Boolean = false): String {
        val request = Request.Builder().url(url).header("User-Agent", "ShikiMove/0.1.0 (Android)")
            .header("Accept", "application/json")
        if (video) request.header("Origin", "https://amove.my.to").header("Referer", "https://amove.my.to/")
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!it.isSuccessful) throw ApiException(when (it.code) {
                                403 -> "Сервис ограничил доступ. Попробуйте открыть сайт в браузере или изменить адрес в настройках."
                                429 -> "Слишком много запросов. Подождите немного и повторите."
                                404 -> "Не найдено. Возможно, адрес сервиса изменился."
                                else -> "Сервис временно недоступен (HTTP ${it.code})"
                            })
                            val body = it.body?.string() ?: throw ApiException("Пустой ответ сервиса")
                            if (continuation.isActive) continuation.resume(body)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            })
        }
    }
}
