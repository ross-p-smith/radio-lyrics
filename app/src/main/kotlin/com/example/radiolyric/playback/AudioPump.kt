package com.example.radiolyric.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.radiolyric.devtools.AppLog as Log

/**
 * Thin wrapper around [AudioTrack] for streaming S16_LE stereo PCM @ 48 kHz.
 *
 * - Constructed lazily on first [write].
 * - [write] is a blocking call (`MODE_STREAM`); on the dab-usb-reader dispatcher this is fine.
 * - [release] tears down the underlying track and is idempotent.
 *
 * Sample rate / channel mask intentionally hard-coded to match `RadioServiceAudiodataListener`'s
 * common DAB+ output (HE-AAC v2 → 48 kHz stereo). If a tuner negotiates a different rate, the
 * AudioTrack is rebuilt automatically (see [maybeReconfigure]).
 */
class AudioPump {

    private var track: AudioTrack? = null
    private var sampleRate: Int = 0
    private var channels: Int = 0

    /**
     * Synchronously writes [pcm] into the underlying [AudioTrack], rebuilding the track on the
     * fly if the stream format changes mid-session.
     */
    fun write(pcm: ByteArray, channelCount: Int = 2, sampleRateHz: Int = 48_000) {
        if (pcm.isEmpty()) return
        maybeReconfigure(channelCount, sampleRateHz)
        val t = track ?: return
        try {
            t.write(pcm, 0, pcm.size)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack.write failed; releasing", e)
            release()
        }
    }

    fun pause() {
        runCatching { track?.pause() }
    }

    fun resume() {
        runCatching { track?.play() }
    }

    fun release() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        sampleRate = 0
        channels = 0
    }

    private fun maybeReconfigure(newChannels: Int, newRate: Int) {
        if (track != null && newRate == sampleRate && newChannels == channels) return
        release()
        val channelMask =
                if (newChannels >= 2) AudioFormat.CHANNEL_OUT_STEREO
                else AudioFormat.CHANNEL_OUT_MONO
        val minBuf =
                AudioTrack.getMinBufferSize(newRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid AudioTrack params: rate=$newRate ch=$newChannels")
            return
        }
        val bufSize = minBuf * 4
        val attrs =
                AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
        val format =
                AudioFormat.Builder()
                        .setSampleRate(newRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
        val newTrack =
                AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
        @Suppress("DEPRECATION")
        if (newTrack.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack failed to initialise (state=${newTrack.state})")
            newTrack.release()
            return
        }
        newTrack.play()
        track = newTrack
        sampleRate = newRate
        channels = newChannels
        Log.i(TAG, "AudioTrack ready: rate=$newRate ch=$newChannels bufSize=$bufSize")
    }

    private companion object {
        private const val TAG = "AudioPump"
        // Suppress lint about unused AudioManager import.
        @Suppress("unused") private val DUMMY = AudioManager.STREAM_MUSIC
    }
}
