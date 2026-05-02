package com.example.radiolyric

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.radiolyric.ui.AppNavigation
import com.example.radiolyric.ui.theme.RadioLyricTheme
import com.example.radiolyric.usb.UsbPermissionGateway
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var usbPermissionGateway: UsbPermissionGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RadioLyricTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                ) { AppNavigation() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register before snapshotting so that an immediately-granted permission broadcast
        // (or one that arrives during the request) is captured.
        usbPermissionGateway.register()
        usbPermissionGateway.snapshotAndRequest()
    }

    override fun onPause() {
        usbPermissionGateway.unregister()
        super.onPause()
    }
}


