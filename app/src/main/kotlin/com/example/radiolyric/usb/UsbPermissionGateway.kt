package com.example.radiolyric.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the Android USB permission handshake for the Mekede DAB+ dongle (VID 0x16C0 / PID 0x05DC).
 *
 * Lifecycle:
 * - Construct once (Hilt singleton).
 * - Call [register] in `Activity.onResume` (or `Service.onCreate`).
 * - Call [unregister] in the matching tear-down callback.
 * - Call [snapshotAndRequest] in `onResume` to handle the cold-start case where the dongle was
 *   already attached when the activity launched.
 *
 * On Android 12+ (API 31+), `PendingIntent` must be `FLAG_IMMUTABLE`. On Android 13+ (API 33+)
 * the broadcast receiver must declare an export flag (`RECEIVER_NOT_EXPORTED` since the action
 * is internal-only).
 */
@Singleton
class UsbPermissionGateway
@Inject
constructor(
        @ApplicationContext private val ctx: Context,
) {

    private val usbManager: UsbManager =
            ctx.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _grantedDevice = MutableStateFlow<UsbDevice?>(null)

    /** Emits the most-recently-granted Mekede USB device, or `null` if none has been granted. */
    val grantedDevice: StateFlow<UsbDevice?> = _grantedDevice.asStateFlow()

    private val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    val device: UsbDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                        UsbManager.EXTRA_DEVICE,
                                        UsbDevice::class.java,
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                    val granted =
                            intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null && granted && device.isMekedeDongle()) {
                        Log.i(TAG, "USB permission granted for ${device.deviceName}")
                        _grantedDevice.value = device
                    } else {
                        Log.w(
                                TAG,
                                "USB permission denied or non-target device: device=${device?.deviceName} granted=$granted",
                        )
                    }
                }
            }

    private var registered = false

    /** Registers the permission-result receiver. Idempotent. */
    fun register() {
        if (registered) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                    ctx,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") ctx.registerReceiver(receiver, filter)
        }
        registered = true
    }

    /** Unregisters the permission-result receiver. Idempotent. */
    fun unregister() {
        if (!registered) return
        runCatching { ctx.unregisterReceiver(receiver) }
        registered = false
    }

    /**
     * Returns all currently-attached Mekede dongles. Use this in `onResume` to handle the
     * cold-boot case where `USB_DEVICE_ATTACHED` fires before the activity is created.
     */
    fun snapshot(): List<UsbDevice> =
            usbManager.deviceList.values.filter { it.isMekedeDongle() }

    /**
     * Convenience: snapshot, then either emit the granted device immediately if permission is
     * already held, or request permission for the first matching device. No-op if no Mekede
     * dongle is attached.
     */
    fun snapshotAndRequest() {
        val devices = snapshot()
        if (devices.isEmpty()) {
            Log.d(TAG, "No Mekede dongle attached at snapshot.")
            return
        }
        val device = devices.first()
        if (usbManager.hasPermission(device)) {
            _grantedDevice.value = device
        } else {
            requestPermission(device)
        }
    }

    /** Triggers the system permission dialog for a specific device. */
    fun requestPermission(device: UsbDevice) {
        val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
        val pi =
                PendingIntent.getBroadcast(
                        ctx,
                        REQUEST_CODE,
                        Intent(ACTION_USB_PERMISSION).setPackage(ctx.packageName),
                        flags,
                )
        usbManager.requestPermission(device, pi)
    }

    private fun UsbDevice.isMekedeDongle(): Boolean =
            vendorId == MEKEDE_VID && productId == MEKEDE_PID

    private companion object {
        private const val TAG = "UsbPermissionGateway"
        private const val ACTION_USB_PERMISSION =
                "com.example.radiolyric.usb.ACTION_USB_PERMISSION"
        private const val REQUEST_CODE = 0x10C0
        private const val MEKEDE_VID = 0x16C0
        private const val MEKEDE_PID = 0x05DC
    }
}
