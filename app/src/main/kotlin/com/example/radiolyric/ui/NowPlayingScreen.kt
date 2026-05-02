package com.example.radiolyric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.radiolyric.presentation.NowPlayingViewModel

@Composable
fun NowPlayingScreen(
        modifier: Modifier = Modifier,
        onOpenLyrics: () -> Unit,
        onOpenStations: () -> Unit,
        viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
    ) {
        Text(
                text = state.stationLabel,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
        Text(
                text = state.title ?: "—",
                style =
                        MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                        ),
                color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
                text = state.artist ?: "—",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                    onClick = { viewModel.play() },
                    modifier = Modifier.height(56.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                Spacer(Modifier.size(8.dp))
                Text("Play")
            }
            Button(onClick = onOpenStations, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Stations")
                Spacer(Modifier.size(8.dp))
                Text("Stations")
            }
            Button(onClick = onOpenLyrics, modifier = Modifier.height(56.dp)) { Text("Lyrics") }
        }
    }
}
