package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubtitlePosition
import com.example.data.model.VideoFile
import com.example.ui.components.VideoPlayerView
import com.example.ui.viewmodel.SubtitlePlayerViewModel
import com.example.data.localai.model.RagChunk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun PlayerScreen(
    viewModel: SubtitlePlayerViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val activeSubtitle by viewModel.activeSubtitle.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val subtitleOffsetMs by viewModel.subtitleOffsetMs.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    if (selectedVideo == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No Video Selected", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onNavigate("LIBRARY") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("Browse Library", color = Color.White)
                }
            }
        }
        return
    }

    val video = selectedVideo!!

    // State Variables
    var isPlaying by remember { mutableStateOf(true) }
    var currentSeekPosition by remember { mutableStateOf(0L) }
    val totalDuration = remember(video.duration) { if (video.duration > 0) video.duration else 180000L }

    // Gesture status overlays state
    var volumeGestureText by remember { mutableStateOf<String?>(null) }
    var brightnessGestureText by remember { mutableStateOf<String?>(null) }
    var seekGestureText by remember { mutableStateOf<String?>(null) }

    // Orientation state
    var isLandscape by remember { mutableStateOf(false) }

    // Controls locking
    var isLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleSyncDialog by remember { mutableStateOf(false) }
    var showSubtitleStylesDialog by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(video.lastPlayedPosition > 2000L) }
    var showAiCopilot by remember { mutableStateOf(false) }

    // Auto-hide controls handler
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Audio & Brightness adjustment mechanics
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    
    val adjustVolume: (Float) -> Unit = { delta ->
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = if (delta > 0) 1 else -1
        val newVol = (currentVol + step).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        val percent = (newVol * 100) / maxVolume
        volumeGestureText = "Volume: $percent%"
        coroutineScope.launch {
            delay(1200)
            volumeGestureText = null
        }
    }

    val adjustBrightness: (Float) -> Unit = { delta ->
        val activity = context as? Activity
        val layoutParams = activity?.window?.attributes
        val currentBright = if (layoutParams?.screenBrightness ?: -1f < 0) 0.5f else layoutParams?.screenBrightness ?: 0.5f
        val newBright = (currentBright + delta * 0.1f).coerceIn(0.01f, 1.0f)
        layoutParams?.screenBrightness = newBright
        activity?.window?.attributes = layoutParams
        brightnessGestureText = "Brightness: ${(newBright * 100).toInt()}%"
        coroutineScope.launch {
            delay(1200)
            brightnessGestureText = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- ExoPlayer Embed View ---
        val app = context.applicationContext as com.example.SubtitleApplication
        VideoPlayerView(
            uri = video.uri,
            isPlaying = isPlaying && !showResumeDialog,
            onPlayingChanged = { isPlaying = it },
            playbackSpeed = playbackSpeed,
            lastPlayedPosition = video.lastPlayedPosition,
            onProgressUpdate = { currentMs ->
                currentSeekPosition = currentMs
                viewModel.updatePlayerTime(currentMs)
                viewModel.updateVideoLastPlayed(video.id, currentMs)
            },
            onDoubleTapLeft = {
                val newPos = (app.exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                app.exoPlayer.seekTo(newPos)
                seekGestureText = "Seek: -10s"
                coroutineScope.launch {
                    delay(1200)
                    seekGestureText = null
                }
            },
            onDoubleTapRight = {
                val newPos = (app.exoPlayer.currentPosition + 10000L).coerceAtMost(totalDuration)
                app.exoPlayer.seekTo(newPos)
                seekGestureText = "Seek: +10s"
                coroutineScope.launch {
                    delay(1200)
                    seekGestureText = null
                }
            },
            onVerticalDragLeft = { delta ->
                adjustBrightness(delta)
            },
            onVerticalDragRight = { delta ->
                adjustVolume(delta)
            },
            onHorizontalDrag = { delta ->
                // timeline scrubbing
                val seekDelta = (delta * 100).toLong()
                val newPos = (app.exoPlayer.currentPosition + seekDelta).coerceIn(0L, totalDuration)
                app.exoPlayer.seekTo(newPos)
                val diffSec = seekDelta / 1000
                seekGestureText = if (diffSec >= 0) "+${diffSec}s" else "${diffSec}s"
                coroutineScope.launch {
                    delay(1200)
                    seekGestureText = null
                }
            },
            onDragEnd = {},
            onTap = {
                if (!isLocked) {
                    showControls = !showControls
                } else {
                    // if locked, tapping shows unlock button momentarily
                    showControls = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- Custom Subtitle Overlay ---
        if (subtitlesEnabled && activeSubtitle != null) {
            val alignValue = when (subtitleStyle.position) {
                SubtitlePosition.TOP -> Alignment.TopCenter
                SubtitlePosition.CENTER -> Alignment.Center
                SubtitlePosition.BOTTOM -> Alignment.BottomCenter
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = if (subtitleStyle.position == SubtitlePosition.TOP) 80.dp else 24.dp,
                        bottom = if (subtitleStyle.position == SubtitlePosition.BOTTOM) 110.dp else 24.dp
                    ),
                contentAlignment = alignValue
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Color(android.graphics.Color.parseColor(subtitleStyle.backgroundColorHex))
                                .copy(alpha = subtitleStyle.backgroundOpacity)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val font = when (subtitleStyle.fontFamily) {
                        "Monospace" -> FontFamily.Monospace
                        "Serif" -> FontFamily.Serif
                        "SansSerif" -> FontFamily.SansSerif
                        else -> FontFamily.Default
                    }
                    Text(
                        text = activeSubtitle?.text ?: "",
                        color = Color(android.graphics.Color.parseColor(subtitleStyle.textColorHex)),
                        fontSize = subtitleStyle.fontSizeSp.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // --- HUD Overlay indicators ---
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = brightnessGestureText != null, enter = fadeIn(), exit = fadeOut()) {
                    GestureHudChip(text = brightnessGestureText ?: "", icon = Icons.Default.BrightnessMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedVisibility(visible = volumeGestureText != null, enter = fadeIn(), exit = fadeOut()) {
                    GestureHudChip(text = volumeGestureText ?: "", icon = Icons.Default.VolumeUp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedVisibility(visible = seekGestureText != null, enter = fadeIn(), exit = fadeOut()) {
                    GestureHudChip(text = seekGestureText ?: "", icon = Icons.Default.SwapHoriz)
                }
            }
        }

        // --- Playback Controls Redesign ---
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
        ) {
            // Locked UI layout
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    FloatingActionButton(
                        onClick = {
                            isLocked = false
                            showControls = true
                        },
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock controls")
                    }
                }
            } else {
                // Top Overlay controller
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(onClick = { onNavigate("LIBRARY") }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    video.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (video.hasSubtitles) "AI Subtitles Active" else "No subtitles loaded",
                                    color = if (video.hasSubtitles) Color(0xFF10B981) else Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row {
                            // Subtitle Toggle
                            IconButton(onClick = { viewModel.toggleSubtitles(!subtitlesEnabled) }) {
                                Icon(
                                    if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                    contentDescription = "Toggle Subtitles",
                                    tint = if (subtitlesEnabled) Color(0xFF6366F1) else Color.White
                                )
                            }
                            // Aspect Ratio Rotation toggle
                            IconButton(onClick = {
                                val activity = context as? Activity
                                isLandscape = !isLandscape
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }) {
                                Icon(
                                    if (isLandscape) Icons.Default.ScreenLockLandscape else Icons.Default.ScreenRotation,
                                    contentDescription = "Rotate",
                                    tint = Color.White
                                )
                            }
                            // Speed Selector
                            IconButton(onClick = { showSpeedDialog = true }) {
                                Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                            }
                            // Subtitle synchronization
                            IconButton(onClick = { showSubtitleSyncDialog = true }) {
                                Icon(Icons.Default.Sync, contentDescription = "Subtitle delay offset", tint = Color.White)
                            }
                            // Style settings
                            IconButton(onClick = { showSubtitleStylesDialog = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Subtitle Settings", tint = Color.White)
                            }
                            // Picture-in-Picture Mode
                            IconButton(onClick = {
                                val activity = context as? Activity
                                activity?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
                            }) {
                                Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                            }
                            // AI Copilot
                            IconButton(onClick = { showAiCopilot = !showAiCopilot }) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI Copilot",
                                    tint = if (showAiCopilot) Color(0xFFEC4899) else Color.White
                                )
                            }
                        }
                    }

                    // Center Controllers (Play/Pause, Locks, Rewind)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        IconButton(onClick = {
                            val newPos = (currentSeekPosition - 10000L).coerceAtLeast(0L)
                            app.exoPlayer.seekTo(newPos)
                        }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        IconButton(onClick = {
                            isLocked = true
                            showControls = false
                        }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock controls", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        IconButton(onClick = {
                            val newPos = (currentSeekPosition + 10000L).coerceAtMost(totalDuration)
                            app.exoPlayer.seekTo(newPos)
                        }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }

                    // Bottom Seek scrubbing timeline & duration tags
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentSeekPosition),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Slider(
                                value = currentSeekPosition.toFloat(),
                                onValueChange = {
                                    currentSeekPosition = it.toLong()
                                    app.exoPlayer.seekTo(currentSeekPosition)
                                },
                                valueRange = 0f..totalDuration.toFloat(),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF6366F1),
                                    activeTrackColor = Color(0xFF6366F1),
                                    inactiveTrackColor = Color.Gray
                                )
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val remaining = totalDuration - currentSeekPosition
                                Text(
                                    text = "-${formatTime(remaining)}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "/ ${formatTime(totalDuration)}",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Dialogs & Sheets ---

        // 1. Playback Speed Selector Dialog
        if (showSpeedDialog) {
            val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("Playback Speed", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        speedOptions.forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        viewModel.updatePlaybackSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = if (isSelected) Color(0xFF6366F1) else Color.Gray)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("${speed}x" + if (speed == 1.0f) " (Normal)" else "", color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // 2. Subtitle Synchronization Offset Dialog
        if (showSubtitleSyncDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleSyncDialog = false },
                title = { Text("Subtitle Sync Offset", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Delay Offset: ${if (subtitleOffsetMs >= 0) "+" else ""}${subtitleOffsetMs / 1000.0}s",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.updateSubtitleOffset(subtitleOffsetMs - 100) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("-100ms", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.updateSubtitleOffset(subtitleOffsetMs + 100) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+100ms", fontSize = 11.sp)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.updateSubtitleOffset(subtitleOffsetMs - 500) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("-500ms", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.updateSubtitleOffset(subtitleOffsetMs + 500) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+500ms", fontSize = 11.sp)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.updateSubtitleOffset(subtitleOffsetMs - 1000) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("-1s", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.updateSubtitleOffset(subtitleOffsetMs + 1000) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+1s", fontSize = 11.sp)
                            }
                        }

                        TextButton(
                            onClick = { viewModel.updateSubtitleOffset(0L) }
                        ) {
                            Text("Reset Sync Offset", color = Color(0xFFEC4899))
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showSubtitleSyncDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }

        // 3. Subtitle Formatting preferences
        if (showSubtitleStylesDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleStylesDialog = false },
                title = { Text("Subtitle Customization", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Size Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Font Size", color = Color.White, fontSize = 13.sp)
                                Text("${subtitleStyle.fontSizeSp.toInt()} sp", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = subtitleStyle.fontSizeSp,
                                onValueChange = { viewModel.updateSubtitleStyle(fontSizeSp = it) },
                                valueRange = 12f..32f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF6366F1), activeTrackColor = Color(0xFF6366F1))
                            )
                        }

                        // Colors Palette
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Font Color", color = Color.White, fontSize = 13.sp)
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("#FFFFFF", "#FFFF00", "#00FF00", "#00FFFF", "#FF00FF").forEach { hex ->
                                    val isSel = subtitleStyle.textColorHex == hex
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .clickable { viewModel.updateSubtitleStyle(textColorHex = hex) }
                                            .run {
                                                if (isSel) this.border(2.dp, Color(0xFF6366F1), RoundedCornerShape(16.dp)) else this
                                            }
                                    )
                                }
                            }
                        }

                        // Position alignment
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Position", color = Color.White, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SubtitlePosition.values().forEach { pos ->
                                    Button(
                                        onClick = { viewModel.updateSubtitleStyle(position = pos) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (subtitleStyle.position == pos) Color(0xFF6366F1) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(pos.name, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showSubtitleStylesDialog = false }) {
                        Text("Apply")
                    }
                }
            )
        }

        // 4. Resume Playback Alert
        if (showResumeDialog) {
            AlertDialog(
                onDismissRequest = { showResumeDialog = false },
                title = { Text("Resume Playback?", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Text(
                        "Would you like to resume this video from where you left off (${formatTime(video.lastPlayedPosition)})?",
                        color = Color.LightGray
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            app.exoPlayer.seekTo(video.lastPlayedPosition)
                            showResumeDialog = false
                            isPlaying = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Yes, Resume", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            app.exoPlayer.seekTo(0L)
                            showResumeDialog = false
                            isPlaying = true
                        }
                    ) {
                        Text("Start Over", color = Color.Gray)
                    }
                }
            )
        }

        // --- AI Copilot Side Sheet ---
        AnimatedVisibility(
            visible = showAiCopilot,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(360.dp)
        ) {
            AiCopilotPanel(
                viewModel = viewModel,
                onClose = { showAiCopilot = false },
                onSeek = { app.exoPlayer.seekTo(it) }
            )
        }
    }
}

