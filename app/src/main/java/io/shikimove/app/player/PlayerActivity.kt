@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.shikimove.app.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.gson.Gson
import io.shikimove.app.data.*
import io.shikimove.app.openBrowser
import io.shikimove.app.ui.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    data class Payload(val anime: Anime, val source: VideoSource, val episode: Int, val seconds: Double, val sources: List<VideoSource>, val season: Int = 1)
    private lateinit var payload: Payload
    private lateinit var player: ExoPlayer
    private lateinit var store: LocalStore
    private val resolver = StreamResolver()
    private var loadJob: Job? = null
    private var source by mutableStateOf(VideoSource())
    private var episode by mutableStateOf(EpisodeKey())
    private var streams by mutableStateOf<StreamResolver.Streams?>(null)
    private var quality by mutableIntStateOf(720)
    private var speed by mutableFloatStateOf(1f)
    private var position by mutableLongStateOf(0)
    private var duration by mutableLongStateOf(0)
    private var playing by mutableStateOf(false)
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var controls by mutableStateOf(true)
    private var touch by mutableIntStateOf(0)
    private var sheet by mutableStateOf<String?>(null)
    private var autoNext by mutableStateOf(true)
    private var fill by mutableStateOf(false)
    private var watchedSeconds = 0.0
    private var marked = false
    private var activityStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("payload")?.takeIf { it.matches(Regex("player-[a-f0-9-]+\\.json")) }
        payload = runCatching { Gson().fromJson(File(cacheDir, name ?: "missing").readText(), Payload::class.java) }.getOrNull()
            ?: run { finish(); return }
        store = LocalStore.get(this)
        source = payload.sources.find { it.id == savedInstanceState?.getString("source") } ?: payload.source
        episode = EpisodeKey(savedInstanceState?.getInt("season") ?: payload.season, savedInstanceState?.getInt("episode") ?: payload.episode)
        if (episode !in source.playlist) episode = source.playlist.firstOrNull() ?: episode
        autoNext = getPreferences(MODE_PRIVATE).getBoolean("auto_next", true)
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                    if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                override fun onPlaybackStateChanged(state: Int) {
                    loading = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_ENDED) {
                        markWatched(); save()
                        if (autoNext) adjacent(1)?.let { load(source, it, 0.0) } else controls = true
                    }
                }
                override fun onPlayerError(exception: PlaybackException) {
                    loading = false; controls = true
                    error = when (exception.errorCode) {
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Не удалось подключиться к видеосерверу. Проверьте интернет и повторите."
                        PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Устройство не смогло воспроизвести этот поток. Попробуйте другое качество или озвучку."
                        else -> "Видеопоток недоступен. Обновите ссылку или выберите другую озвучку."
                    }
                }
            })
        }
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { ShikiTheme { PlayerScreen() } }
        load(source, episode, savedInstanceState?.getDouble("seconds") ?: payload.seconds)
        lifecycleScope.launch {
            var ticks = 0
            while (isActive) {
                delay(500)
                if (streams == null) continue
                val next = player.currentPosition.coerceAtLeast(0)
                if (playing && next >= position && next - position < 4000) watchedSeconds += (next - position) / 1000.0
                position = next; duration = player.duration.coerceAtLeast(0)
                if (duration > 0 && position >= duration * .9) markWatched()
                if (++ticks % 8 == 0) save()
            }
        }
    }
    private fun adjacent(direction: Int): EpisodeKey? {
        val list = source.playlist.filter { it.season == episode.season }
        val index = list.indexOf(episode)
        return if (index < 0) null else list.getOrNull(index + direction)
    }
    private fun markWatched() {
        if (!marked && episode.season == 1 && watchedSeconds >= minOf(30.0, duration / 1000.0 * .75) && watchedSeconds > 0) {
            store.watched(payload.anime, episode.episode); marked = true
            AccountRepository.get(this).sync()
        }
    }
    private fun save() {
        if (!::player.isInitialized || player.duration <= 0 || streams == null) return
        store.saveProgress(WatchProgress(payload.anime, source, episode.episode, player.currentPosition / 1000.0,
            player.duration / 1000.0, season = episode.season))
    }
    private fun load(value: VideoSource, key: EpisodeKey, seconds: Double) {
        save(); loadJob?.cancel(); player.stop(); player.clearMediaItems()
        source = value; episode = key; streams = null; error = null; loading = true; controls = true
        position = (seconds * 1000).toLong(); duration = 0; watchedSeconds = 0.0; marked = false
        loadJob = lifecycleScope.launch {
            try { val found = resolver.resolve(value, key); streams = found; playStream(seconds, true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { loading = false; error = if (e is ApiException) e.message else "Не удалось получить видео. Проверьте соединение и повторите." }
        }
    }
    private fun playStream(seconds: Double, start: Boolean) {
        val found = streams ?: return
        if (quality !in found.qualities) quality = found.qualities.keys.maxOrNull() ?: return
        val dataSource = DefaultHttpDataSource.Factory().setUserAgent(StreamResolver.USER_AGENT)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
            .setDefaultRequestProperties(mapOf("Referer" to found.referer, "Origin" to found.referer.trimEnd('/')))
        val media = MediaItem.Builder().setUri(found.qualities.getValue(quality)).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        player.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(media))
        player.seekTo((seconds * 1000).toLong().coerceAtLeast(0)); player.setPlaybackSpeed(speed)
        player.prepare(); player.playWhenReady = start && activityStarted
    }
    private fun seek(delta: Long) { player.seekTo((player.currentPosition + delta).coerceIn(0, player.duration.coerceAtLeast(0))); touch++ }

    @Composable private fun PlayerScreen() {
        LaunchedEffect(controls, playing, touch, sheet, loading) { if (controls && playing && sheet == null && !loading) { delay(4200); controls = false } }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { PlayerView(it).apply { player = this@PlayerActivity.player; useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) } },
                update = { it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT }, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onTap = { controls = !controls; touch++ }, onDoubleTap = { seek(if (it.x < size.width / 2) -10000 else 10000) })
            })
            if (loading && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(46.dp), color = Lavender)
            if (controls || error != null) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .72f), Color.Transparent, Color.Black.copy(alpha = .86f)))))
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { finish() }) { Symbol("back", description = "Назад") }
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(payload.anime.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text("${source.seasonLabel(episode.season)} · Серия ${episode.episode} · ${source.translationLabel}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { sheet = "settings" }) { Symbol("settings", description = "Настройки плеера") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (error != null) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 480.dp).padding(12.dp)) {
                            Text(error!!, style = MaterialTheme.typography.bodyMedium)
                            Row { TextButton(onClick = { load(source, episode, position / 1000.0) }) { Text("Повторить") }
                                TextButton(onClick = { sheet = "voice" }) { Text("Озвучка") } }
                        } else if (!loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                            TextButton(onClick = { seek(-10000) }) { Text("−10 с", color = Color.White) }
                            FilledIconButton(onClick = { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); if (playing) player.pause() else player.play(); touch++ },
                                modifier = Modifier.size(62.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .16f))) {
                                Symbol(if (playing) "pause" else "play", Modifier.size(30.dp), Color.White, if (playing) "Пауза" else "Воспроизвести")
                            }
                            TextButton(onClick = { seek(10000) }) { Text("+10 с", color = Color.White) }
                        }
                    }
                    var drag by remember { mutableStateOf<Float?>(null) }
                    Slider(value = drag ?: position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                        onValueChange = { drag = it; touch++ }, onValueChangeFinished = { drag?.let { player.seekTo(it.toLong()) }; drag = null },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f), enabled = duration > 0,
                        modifier = Modifier.fillMaxWidth().height(28.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${timeLabel((drag?.toLong() ?: position) / 1000.0)} / ${timeLabel(duration / 1000.0)}", fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { sheet = "quality" }) { Text("${quality}p", fontSize = 12.sp) }
                        TextButton(onClick = { sheet = "speed" }) { Text("${speed}×", fontSize = 12.sp) }
                        IconButton(onClick = { requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }) { Symbol("expand", description = "Повернуть экран") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { adjacent(-1)?.let { load(source, it, 0.0) } }, enabled = adjacent(-1) != null) { Text("Предыдущая") }
                        TextButton(onClick = { sheet = "episodes" }) { Text("Серии") }
                        TextButton(onClick = { adjacent(1)?.let { load(source, it, 0.0) } }, enabled = adjacent(1) != null) { Text("Следующая") }
                    }
                }
            }
        }
        if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null; touch++ }, containerColor = Panel) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp).navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                item { Text(when(sheet) { "quality" -> "Качество"; "speed" -> "Скорость"; "voice" -> "Озвучка"; "episodes" -> "Серии"; else -> "Настройки" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp)) }
                when (sheet) {
                    "quality" -> items(streams?.qualities?.keys?.toList().orEmpty()) { q -> TextButton(onClick = { val pos = player.currentPosition / 1000.0; val wasPlaying = player.playWhenReady; quality = q; error = null; playStream(pos, wasPlaying); sheet = null }, modifier = Modifier.fillMaxWidth()) { Text("${if (quality == q) "✓  " else ""}${q}p") } }
                    "speed" -> items(listOf(.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { value -> TextButton(onClick = { speed = value; player.setPlaybackSpeed(value); sheet = null }, modifier = Modifier.fillMaxWidth()) { Text("${if (speed == value) "✓  " else ""}${value}×") } }
                    "voice" -> items(payload.sources, key = { it.id }) { candidate -> TextButton(onClick = {
                        val key = episode.takeIf { it in candidate.playlist } ?: candidate.playlist.firstOrNull()
                        if (key != null) { val pos = if (key == episode) position / 1000.0 else 0.0; sheet = null; load(candidate, key, pos) }
                    }, enabled = candidate.playlist.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("${if (source.id == candidate.id) "✓  " else ""}${candidate.translationLabel}") } }
                    "episodes" -> items(source.playlist) { key -> TextButton(onClick = { sheet = null; load(source, key, 0.0) }, modifier = Modifier.fillMaxWidth()) { Text("${if (episode == key) "✓  " else ""}${source.seasonLabel(key.season)} · Серия ${key.episode}") } }
                    else -> {
                        item { TextButton(onClick = { sheet = "voice" }) { Text("Озвучка · ${source.translationLabel}") } }
                        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Следующая серия автоматически", Modifier.weight(1f)); Switch(autoNext, { autoNext = it; getPreferences(MODE_PRIVATE).edit().putBoolean("auto_next", it).apply() }) } }
                        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Заполнить экран", Modifier.weight(1f)); Switch(fill, { fill = it }) } }
                        item { TextButton(onClick = { store.watched(payload.anime, episode.episode); AccountRepository.get(this@PlayerActivity).sync(); sheet = null }, enabled = episode.season == 1) { Text("Отметить серию просмотренной") } }
                        item { TextButton(onClick = { openBrowser(this@PlayerActivity, SourceResolver.amoveUrl(payload.anime.id, source.id)) }) { Text("Открыть на amove") } }
                    }
                }
            }
        }
    }
    override fun onStart() { super.onStart(); activityStarted = true }
    override fun onStop() { activityStarted = false; if (::player.isInitialized) { save(); player.pause() }; super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("source", source.id); outState.putInt("season", episode.season); outState.putInt("episode", episode.episode)
        outState.putDouble("seconds", position / 1000.0); super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { loadJob?.cancel(); if (::player.isInitialized) player.release(); super.onDestroy() }
    companion object {
        fun launch(context: Context, anime: Anime, source: VideoSource, episode: Int, seconds: Double, sources: List<VideoSource>, season: Int = 1) {
            val file = File(context.cacheDir, "player-${UUID.randomUUID()}.json")
            file.writeText(Gson().toJson(Payload(anime, source, episode, seconds, sources, season)))
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("payload", file.name))
        }
    }
}
