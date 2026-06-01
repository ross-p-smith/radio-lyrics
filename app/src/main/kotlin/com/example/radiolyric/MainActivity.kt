package com.example.radiolyric

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.radiolyric.data.local.SettingsRepository
import com.example.radiolyric.devtools.AppLog as Log
import com.example.radiolyric.playback.PlaybackService
import com.example.radiolyric.ui.AppNavigation
import com.example.radiolyric.ui.theme.RadioLyricTheme
import com.example.radiolyric.usb.UsbPermissionGateway
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var usbPermissionGateway: UsbPermissionGateway
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var notificationPermissionRequested = false
    private var playbackServiceStartRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeStartPlaybackService()
        notificationPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
                    // Persist that we asked, regardless of grant/deny — Android only allows
                    // one programmatic prompt per install.
                    lifecycleScope.launch {
                        settingsRepository.setNotificationPermissionAsked(true)
                    }
                }
        setContent {
            RadioLyricTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                ) { AppNavigation() }
            }
        }
    }

    private fun maybeStartPlaybackService() {
        if (playbackServiceStartRequested) return
        playbackServiceStartRequested = true
        val intent = Intent(this, PlaybackService::class.java).apply {
            putExtra("trigger", "activity_launch")
        }
        Log.i(TAG, "Requesting PlaybackService start from MainActivity")
        runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(applicationContext, intent)
                    } else {
                        applicationContext.startService(intent)
                    }
                }
                .onSuccess { Log.i(TAG, "PlaybackService start request sent") }
                .onFailure { Log.w(TAG, "Could not start PlaybackService from MainActivity", it) }
    }

    override fun onResume() {
        super.onResume()
        // Register before snapshotting so that an immediately-granted permission broadcast
        // (or one that arrives during the request) is captured.
        usbPermissionGateway.register()
        usbPermissionGateway.snapshotAndRequest()
        maybeRequestNotificationPermission()
    }

    override fun onPause() {
        usbPermissionGateway.unregister()
        super.onPause()
    }

    /**
     * On Android 13 (API 33) and above, the foreground media notification is hidden
     * unless the user grants `POST_NOTIFICATIONS`. Verified live on Mekede DUDU7
     * (`dumpsys package com.example.radiolyric` showed `granted=false` and
     * `dumpsys notification` showed `importance=NONE`). Ask exactly once per install.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationPermissionRequested) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationPermissionRequested = true
        lifecycleScope.launch {
            if (settingsRepository.notificationPermissionAsked.first()) return@launch
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}


