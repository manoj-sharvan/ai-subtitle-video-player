package com.example.ui.components

import android.content.Intent
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.PlaybackService
import com.example.SubtitleApplication
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
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onVerticalDragLeft: (Float) -> Unit,
    onVerticalDragRight: (Float) -> Unit,
    onHorizontalDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as SubtitleApplication
    val exoPlayer = app.exoPlayer
    val currentOnProgressUpdate = rememberUpdatedState(onProgressUpdate)

    // Handle play / pause, seek, speed changes
    LaunchedEffect(uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (lastPlayedPosition > 0L) {
            exoPlayer.seekTo(lastPlayedPosition)
        }
        exoPlayer.playWhenReady = isPlaying
        
        // Start background playback service
        val intent = Intent(context, PlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
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
            // Do NOT release exoPlayer here, as it is a shared singleton in Application
        }
    }

    // GestureOverlay wraps the AndroidView PlayerView
    GestureOverlay(
        onDoubleTapLeft = onDoubleTapLeft,
        onDoubleTapRight = onDoubleTapRight,
        onVerticalDragLeft = onVerticalDragLeft,
        onVerticalDragRight = onVerticalDragRight,
        onHorizontalDrag = onHorizontalDrag,
        onDragEnd = onDragEnd,
        onTap = onTap,
        modifier = modifier.background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Hide default controls since we draw custom MX Player UI
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
