package com.example.radiolyric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.radiolyric.data.lyrics.LyricLine
import com.example.radiolyric.presentation.LyricsUiState
import com.example.radiolyric.presentation.LyricsViewModel

@Composable
fun LyricsScreen(modifier: Modifier = Modifier, viewModel: LyricsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier = modifier.fillMaxSize()) {
        when (val s = state) {
            LyricsUiState.Idle -> CenteredMessage("Waiting for now-playing metadata…")
            LyricsUiState.Loading -> CenteredMessage("Looking up lyrics…")
            LyricsUiState.None -> CenteredMessage("No lyrics available.")
            is LyricsUiState.Plain -> PlainLyrics(s.text)
            is LyricsUiState.Synced -> SyncedLyrics(s.lyrics.lines, viewModel)
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
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
    val positionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val active = activeLineIndex(lines, positionMs)
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

/** Binary-search the largest `i` such that `lines[i].timeMs <= positionMs`. */
private fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return 0
    var lo = 0
    var hi = lines.size - 1
    var ans = 0
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return ans
}
