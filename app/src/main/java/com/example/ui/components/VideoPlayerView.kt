package com.example.ui.components

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    uri: String,
    isPlaying: Boolean,
    onPlayingChanged: (Boolean) -> Unit,
    playbackSpeed: Float,
    lastPlayedPosition: Long,
    onProgressUpdate: (Long) -> Unit,
    onBrightnessGesture: (Float) -> Unit, // drag delta -1f..1f
    onVolumeGesture: (Float) -> Unit,     // drag delta -1f..1f
    onSeekGesture: (Long) -> Unit,        // seek delta ms
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnProgressUpdate = rememberUpdatedState(onProgressUpdate)

    // Instantiate ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Handle play / pause, seek, speed changes
    LaunchedEffect(uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (lastPlayedPosition > 0L) {
            exoPlayer.seekTo(lastPlayedPosition)
        }
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Monitor playback progress to sync subtitles
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (exoPlayer.isPlaying) {
                currentOnProgressUpdate.value(exoPlayer.currentPosition)
            }
            delay(200) // sync interval
        }
    }

    // Sync state back on media controller events
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlayingChanged(playing)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // AndroidView embedding of Media3 PlayerView with custom touch gesture handling
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Compose pointer input for swipe custom gestures on top of native controller
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val width = size.width
                            val halfWidth = width / 2
                            if (offset.x < halfWidth) {
                                // Double tap left: Rewind 10s
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                onSeekGesture(-10000L)
                            } else {
                                // Double tap right: Fast Forward 10s
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(newPos)
                                onSeekGesture(10000L)
                            }
                        },
                        onTap = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    )
                }
        )
    }
}
