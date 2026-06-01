package com.example.radiolyric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.radiolyric.data.lyrics.LyricLine
import com.example.radiolyric.presentation.LyricsUiState
import com.example.radiolyric.presentation.LyricsViewModel
import com.example.radiolyric.presentation.SongHeading

@Composable
fun LyricsScreen(modifier: Modifier = Modifier, viewModel: LyricsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val heading by viewModel.songHeading.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        heading?.let { SongHeader(it) }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (val s = state) {
                LyricsUiState.Idle -> CenteredMessage("Waiting for now-playing metadata…")
                LyricsUiState.Loading -> CenteredMessage("Looking up lyrics…")
                LyricsUiState.None -> CenteredMessage("No lyrics available.")
                is LyricsUiState.Plain -> PlainLyrics(s.text)
                is LyricsUiState.Synced -> SyncedLyrics(s.lyrics.lines, viewModel)
            }
        }
    }
}

@Composable
private fun SongHeader(heading: SongHeading) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
                text = heading.title,
                style =
                        MaterialTheme.typography.displaySmall.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 34.sp,
                        ),
                color = MaterialTheme.colorScheme.onBackground,
        )
        heading.artist?.let { artist ->
            Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
    ) {
        Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, viewModel: LyricsViewModel) {
    val active by viewModel.activeLineIndex.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(active) {
        val target = (active - 2).coerceAtLeast(0)
        if (lines.isNotEmpty()) listState.animateScrollToItem(target.coerceAtMost(lines.lastIndex))
    }

    LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lines.size) { i ->
            val line = lines[i]
            val isActive = i == active
            Text(
                    text = if (line.text.isBlank()) "♪" else line.text,
                    style =
                            if (isActive) {
                                MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Bold,
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                            },
                    color =
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
