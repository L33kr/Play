@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.shikimove.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.shikimove.app.DetailState
import io.shikimove.app.MainViewModel
import io.shikimove.app.data.*
import io.shikimove.app.player.PlayerActivity

@Composable internal fun DetailPage(state: DetailState, entry: LibraryEntry?, progress: WatchProgress?, settings: AppSettings, vm: MainViewModel) {
    val context = LocalContext.current
    var listOpen by remember(state.anime.id) { mutableStateOf(false) }
    val sources = state.sources?.sources.orEmpty()
    val choice = remember(sources, progress, entry?.episodes) { PlaybackChoice.choose(sources, progress, entry?.episodes ?: 0) }
    AnimeDetailContent(state, entry, settings.shikiBase, vm::close, { listOpen = true }, {
        if (choice == null) vm.loadSources()
        else PlayerActivity.launch(context, state.anime, choice.source, choice.episode.episode,
            choice.seconds, sources, choice.episode.season)
    })
    if (listOpen) AnimeListSheet(state.anime, entry, { listOpen = false },
        { vm.store.setShelf(state.anime, it) }, { vm.store.setScore(state.anime, it) })
}

@Composable internal fun AnimeDetailContent(state: DetailState, entry: LibraryEntry?, base: String,
    onBack: () -> Unit, onList: () -> Unit, onWatch: () -> Unit) {
    val anime = state.anime
    val ready = state.sources?.sources.orEmpty().any { it.playlist.isNotEmpty() }
    Column(Modifier.fillMaxSize().background(Night)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Panel, CircleShape)) {
                Symbol("back", description = "Назад")
            }
            Spacer(Modifier.weight(1f))
            Text("Об аниме", color = Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onList, modifier = Modifier.background(if (entry == null) Panel else Lavender.copy(alpha = .13f), CircleShape)) {
                Symbol("bookmark", color = if (entry == null) Muted else Lavender,
                    description = entry?.let { "Мой список: ${it.shelf.label}. Изменить статус и оценку" } ?: "Добавить в мой список")
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 16.dp, bottom = 28.dp)) {
            Box(Modifier.fillMaxWidth().drawBehind {
                drawRect(Brush.radialGradient(listOf(Lavender.copy(alpha = .1f), Color.Transparent),
                    center = Offset(size.width * .2f, size.height * .45f), radius = size.height))
            }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(anime, base, Modifier.width(120.dp).height(176.dp)
                        .shadow(16.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (anime.statusLabel.isNotBlank()) Surface(color = Lavender.copy(alpha = .1f), shape = RoundedCornerShape(8.dp)) {
                            Text(anime.statusLabel, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Lavender, fontSize = 12.sp)
                        }
                        Text("${anime.year} · ${anime.format}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if (anime.score?.toDoubleOrNull()?.let { it > 0 } == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("★", color = Color(0xFFEAD39E), fontSize = 22.sp)
                            Text(anime.score, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-.5).sp)
                        }
                        if (anime.kind != "movie") {
                            val count = anime.episodes.takeIf { it > 0 } ?: anime.episodesAired
                            if (count > 0) Text(episodeCountLabel(count), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(anime.title, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-.6).sp, fontWeight = FontWeight.Bold)
            if (anime.title != anime.name && anime.name.isNotBlank()) Text(anime.name, Modifier.padding(top = 7.dp), color = Muted,
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(22.dp))
            Button(onClick = onWatch, enabled = !state.sourcesLoading, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(disabledContainerColor = Lavender.copy(alpha = .35f), disabledContentColor = Color.White.copy(alpha = .7f))) {
                if (state.sourcesLoading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = .7f))
                else Symbol("play", Modifier.size(20.dp), MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Смотреть", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (!state.sourcesLoading && !ready) {
                Text(state.sourceError ?: "Видео пока не найдено. Нажми «Смотреть», чтобы проверить ещё раз.",
                    Modifier.padding(top = 10.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
            } else if (state.sources?.exact == false) {
                Text("Источник найден по названию — проверь, что это нужный сезон.", Modifier.padding(top = 10.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            val genres = anime.genres.orEmpty().mapNotNull { it.russian ?: it.name }.filter(String::isNotBlank)
            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { genre -> Surface(color = Panel, shape = RoundedCornerShape(10.dp)) {
                        Text(genre, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Muted, fontSize = 12.sp)
                    } }
                }
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = .06f))
            Spacer(Modifier.height(22.dp))
            Text("Описание", style = MaterialTheme.typography.titleMedium)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 18.dp), color = Lavender, trackColor = Panel)
            else {
                val description = anime.description.orEmpty().replace(Regex("\\[/?[^]\\n]+]"), "").trim()
                Text(description.ifBlank { state.error ?: "Описания пока нет." }, Modifier.padding(top = 10.dp), color = Muted,
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable private fun AnimeListSheet(anime: Anime, entry: LibraryEntry?, dismiss: () -> Unit,
    setShelf: (Shelf?) -> Unit, setScore: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Мой список", style = MaterialTheme.typography.titleLarge)
            Text(anime.title, color = Muted, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Shelf.entries.forEach { shelf -> FilterChip(selected = entry?.shelf == shelf, onClick = { setShelf(shelf) }, label = { Text(shelf.label) }) }
            }
            if (entry != null) {
                Text("Просмотрено: ${entry.episodes}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Моя оценка", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..10).forEach { score -> FilterChip(selected = entry.score == score, onClick = { setScore(if (entry.score == score) 0 else score) }, label = { Text("$score") }) }
                }
                TextButton(onClick = { setShelf(null); dismiss() }) { Text("Удалить из списка", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun episodeCountLabel(count: Int): String {
    val noun = when { count % 100 in 11..14 -> "серий"; count % 10 == 1 -> "серия"; count % 10 in 2..4 -> "серии"; else -> "серий" }
    return "$count $noun"
}
