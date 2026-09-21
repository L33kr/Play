@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.shikimove.app.ui

import android.content.Intent
import io.shikimove.app.account.LoginActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.shikimove.app.*
import io.shikimove.app.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable fun ShikiApp(vm: MainViewModel = viewModel()) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val library by vm.store.library.collectAsStateWithLifecycle()
    val history by vm.store.history.collectAsStateWithLifecycle()
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("catalog") }
    BackHandler(detail != null) { vm.close() }
    Scaffold(containerColor = Night, bottomBar = {
        if (detail == null) NavigationBar(containerColor = Night, tonalElevation = 0.dp) {
            listOf(Triple("catalog", "grid", "Каталог"), Triple("library", "bookmark", "Мой список"),
                Triple("settings", "account", "Профиль")).forEach { (id, icon, label) ->
                NavigationBarItem(selected = tab == id, onClick = { tab = id },
                    icon = { Symbol(icon, color = if (tab == id) Lavender else Muted) }, label = { Text(label, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Panel, selectedTextColor = Lavender, unselectedTextColor = Muted))
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (detail != null) DetailPage(detail!!, library.find { it.anime.id == detail!!.anime.id },
                history.find { it.anime.id == detail!!.anime.id }, settings, vm)
            else when (tab) {
                "catalog" -> CatalogPage(catalog, history.firstOrNull(), settings.shikiBase, vm)
                "library" -> LibraryPage(library, settings.shikiBase, vm::open)
                "history" -> HistoryPage(history, settings.shikiBase, vm::open)
                else -> SettingsPage(settings, vm)
            }
        }
    }
}

@Composable private fun BrandHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Lavender), contentAlignment = Alignment.Center) {
            Symbol("play", Modifier.size(20.dp), Night)
        }
        Spacer(Modifier.width(10.dp))
        Text("shiki", fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.8).sp)
        Text("move", color = Lavender, fontWeight = FontWeight.Light, fontSize = 22.sp, letterSpacing = (-0.8).sp)
        Spacer(Modifier.weight(1f))
        
    }
}

@Composable private fun CatalogPage(state: CatalogState, recent: WatchProgress?, base: String, vm: MainViewModel) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.query, state.filter) { gridState.scrollToItem(0) }
    LazyVerticalGrid(GridCells.Fixed(2), state = gridState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                BrandHeader()
                
                OutlinedTextField(value = state.query, onValueChange = { vm.search(query = it, debounce = true) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), singleLine = true,
                    shape = RoundedCornerShape(18.dp), placeholder = { Text("Название аниме…") },
                    leadingIcon = { Symbol("search", color = Muted) }, trailingIcon = {
                        if (state.query.isNotBlank()) IconButton(onClick = { vm.search(query = "") }) { Symbol("close", description = "Очистить поиск") }
                    }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Color.Transparent))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.width(12.dp))
                    listOf("popular" to "Популярное", "ongoing" to "Сейчас выходят", "movie" to "Фильмы", "anons" to "Анонсы").forEach { (id, label) ->
                        FilterChip(selected = state.filter == id, onClick = { vm.search(filter = id) }, label = { Text(label) }, shape = RoundedCornerShape(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                if (recent != null && state.query.isBlank()) {
                    Text("Продолжить просмотр", Modifier.padding(start = 22.dp, top = 22.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium)
                    AnimeRow(recent.anime, base, "Серия ${recent.episode} · ${timeLabel(recent.seconds)}", { vm.open(recent.anime) }, play = true)
                }
                Text(if (state.query.isBlank()) "Популярное" else "Результаты поиска", Modifier.padding(start = 22.dp, top = 24.dp), style = MaterialTheme.typography.titleLarge)
            }
        }
        if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) { Loading("Ищем аниме…") }
        itemsIndexed(state.items, key = { _, anime -> anime.id }) { index, anime ->
            // Horizontal padding is applied to the grid cell, keeping the header edge-to-edge.
            AnimeCard(anime, base, Modifier.padding(start = if (index % 2 == 0) 16.dp else 0.dp, end = if (index % 2 == 1) 16.dp else 0.dp)) { vm.open(anime) }
        }
        if (state.error != null) item(span = { GridItemSpan(maxLineSpan) }) {
            ErrorCard(state.error) { if (state.items.isEmpty()) vm.search() else vm.more() }
        }
        if (!state.loading && state.error == null && state.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState("Ничего не найдено", "Попробуйте оригинальное название или снимите фильтр.")
        }
        if (state.items.isNotEmpty() && state.hasMore && state.error == null) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = vm::more, enabled = !state.loadingMore) { Text(if (state.loadingMore) "Загружаем…" else "Показать ещё") }
            }
        }
    }
}