@Composable
fun AiCopilotPanel(
    viewModel: SubtitlePlayerViewModel,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val chatHistory by viewModel.chatHistory.collectAsState()
    val searchResults by viewModel.ragSearchResults.collectAsState()
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val isProcessingAI by viewModel.isProcessingAI.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Q&A, 1: Search, 2: Insights

    Card(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFEC4899))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Copilot", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            // Tab bar
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = Color(0xFFEC4899)
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Q&A", fontSize = 12.sp) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Search", fontSize = 12.sp) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Insights", fontSize = 12.sp) }
                )
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (activeTab) {
                    0 -> QaChatTab(
                        chatHistory = chatHistory,
                        isProcessing = isProcessingAI,
                        onAsk = { viewModel.askQuestionAboutVideo(it) }
                    )
                    1 -> SearchTab(
                        searchResults = searchResults,
                        onSearch = { viewModel.searchSubtitlesLocal(it) },
                        onSeek = onSeek
                    )
                    2 -> InsightsTab(
                        video = selectedVideo,
                        onSummarize = { viewModel.summarizeSpeechTranscript() },
                        onExtractChapters = { viewModel.generateChaptersFromTranscript() },
                        onSeek = onSeek
                    )
                }
            }
        }
    }
}

@Composable
fun QaChatTab(
    chatHistory: List<SubtitlePlayerViewModel.ChatMessage>,
    isProcessing: Boolean,
    onAsk: (String) -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            lazyListState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (chatHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Ask questions about this video!", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("The local AI model will answer based on transcript context.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatHistory) { msg ->
                        val alignSide = if (msg.isUser) Alignment.End else Alignment.Start
                        val bgColor = if (msg.isUser) Color(0xFF6366F1) else Color(0xFF334155)
                        
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignSide) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .padding(10.dp)
                                    .widthIn(max = 240.dp)
                            ) {
                                Text(msg.text, color = Color.White, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (msg.isUser) "You" else "Local LLM",
                                color = Color.Gray,
                                fontSize = 9.sp
                            )
                        }
                    }
                    if (isProcessing) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFEC4899))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Thinking...", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = queryText,
                onValueChange = { queryText = it },
                placeholder = { Text("Ask local AI...", fontSize = 12.sp, color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (queryText.trim().isNotEmpty()) {
                        onAsk(queryText)
                        queryText = ""
                    }
                },
                enabled = queryText.trim().isNotEmpty(),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (queryText.trim().isNotEmpty()) Color(0xFFEC4899) else Color(0xFF334155))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
