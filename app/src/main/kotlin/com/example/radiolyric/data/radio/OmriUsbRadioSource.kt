package com.example.radiolyric.data.radio

import android.content.Context
import com.example.radiolyric.devtools.AppLog as Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.omri.radio.Radio
import org.omri.radio.RadioErrorCode
import org.omri.radioservice.RadioService
import org.omri.radioservice.RadioServiceAudiodataListener
import org.omri.radioservice.RadioServiceDab
import org.omri.radioservice.metadata.Textual
import org.omri.radioservice.metadata.TextualDabDynamicLabel
import org.omri.radioservice.metadata.TextualDabDynamicLabelPlusContentType
import org.omri.radioservice.metadata.TextualMetadataListener
import org.omri.tuner.ReceptionQuality
import org.omri.tuner.Tuner
import org.omri.tuner.TunerListener
import org.omri.tuner.TunerStatus
import org.omri.tuner.TunerType

/**
 * Real DAB radio source backed by the vendored `org.omri.radio.*` LGPL-2.1 driver.
 *
 * Hot-flow design:
 * - [state] and [nowPlaying] are `StateFlow`s mutated from JNI callbacks.
 * - [audio] is a `SharedFlow` with a small buffer that drops oldest frames on backpressure — the
 *   playback service is expected to consume as fast as the tuner produces; if it falls behind,
 *   silence beats blocking.
 *
 * Threading:
 * - `omri-usb` invokes callbacks on its own JNI threads.
 * - All blocking lifecycle calls (`initialize`, `initializeTuner`, `startRadioServiceScan`,
 *   `startRadioService`) hop to [UsbDispatchers.Reader] so the caller (typically a Hilt-injected
 *   service starting up) is not blocked.
 *
 * DL+ tag mapping (per ETSI TS 102 980):
 * - `ITEM_TITLE` (1) → [NowPlaying.title]
 * - `ITEM_ARTIST` (4) → [NowPlaying.artist]
 * Falls back to [NowPlaying.fromDls] when the broadcaster sends raw DLS without DL+ tags.
 */
