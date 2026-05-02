package com.example.radiolyric.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.radiolyric.data.local.StationEntity
import com.example.radiolyric.data.radio.Station
import com.example.radiolyric.presentation.NowPlayingViewModel
import com.example.radiolyric.presentation.StationPickerViewModel
import com.example.radiolyric.ui.components.InCarTouchTarget

@Composable
fun StationPickerScreen(
        modifier: Modifier = Modifier,
        nowPlayingViewModel: NowPlayingViewModel = hiltViewModel(),
        viewModel: StationPickerViewModel = hiltViewModel(),
) {
    val stations by viewModel.stations.collectAsStateWithLifecycle()

    LazyColumn(
            modifier = modifier.fillMaxSize().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Top,
    ) {
        items(stations, key = { it.sid }) { entity ->
            StationRow(
                    entity = entity,
                    onTap = {
                        nowPlayingViewModel.tune(entity.toStation())
                    },
                    onLongPress = { viewModel.togglePinned(entity) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun StationRow(entity: StationEntity, onTap: () -> Unit, onLongPress: () -> Unit) {
    InCarTouchTarget(modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
        Column {
            Spacer(Modifier.height(4.dp))
            Text(
                    text = entity.label + if (entity.pinned) " ★" else "",
                    style =
                            MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                            ),
                    color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                    text =
                            "SId 0x%04X · EId 0x%04X · %d kHz".format(
                                    entity.sid,
                                    entity.eid,
                                    entity.frequencyKhz,
                            ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
    // Long-press support omitted for MVP (Compose 1.6 needs combinedClickable). Tap-to-tune is
    // the primary interaction; pinning is a Phase 8.x follow-up.
    onLongPress.also { /* no-op suppressing unused warning */ }
}

private fun StationEntity.toStation(): Station =
        Station(sid = sid, eid = eid, frequencyKhz = frequencyKhz, label = label)