fun SearchTab(
    searchResults: List<RagChunk>,
    onSearch: (String) -> Unit,
    onSeek: (Long) -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        TextField(
            value = searchText,
            onValueChange = {
                searchText = it
                onSearch(it)
            },
            placeholder = { Text("Search keywords in transcript...", fontSize = 12.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (searchText.trim().isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Type a keyword to search", color = Color.Gray, fontSize = 12.sp)
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matches found", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { chunk ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(chunk.startMs) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = chunk.text,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = formatTime(chunk.startMs),
                                        color = Color(0xFFEC4899),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InsightsTab(
    video: VideoFile?,
    onSummarize: () -> Unit,
    onExtractChapters: () -> Unit,
    onSeek: (Long) -> Unit
) {
    if (video == null) return

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF334155))) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Auto Summary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (video.summary != null && video.summary.isNotEmpty()) {
                        Text(video.summary, color = Color.LightGray, fontSize = 12.sp)
                    } else {
                        Button(
                            onClick = onSummarize,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                        ) {
                            Text("Generate Summary", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF334155))) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Video Chapters", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (video.chapters != null && video.chapters.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            video.chapters.forEach { marker ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSeek(marker.timeMs) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatTime(marker.timeMs),
                                        color = Color(0xFFEC4899),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = marker.title,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onExtractChapters,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                        ) {
                            Text("Extract Chapters", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF334155))) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Keywords & Tags", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (video.keywords != null && video.keywords.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            video.keywords.forEach { word ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("#$word", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    } else {
                        Text("No keywords extracted yet.", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GestureHudChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