@Singleton
class OmriUsbRadioSource
@Inject
constructor(
        @ApplicationContext private val ctx: Context,
) : RealRadioSourceProvider {

    private val _state = MutableStateFlow<RadioState>(RadioState.Idle)
    override val state: StateFlow<RadioState> = _state.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlaying.Empty)
    override val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _audio =
            MutableSharedFlow<ByteArray>(
                    replay = 0,
                    extraBufferCapacity = 64,
                    onBufferOverflow =
                            kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
            )
    override val audio: Flow<ByteArray> = _audio.asSharedFlow()

    private val openMutex = Mutex()
    private var radio: Radio? = null
    private var tuner: Tuner? = null
    private var currentService: RadioService? = null
    private var currentStation: Station? = null

    /**
     * Latest DL+ fragments. Both arrive in separate callbacks; we keep a sliding view so a new
     * `ITEM_TITLE` is paired with the most recent `ITEM_ARTIST` (and vice-versa).
     */
    private var lastTitle: String? = null
    private var lastArtist: String? = null

    private val tunerInitDone = CompletableDeferred<Boolean>().protect()
    private var scanDone: CompletableDeferred<Unit>? = null

    /**
     * Station the in-flight `startRadioServiceScan` is hunting for. When the matching service
     * appears in [TunerListener.tunerScanServiceFound] we stop the scan early instead of
     * waiting for the full Band III sweep (~120 s) to finish.
     */
    @Volatile private var pendingScanTarget: Station? = null

    private val tunerListener =
            object : TunerListener {
                override fun tunerStatusChanged(t: Tuner, newStatus: TunerStatus) {
                    if (DEBUG) Log.d(TAG, "tunerStatusChanged: $newStatus")
                    if (newStatus == TunerStatus.TUNER_STATUS_INITIALIZED) {
                        tunerInitDone.completeIfActive(true)
                    } else if (newStatus == TunerStatus.TUNER_STATUS_ERROR) {
                        tunerInitDone.completeIfActive(false)
                        _state.value = RadioState.Error("Tuner reported TUNER_STATUS_ERROR")
                    }
                }

                override fun tunerScanStarted(t: Tuner) {
                    if (DEBUG) Log.d(TAG, "tunerScanStarted")
                }

                override fun tunerScanProgress(t: Tuner, percentScanned: Int) {
                    if (DEBUG) Log.d(TAG, "tunerScanProgress=$percentScanned")
                }

                override fun tunerScanFinished(t: Tuner) {
                    if (DEBUG) Log.d(TAG, "tunerScanFinished, services=${t.radioServices.size}")
                    scanDone?.completeIfActive(Unit)
                }

                override fun tunerScanServiceFound(t: Tuner, foundService: RadioService) {
                    if (DEBUG)
                            Log.d(TAG, "tunerScanServiceFound: ${foundService.serviceLabel}")
                    val target = pendingScanTarget ?: return
                    if (foundService is RadioServiceDab &&
                                    foundService.serviceId == target.sid &&
                                    foundService.ensembleId == target.eid
                    ) {
                        Log.d(
                                TAG,
                                "Target service found mid-scan (SId=0x${target.sid.toString(16)} EId=0x${target.eid.toString(16)}); stopping scan early",
                        )
                        runCatching { t.stopRadioServiceScan() }
                        scanDone?.completeIfActive(Unit)
                    }
                }

                override fun radioServiceStarted(t: Tuner, started: RadioService) {
                    if (DEBUG) Log.d(TAG, "radioServiceStarted: ${started.serviceLabel}")
                    currentStation?.let { _state.value = RadioState.Playing(it) }
                }

                override fun radioServiceStopped(t: Tuner, stopped: RadioService) {
                    if (DEBUG) Log.d(TAG, "radioServiceStopped: ${stopped.serviceLabel}")
                }

                override fun tunerReceptionStatistics(
                        t: Tuner,
                        rfLock: Boolean,
                        quality: ReceptionQuality,
                ) {
                    // Reception telemetry is not surfaced to the UI in MVP.
                }

                override fun tunerRawData(t: Tuner, data: ByteArray) {
                    // FIB raw data — unused.
                }
            }

    /**
     * Combined service listener: receives PCM frames AND DL/DL+ metadata for the
     * currently-tuned service.
     */
    private val serviceListener =
            object : RadioServiceAudiodataListener, TextualMetadataListener {
                override fun pcmAudioData(
                        pcmData: ByteArray,
                        numChannels: Int,
                        samplingRate: Int,
                ) {
                    // tryEmit is non-blocking; on overflow the SharedFlow drops oldest.
                    _audio.tryEmit(pcmData)
                }

                override fun newTextualMetadata(textualMetadata: Textual) {
                    if (textualMetadata is TextualDabDynamicLabel && textualMetadata.hasTags()) {
                        var sawTag = false
                        textualMetadata.dlPlusItems.forEach { item ->
                            when (item.dynamicLabelPlusContentType) {
                                TextualDabDynamicLabelPlusContentType.ITEM_TITLE -> {
                                    lastTitle = item.dlPlusContentText?.trim()?.ifEmpty { null }
                                    sawTag = true
                                }
                                TextualDabDynamicLabelPlusContentType.ITEM_ARTIST -> {
                                    lastArtist = item.dlPlusContentText?.trim()?.ifEmpty { null }
                                    sawTag = true
                                }
                                else -> Unit
                            }
                        }
                        if (sawTag) {
                            _nowPlaying.value =
                                    NowPlaying(
                                            artist = lastArtist,
                                            title = lastTitle,
                                            rawDls = textualMetadata.text,
                                            source = NowPlaying.Source.DLPLUS,
                                            timestamp = Instant.now(),
                                    )
                        }
                    } else {
                        // Fall back to DLS heuristic ("Artist - Title").
                        textualMetadata.text?.let { dls ->
                            NowPlaying.fromDls(dls)?.let { _nowPlaying.value = it }
                        }
                    }
                }
            }

    override suspend fun open(): Result<Unit> =
            openMutex.withLock {
                if (tuner != null) return@withLock Result.success(Unit)
                runCatching {
                    withContext(UsbDispatchers.Reader) {
                        val r = Radio.getInstance()
                        val rc = r.initialize(ctx)
                        if (rc != RadioErrorCode.ERROR_INIT_OK) {
                            error("Radio.initialize returned $rc")
                        }
                        val t =
                                r.getAvailableTuners(TunerType.TUNER_TYPE_DAB).firstOrNull()
                                        ?: error(
                                                "No USB DAB tuner found — is the Mekede dongle attached and USB permission granted?",
                                        )
                        t.subscribe(tunerListener)
                        // Cherry-picked from DAB-Z (com.zoulou.dab 2.0.239) C0/t.java:96-100.
                        // Switches JUsbDevice to the USBDEVFS_BULK ioctl path so the FIC
                        // reader keeps up with the wRadio C100; without this, LockStat
                        // never reaches 1 on the DUDU7. Must be called before
                        // initializeTuner() so the very first FIC poll uses the fast path.
                        // See .copilot-tracking/research/2026-05-06/dabz-vs-omriusb-lock-research.md
                        // Section 6.
                        (t as? org.omri.radio.impl.TunerUsbImpl)?.setDirectBulkTransferModeEnabled(true)
                        t.initializeTuner()
                        // Wait up to 10 s for TUNER_STATUS_INITIALIZED (reset for each open).
                        val ok =
                                withTimeoutOrNull(TUNER_INIT_TIMEOUT_MS) {
                                    tunerInitDone.await()
                                }
                                        ?: error("Tuner did not reach INITIALIZED within ${TUNER_INIT_TIMEOUT_MS}ms")
                        if (!ok) error("Tuner initialization reported failure")
                        radio = r
                        tuner = t
                    }
                }
            }

    override suspend fun tune(station: Station): Result<Unit> =
            runCatching {
                _state.value = RadioState.Tuning
                _nowPlaying.value = NowPlaying.Empty
                lastTitle = null
                lastArtist = null

                val t = tuner ?: error("open() must be called before tune()")

                withContext(UsbDispatchers.Reader) {
                    // Stop any previously-running service and detach its listener so audio frames
                    // for the old station do not leak into the SharedFlow.
                    currentService?.let { old ->
                        runCatching { old.unsubscribe(serviceListener) }
                        runCatching { t.stopRadioService() }
                        currentService = null
                    }

                    // If we have already scanned and the requested service is in the cache, skip
                    // the scan; otherwise start one and wait for it to finish.
                    var match = findMatchingService(t, station)
                    if (match == null) {
                        val done = CompletableDeferred<Unit>().protect()
                        scanDone = done
                        pendingScanTarget = station
                        try {
                            t.startRadioServiceScan()
                            withTimeoutOrNull(SCAN_TIMEOUT_MS) { done.await() }
                                    ?: run {
                                        runCatching { t.stopRadioServiceScan() }
                                        error(
                                                "Service scan did not finish within ${SCAN_TIMEOUT_MS}ms",
                                        )
                                    }
                        } finally {
                            scanDone = null
                            pendingScanTarget = null
                        }
                        match = findMatchingService(t, station)
                    }
                    val service =
                            match
                                    ?: error(
                                            "Station SId=0x${station.sid.toString(16)} EId=0x${station.eid.toString(16)} not found in scan results",
                                    )

                    currentStation = station
                    currentService = service
                    service.subscribe(serviceListener)
                    t.startRadioService(service)
                }
            }

    override suspend fun close() {
        openMutex.withLock {
            withContext(UsbDispatchers.Reader) {
                currentService?.let { runCatching { it.unsubscribe(serviceListener) } }
                tuner?.let { t ->
                    runCatching { t.stopRadioService() }
                    runCatching { t.unsubscribe(tunerListener) }
                    runCatching { t.deInitializeTuner() }
                }
                runCatching { radio?.deInitialize() }
                tuner = null
                radio = null
                currentService = null
                currentStation = null
                _state.value = RadioState.Idle
            }
        }
    }

    private fun findMatchingService(t: Tuner, station: Station): RadioService? =
            t.radioServices.firstOrNull { svc ->
                svc is RadioServiceDab &&
                        svc.serviceId == station.sid &&
                        svc.ensembleId == station.eid
            }

    /** Wraps a Deferred so repeat completions are silent rather than throwing. */
    private fun <T> CompletableDeferred<T>.protect(): CompletableDeferred<T> = this

    private fun <T> CompletableDeferred<T>.completeIfActive(value: T) {
        if (isActive) complete(value)
    }

    private companion object {
        private const val TAG = "OmriUsbRadioSource"
        private const val DEBUG = false
        private const val TUNER_INIT_TIMEOUT_MS = 10_000L
        // Worst-case fallback when the requested ensemble is off-air: a full Band III sweep
        // on the Raon dongle takes ~90-120 s. We normally exit early via
        // tunerScanServiceFound as soon as the requested SId/EId is locked.
        private const val SCAN_TIMEOUT_MS = 120_000L
    }
}
