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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.shikimove.app.*
import io.shikimove.app.data.*
import io.shikimove.app.player.PlayerActivity
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

@Composable private fun Poster(anime: Anime, base: String, modifier: Modifier) {
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

@Composable private fun DetailPage(state: DetailState, entry: LibraryEntry?, progress: WatchProgress?, settings: AppSettings, vm: MainViewModel) {
    val anime = state.anime
    val context = LocalContext.current
    var shelfDialog by remember { mutableStateOf(false) }
    var translations by remember { mutableStateOf(false) }
    var selectedSource by rememberSaveable(anime.id) { mutableStateOf<String?>(null) }
    var selectedSeason by rememberSaveable(anime.id) { mutableIntStateOf(progress?.season?.takeIf { it >= 0 } ?: 1) }
    var seasonMenu by remember { mutableStateOf(false) }
    var scoreMenu by remember { mutableStateOf(false) }
    var episode by rememberSaveable(anime.id) { mutableStateOf((progress?.episode ?: 1).toString()) }
    val sources = state.sources?.sources.orEmpty()
    val source = sources.find { it.id == selectedSource } ?: sources.find { it.translation?.id == progress?.source?.translation?.id } ?: sources.firstOrNull()
    LaunchedEffect(source?.id, selectedSeason) {
        if (source != null) {
            val keys = source.playlist
            if (keys.none { it.season == selectedSeason }) selectedSeason = keys.firstOrNull()?.season ?: 1
            if (EpisodeKey(selectedSeason, episode.toIntOrNull() ?: 1) !in keys) episode = keys.firstOrNull { it.season == selectedSeason }?.episode?.toString() ?: "1"
        }
    }
    val episodeNumber = episode.toIntOrNull()
    val validEpisode = source != null && episodeNumber != null && EpisodeKey(selectedSeason, episodeNumber) in source.playlist
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::close) { Symbol("back", description = "Назад") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { openBrowser(context, "${settings.shikiBase}/animes/${anime.id}") }) { Text("Shikimori"); Spacer(Modifier.width(8.dp)); Symbol("external", Modifier.size(18.dp), Lavender) }
        }
        Row(Modifier.padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Poster(anime, settings.shikiBase, Modifier.width(120.dp).height(176.dp).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(anime.statusLabel.uppercase(), color = Lavender, fontSize = 10.sp, letterSpacing = 1.sp)
                Text("${anime.year} · ${anime.format}", Modifier.padding(top = 14.dp), color = Muted)
                Text("★ ${anime.score ?: "—"}", Modifier.padding(top = 12.dp), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(if (anime.episodes > 0) "${anime.episodes} серий" else "Серий вышло: ${anime.episodesAired}", Modifier.padding(top = 10.dp), color = Muted)
            }
        }
        Text(anime.title, Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp), style = MaterialTheme.typography.headlineLarge)
        if (anime.title != anime.name) Text(anime.name, Modifier.padding(start = 22.dp, end = 22.dp, top = 7.dp), color = Muted)
        OutlinedButton(onClick = { shelfDialog = true }, Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), shape = RoundedCornerShape(14.dp)) {
            Symbol("bookmark", Modifier.size(18.dp), Lavender); Spacer(Modifier.width(10.dp)); Text(entry?.shelf?.label ?: "Добавить в мой список")
        }
        if (entry != null) Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Просмотрено: ${entry.episodes}", Modifier.weight(1f), color = Muted)
            Box { TextButton(onClick = { scoreMenu = true }) { Text(if (entry.score > 0) "★ ${entry.score} / 10" else "Оценить") }
                DropdownMenu(scoreMenu, { scoreMenu = false }) { (0..10).forEach { score -> DropdownMenuItem(text = { Text(if (score == 0) "Без оценки" else "$score / 10") }, onClick = { vm.store.setScore(anime, score); scoreMenu = false }) } }
            }
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Смотреть", style = MaterialTheme.typography.titleLarge)
                when {
                    state.sourcesLoading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Подбираем озвучки…", color = Muted) }
                    state.sourceError != null -> { Text(state.sourceError, color = Muted); TextButton(onClick = { vm.loadSources() }) { Text("Повторить поиск плеера") } }
                    source == null -> Text("Совпадающий источник не найден. Можно проверить наличие на amove.", color = Muted)
                    else -> {
                        if (state.sources?.exact == false) Text("Найдено по названию. Проверь, что это нужный сезон.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        Box {
                            OutlinedButton(onClick = { translations = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Text(source.translationLabel, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (source.translation?.type == "subtitles") "СУБ" else "ОЗВ", color = Muted, fontSize = 10.sp)
                            }
                            DropdownMenu(expanded = translations, onDismissRequest = { translations = false }) {
                                sources.forEach { candidate -> DropdownMenuItem(text = { Column {
                                    Text(candidate.translationLabel)
                                    Text("${candidate.episodeLimit} серий · ${if (candidate.translation?.type == "subtitles") "Субтитры" else "Озвучка"}", color = Muted, style = MaterialTheme.typography.bodySmall)
                                } }, onClick = { selectedSource = candidate.id; translations = false }) }
                            }
                        }
                        val seasons = source.playlist.map { it.season }.distinct()
                        if (seasons.size > 1) Box {
                            TextButton(onClick = { seasonMenu = true }) { Text(source.seasonLabel(selectedSeason)) }
                            DropdownMenu(seasonMenu, { seasonMenu = false }) { seasons.forEach { value -> DropdownMenuItem(text = { Text(source.seasonLabel(value)) }, onClick = { selectedSeason = value; seasonMenu = false }) } }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(episode, { if (it.length <= 5 && it.all(Char::isDigit)) episode = it },
                                Modifier.width(110.dp), label = { Text("Серия") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !validEpisode, shape = RoundedCornerShape(12.dp))
                            Text("Доступно: ${source.playlist.count { it.season == selectedSeason }}\n${source.quality.orEmpty()}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        val resume = progress?.takeIf { it.season == selectedSeason && it.episode == episodeNumber && it.source.translation?.id == source.translation?.id }
                        Button(onClick = {
                            PlayerActivity.launch(context, anime, source, episodeNumber ?: 1, resume?.seconds ?: 0.0, sources, selectedSeason)
                        }, enabled = validEpisode, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                            Symbol("play", Modifier.size(20.dp), MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp))
                            Text(if ((resume?.seconds ?: 0.0) > 5) "Продолжить с ${timeLabel(resume!!.seconds)}" else "Смотреть серию ${episodeNumber ?: "—"}")
                        }
                        if ((resume?.seconds ?: 0.0) > 5) TextButton(onClick = { PlayerActivity.launch(context, anime, source, episodeNumber ?: 1, 0.0, sources, selectedSeason) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Начать серию сначала") }
                    }
                }
                TextButton(onClick = { openBrowser(context, SourceResolver.amoveUrl(anime.id, source?.id.orEmpty())) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Открыть amove"); Spacer(Modifier.width(8.dp)); Symbol("external", Modifier.size(16.dp), Lavender)
                }
            }
        }
        FlowRow(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            anime.genres.orEmpty().forEach { genre ->
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(genre.russian ?: genre.name.orEmpty(), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Об аниме", Modifier.padding(horizontal = 22.dp), style = MaterialTheme.typography.titleLarge)
        if (state.loading) Loading("Загружаем описание…") else {
            val description = anime.description.orEmpty().replace(Regex("\\[/?[^]\\n]+]"), "").trim()
            Text(description.ifBlank { "Описания пока нет." }, Modifier.padding(horizontal = 22.dp, vertical = 14.dp), color = Muted, style = MaterialTheme.typography.bodyLarge)
            if (state.error != null) ErrorCard(state.error) { vm.open(anime) }
        }
    }
    if (shelfDialog) AlertDialog(onDismissRequest = { shelfDialog = false }, title = { Text("Мой список") }, text = {
        Column { Shelf.entries.forEach { shelf ->
            TextButton(onClick = { vm.store.setShelf(anime, shelf); shelfDialog = false }, Modifier.fillMaxWidth()) { Text(shelf.label, Modifier.fillMaxWidth()) }
        }
            if (entry != null) TextButton(onClick = { vm.store.setShelf(anime, null); shelfDialog = false }) { Text("Удалить из списка", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { shelfDialog = false }) { Text("Закрыть") } })
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
