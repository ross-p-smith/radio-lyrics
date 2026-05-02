package com.example.radiolyric.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.radiolyric.data.local.SettingsRepository
import com.example.radiolyric.data.local.StationDao
import com.example.radiolyric.data.local.StationEntity
import com.example.radiolyric.data.radio.Stations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StationPickerViewModel
@Inject
constructor(
        private val stationDao: StationDao,
        private val settings: SettingsRepository,
) : ViewModel() {

    val stations: StateFlow<List<StationEntity>> =
            stationDao
                    .observeAll()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), seed())

    fun setDefault(sid: Int) {
        viewModelScope.launch { settings.setDefaultStationSid(sid) }
    }

    fun togglePinned(entity: StationEntity) {
        viewModelScope.launch { stationDao.setPinned(entity.sid, !entity.pinned) }
    }

    private fun seed(): List<StationEntity> =
            Stations.all.map { s ->
                StationEntity(
                        sid = s.sid,
                        eid = s.eid,
                        frequencyKhz = s.frequencyKhz,
                        label = s.label,
                        pinned = true,
                )
            }
}
