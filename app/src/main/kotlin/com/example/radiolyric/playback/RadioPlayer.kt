package com.example.radiolyric.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi

/**
 * A minimal Media3 [Player] that participates in a [androidx.media3.session.MediaSession] purely
 * to surface metadata + transport state. Audio is produced out-of-band by [AudioPump]; this
 * player NEVER decodes media and has no notion of position or duration.
 */
@UnstableApi
class RadioPlayer(
        looper: Looper = Looper.getMainLooper(),
        private val onPlayWhenReadyChange: (Boolean) -> Unit = {},
) : SimpleBasePlayer(looper) {

    private var item: MediaItem? = null
    private var playWhenReady: Boolean = false

    override fun getState(): State {
        val playlist =
                item?.let {
                    listOf(
                            MediaItemData.Builder("radio")
                                    .setMediaItem(it)
                                    .setMediaMetadata(it.mediaMetadata)
                                    .build(),
                    )
                }
                        ?: emptyList()
        return State.Builder()
                .setAvailableCommands(
                        Player.Commands.Builder()
                                .addAll(
                                        Player.COMMAND_PLAY_PAUSE,
                                        Player.COMMAND_PREPARE,
                                        Player.COMMAND_STOP,
                                        Player.COMMAND_GET_METADATA,
                                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                                        Player.COMMAND_GET_TIMELINE,
                                        Player.COMMAND_SET_MEDIA_ITEM,
                                )
                                .build(),
                )
                .setPlaylist(playlist)
                .setPlaybackState(if (item != null) Player.STATE_READY else Player.STATE_IDLE)
                .setPlayWhenReady(
                        playWhenReady,
                        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                )
                .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean) =
            com.google.common.util.concurrent.Futures.immediateVoidFuture().also {
                this.playWhenReady = playWhenReady
                onPlayWhenReadyChange(playWhenReady)
                invalidateState()
            }

    override fun handleSetMediaItems(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
    ) =
            com.google.common.util.concurrent.Futures.immediateVoidFuture().also {
                item = mediaItems.firstOrNull()
                invalidateState()
            }

    override fun handleStop() =
            com.google.common.util.concurrent.Futures.immediateVoidFuture().also {
                playWhenReady = false
                onPlayWhenReadyChange(false)
                invalidateState()
            }

    /** Pushes a metadata-only [MediaItem] (no URI) so the lock-screen renders artist + title. */
    fun setNowPlaying(artist: String?, title: String?, source: String?) {
        val meta =
                MediaMetadata.Builder()
                        .setArtist(artist.orEmpty())
                        .setTitle(title.orEmpty())
                        .setAlbumTitle(source.orEmpty())
                        .setIsPlayable(true)
                        .build()
        item = MediaItem.Builder().setMediaId("radio").setMediaMetadata(meta).build()
        invalidateState()
    }
}