@Composable private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable private fun AnimeCard(anime: Anime, base: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(18.dp)).background(Panel)) {
            Poster(anime, base, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(62.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f)))))
            if (!anime.score.isNullOrBlank() && anime.score != "0.0") Surface(Modifier.padding(9.dp).align(Alignment.TopEnd), color = Night.copy(alpha = .86f), shape = RoundedCornerShape(8.dp)) {
                Text("★ ${anime.score}", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color(0xFFFFDA96), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (anime.status == "ongoing") Text("● ВЫХОДИТ", Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color(0xFFA2E1C9), fontSize = 10.sp, letterSpacing = .8.sp)
        }
        Text(anime.title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${anime.year} · ${anime.format}", Modifier.padding(top = 4.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun Poster(anime: Anime, base: String, modifier: Modifier) {
    val path = anime.image?.original ?: anime.image?.preview
    val url = path?.let { base.toHttpUrlOrNull()?.resolve(it)?.toString() }
    Box(modifier.background(Panel), contentAlignment = Alignment.Center) {
        Text(anime.title.take(1), color = Muted, fontSize = 30.sp)
        AsyncImage(model = url, contentDescription = "Постер: ${anime.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable private fun AnimeRow(anime: Anime, base: String, subtitle: String, onClick: () -> Unit, play: Boolean = false) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = Panel) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(anime, base, Modifier.width(54.dp).height(76.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(anime.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, Modifier.padding(top = 6.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Symbol(if (play) "play" else "chevron", color = Lavender)
        }
    }
}

@Composable private fun LibraryPage(library: List<LibraryEntry>, base: String, open: (Anime) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Shelf?>(null) }
    val filtered = library.filter { selected == null || it.shelf == selected }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageTitle("Мой список", "Статусы, серии и оценки") }
        item {
            Row(Modifier.horizontalScrollCompat().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected == null, { selected = null }, { Text("Все · ${library.size}") })
                Shelf.entries.forEach { shelf -> FilterChip(selected == shelf, { selected = shelf }, { Text(shelf.label) }) }
            }
        }
        if (filtered.isEmpty()) item { EmptyState("Здесь будут твои аниме", "Добавь аниме из каталога или войди в Shikimori в профиле.") }
        items(filtered, key = { it.anime.id }) { entry -> AnimeRow(entry.anime, base, "${entry.shelf.label} · ${entry.episodes} серий" + if (entry.score > 0) " · ★ ${entry.score}" else "", { open(entry.anime) }) }
    }
}

@Composable private fun HistoryPage(history: List<WatchProgress>, base: String, open: (Anime) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageTitle("История", "Вернись к моменту, на котором остановился") }
        if (history.isEmpty()) item { EmptyState("Пока ни одной истории", "Начни просмотр — здесь появятся аниме, серия и сохранённая позиция.") }
        items(history, key = { it.anime.id }) { progress ->
            AnimeRow(progress.anime, base, "Серия ${progress.episode} · ${timeLabel(progress.seconds)}\n${progress.source.translationLabel}", { open(progress.anime) }, play = true)
        }
    }
}

@Composable private fun SettingsPage(settings: AppSettings, vm: MainViewModel) {
    val context = LocalContext.current
    val user by vm.store.user.collectAsStateWithLifecycle()
    val sync by vm.account.state.collectAsStateWithLifecycle()
    val pending by vm.store.pending.collectAsStateWithLifecycle()
    val history by vm.store.history.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var shiki by remember(settings) { mutableStateOf(settings.shikiBase) }
    var endpoint by remember(settings) { mutableStateOf(settings.videoSearch) }
    var message by remember { mutableStateOf<String?>(null) }
    if (historyOpen) {
        BackHandler { historyOpen = false }
        Column { TextButton(onClick = { historyOpen = false }) { Text("← Профиль") }; HistoryPage(history, settings.shikiBase, vm::open) }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        PageTitle("Профиль", user?.nickname ?: "Shikimori")
        Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (user == null) {
                Text("Войдите, чтобы синхронизировать список, оценки и просмотренные серии.", color = Muted)
                Button(onClick = { context.startActivity(Intent(context, LoginActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("Войти в Shikimori") }
            } else {
                if (sync.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(when { sync.running -> "Синхронизация…"; pending.isNotEmpty() -> "Ожидают отправки: ${pending.size}"; sync.lastSync != null -> "Список синхронизирован"; else -> "Список сохранён на устройстве" }, color = Muted)
                sync.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (sync.needsLogin) Button(onClick = { context.startActivity(Intent(context, LoginActivity::class.java)) }) { Text("Войти снова") }
                OutlinedButton(onClick = vm.account::sync, enabled = !sync.running, modifier = Modifier.fillMaxWidth()) { Text("Синхронизировать") }
                TextButton(onClick = vm.account::logout) { Text("Выйти из аккаунта") }
            }
            HorizontalDivider(color = Panel)
            TextButton(onClick = { historyOpen = true }) { Symbol("clock"); Spacer(Modifier.width(12.dp)); Text("История просмотра · ${history.size}") }
            Text("Позиция внутри серии сохраняется на устройстве. Количество просмотренных серий — в Shikimori.", color = Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { clear = true }) { Text("Очистить историю") }
            HorizontalDivider(color = Panel)
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Скрыть настройки подключения" else "Настройки подключения") }
            if (advanced) {
                OutlinedTextField(shiki, { shiki = it }, Modifier.fillMaxWidth(), label = { Text("Каталог Shikimori") }, singleLine = true)
                OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("Поиск плеера") }, singleLine = true)
                Button(onClick = { try { vm.settings(SourceResolver.validatedSettings(shiki, endpoint)); message = "Сохранено" } catch (e: IllegalArgumentException) { message = e.message } }) { Text("Сохранить") }
                Text("Вход и синхронизация используют shikimori.io.", color = Muted, style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it) }
            }
            Text("ShikiMove ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Очистить историю?") }, text = { Text("Сохранённые позиции будут удалены. Список Shikimori сохранится.") }, confirmButton = { TextButton(onClick = { vm.store.clearHistory(); clear = false }) { Text("Очистить") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Отмена") } })
}

@Composable private fun PageTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 28.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, Modifier.padding(top = 8.dp), color = Muted)
    }
}
@Composable fun Loading(label: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 46.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = Muted)
    }
}
@Composable private fun ErrorCard(message: String, retry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(20.dp), color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp)) { Text(message, color = Muted); TextButton(onClick = retry) { Text("Попробовать снова") } }
    }
}
fun timeLabel(seconds: Double): String {
    val value = seconds.coerceAtLeast(0.0).toInt()
    return if (value >= 3600) "%d:%02d:%02d".format(value / 3600, value / 60 % 60, value % 60) else "%d:%02d".format(value / 60, value % 60)
}
