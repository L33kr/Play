package io.shikimove.app.data

import com.google.gson.Gson
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SessionExpired : java.io.IOException("Сессия закончилась. Войдите в Shikimori снова.")

/** Cookies are attached only to the fixed Shikimori origin; redirects are deliberately not followed. */
class AccountApi(private val cookie: String, private val base: String = ORIGIN,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build(), private val gap: Long = 700) {
    private val gson = Gson()
    private var lastRequest = 0L
    suspend fun whoami(): ShikiUser? = gson.fromJson(request("api/users/whoami"), ShikiUser::class.java)?.takeIf { it.id > 0 }
    suspend fun rates(owner: Long): List<UserRate> {
        val result = mutableListOf<UserRate>()
        var page = 1
        while (true) {
            val batch = gson.fromJson(request("api/users/$owner/anime_rates?limit=500&page=$page"), Array<UserRate>::class.java)
                ?: throw ApiException("Пустой ответ списка Shikimori")
            result += batch.take(500)
            if (batch.size <= 500) break
            page++
        }
        return result.distinctBy { it.id }
    }
    suspend fun push(owner: Long, pending: PendingRate) {
        val existing = gson.fromJson(request("api/v2/user_rates?user_id=$owner&target_id=${pending.anime.id}&target_type=Anime"), Array<UserRate>::class.java)?.firstOrNull()
        if (pending.delete) {
            if (existing != null) request("api/v2/user_rates/${existing.id}", "DELETE")
            return
        }
        val fields = mutationFields(owner, pending, existing)
        val data = gson.toJson(mapOf("user_rate" to fields))
        request(if (existing == null) "api/v2/user_rates" else "api/v2/user_rates/${existing.id}", if (existing == null) "POST" else "PATCH", data)
    }
    private suspend fun request(path: String, method: String = "GET", body: String? = null): String {
        val elapsed = System.nanoTime() / 1_000_000 - lastRequest
        delay((gap - elapsed).coerceAtLeast(0)); lastRequest = System.nanoTime() / 1_000_000
        val url = base.toHttpUrl().resolve(path) ?: throw ApiException("Некорректный путь API")
        check(url.host == base.toHttpUrl().host && url.port == base.toHttpUrl().port)
        val request = Request.Builder().url(url).header("User-Agent", "ShikiMove/0.2.0 (Android; github.com/L33kr/Play)")
            .header("Accept", "application/json").header("Cookie", cookie)
            .method(method, if (method in listOf("POST", "PATCH")) (body ?: "{}").toRequestBody("application/json".toMediaType()) else null).build()
        return client.await(request).use {
            if (it.code == 401 || it.code in 300..399) throw SessionExpired()
            if (!it.isSuccessful) throw ApiException(when (it.code) {
                403 -> "Shikimori отклонил запрос. Проверьте вход в аккаунт."
                429 -> "Shikimori просит подождать. Изменения сохранены для повторной отправки."
                else -> "Ошибка синхронизации: HTTP ${it.code}. Изменения сохранены."
            })
            it.body?.string().orEmpty()
        }
    }
    companion object {
        const val ORIGIN = "https://shikimori.io/"
        fun mutationFields(owner: Long, pending: PendingRate, existing: UserRate?): Map<String, Any> = buildMap {
            if (existing == null) { put("user_id", owner); put("target_id", pending.anime.id); put("target_type", "Anime"); put("status", "planned") }
            pending.status?.let { if (!(pending.automatic && existing?.status == "completed" && it != "completed")) put("status", it) }
            pending.episodes?.let { put("episodes", maxOf(it, existing?.episodes ?: 0)) }
            pending.score?.let { put("score", it.coerceIn(0, 10)) }
        }
    }
}
