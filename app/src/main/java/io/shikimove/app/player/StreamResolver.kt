package io.shikimove.app.player

import com.google.gson.JsonParser
import io.shikimove.app.data.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Public player protocol, adapted from pewaru-333/ShikiApp (GPL-3.0). No API tokens. */
class StreamResolver(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build()) {
    data class Streams(val qualities: Map<Int, String>, val referer: String)

    suspend fun resolve(source: VideoSource, episode: EpisodeKey): Streams {
        val page = source.episodeUrl(episode)?.let(SourceResolver::safePlayerUrl)
            ?: throw ApiException("Ссылка на эту серию не найдена. Обновите список озвучек.")
        val html = fetch(Request.Builder().url(page).build())
        val paramsText = Regex("""(?s)urlParams\s*=\s*['"]?(\{.*?\})['"]?\s*;""")
            .find(html)?.groupValues?.get(1) ?: throw ApiException("Источник изменил плеер или ограничил доступ. Попробуйте другую озвучку.")
        val params = runCatching { JsonParser.parseString(paramsText).asJsonObject }.getOrElse { throw ApiException("Не удалось прочитать параметры плеера") }
        val scriptPath = Regex("""src=["'](/assets/js/[^"']*player[^"']+\.js)["']""")
            .find(html)?.groupValues?.get(1) ?: throw ApiException("Не найден скрипт плеера")
        val script = fetch(Request.Builder().url(page.resolve(scriptPath)!!).build())
        val endpoint = postPath(script)
        val form = FormBody.Builder()
        for (key in listOf("type", "hash", "id")) {
            val value = Regex("""\.$key\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)
                ?: throw ApiException("Не найден идентификатор видео")
            form.add(key, value)
        }
        for (key in listOf("d", "d_sign", "pd", "pd_sign", "ref", "ref_sign")) form.add(key, params.get(key)?.asString.orEmpty())
        form.add("bad_user", "true").add("cdn_is_working", "true")
        // Keep the player page's signed ref unchanged. Do not inject the amove Referer here.
        val body = fetch(Request.Builder().url(page.resolve(endpoint)!!).post(form.build()).build())
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse { throw ApiException("Источник не вернул видеопоток") }
        if (json.has("error")) throw ApiException("Источник отказал в воспроизведении. Попробуйте другую озвучку или повторите позже.")
        val qualities = json.getAsJsonObject("links")?.entrySet().orEmpty().mapNotNull { (key, value) ->
            val quality = key.toIntOrNull() ?: return@mapNotNull null
            val url = runCatching { value.asJsonArray.firstNotNullOfOrNull { item -> decodeStream(item.asJsonObject.get("src").asString) } }.getOrNull()
            url?.let { quality to it }
        }.toMap().toSortedMap(compareByDescending { it })
        if (qualities.isEmpty()) throw ApiException("Не удалось получить видео. Попробуйте другую озвучку.")
        return Streams(qualities, "${page.scheme}://${page.host}/")
    }

    private suspend fun fetch(request: Request): String = client.await(request.newBuilder().header("User-Agent", USER_AGENT).build()).use {
        if (!it.isSuccessful) throw ApiException("Источник видео ответил HTTP ${it.code}. Попробуйте ещё раз или смените озвучку.")
        it.body?.string() ?: throw ApiException("Пустой ответ источника")
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        fun postPath(script: String): String {
            val match = Regex("""\$.ajax\(\{type:["']POST["'],url:(?:atob\()?["']([^"']+)["']\)?""")
                .find(script)?.groupValues?.get(1) ?: throw ApiException("Изменился протокол источника видео")
            val path = if (match.startsWith('/')) match else runCatching { String(Base64.getDecoder().decode(match)) }.getOrNull()
            if (path == null || !path.startsWith('/') || path.startsWith("//") || path.contains('\\')) throw ApiException("Некорректный адрес источника")
            return path
        }
        fun decodeStream(value: String): String? {
            fun valid(raw: String): String? {
                val url = (if (raw.startsWith("//")) "https:$raw" else raw).toHttpUrlOrNull() ?: return null
                return url.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.encodedPath.endsWith(".m3u8") }?.toString()
            }
            valid(value)?.let { return it }
            for (shift in 0..25) {
                val rotated = value.map { c -> when(c) {
                    in 'A'..'Z' -> ('A'.code + (c - 'A' + shift) % 26).toChar()
                    in 'a'..'z' -> ('a'.code + (c - 'a' + shift) % 26).toChar()
                    else -> c
                } }.joinToString("")
                val decoded = runCatching { String(Base64.getDecoder().decode(rotated)) }.getOrNull() ?: continue
                valid(decoded)?.let { return it }
            }
            return null
        }
    }
}
