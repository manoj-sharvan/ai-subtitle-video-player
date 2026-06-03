package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
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

    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val progressPercent by viewModel.transcriptionProgress.collectAsState()
    val currentStep by viewModel.transcriptionStep.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var currentSeekPosition by remember { mutableStateOf(0L) }

    // Gesture indicator overlays state
    var volumeGestureText by remember { mutableStateOf<String?>(null) }
    var brightnessGestureText by remember { mutableStateOf<String?>(null) }
    var seekGestureText by remember { mutableStateOf<String?>(null) }

    var showSpeedDialog by remember { mutableStateOf(false) }

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

    // Auto-transcribe if selected local video doesn't have subtitles generated yet
    LaunchedEffect(video.id) {
        if (!video.hasSubtitles && !isTranscribing) {
            viewModel.startTranscription(
                video = video,
                language = "English",
                isOfflineMode = true,
                enableNoiseReduction = true,
                enableSpeakerId = true
            )
        }
    }

    if (!video.hasSubtitles || isTranscribing) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "AI Processing",
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "AI Subtitle Generator",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Automatically generating synchronized captions for your local video...",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                CircularProgressIndicator(
                    progress = progressPercent / 100f,
                    color = Color(0xFFEC4899),
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$progressPercent%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentStep,
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val stepsList = listOf(
                            "Scanning Videos" to 10,
                            "Loading Video" to 20,
                            "Extracting Audio" to 40,
                            "Uploading Audio" to 60,
                            "Generating Subtitle" to 85,
                            "Saving Subtitle" to 95,
                            "Ready to Play" to 100
                        )
                        
                        stepsList.forEachIndexed { index, step ->
                            val targetPercent = step.second
                            val stepName = step.first
                            val prevTarget = if (index > 0) stepsList[index - 1].second else 0
                            
                            val isCompleted = progressPercent >= targetPercent
                            val isActive = progressPercent > prevTarget && progressPercent < targetPercent
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isCompleted) Icons.Default.CheckCircle else if (isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isCompleted) Color(0xFF10B981) else if (isActive) Color(0xFFEC4899) else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stepName,
                                        color = if (isCompleted || isActive) Color.White else Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (isActive) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFEC4899),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { onNavigate("LIBRARY") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Library", color = Color.White)
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- ExoPlayer Embed View ---
        VideoPlayerView(
            uri = video.uri,
            isPlaying = isPlaying,
            onPlayingChanged = { isPlaying = it },
            playbackSpeed = playbackSpeed,
            lastPlayedPosition = video.lastPlayedPosition,
            onProgressUpdate = { currentMs ->
                currentSeekPosition = currentMs
                viewModel.updatePlayerTime(currentMs)
                // save position periodically
                viewModel.updateVideoLastPlayed(video.id, currentMs)
            },
            onBrightnessGesture = { delta ->
                brightnessGestureText = "Brightness: ${(delta * 100).toInt()}%"
                coroutineScope.launch {
                    delay(1200)
                    brightnessGestureText = null
                }
            },
            onVolumeGesture = { delta ->
                volumeGestureText = "Volume: ${(delta * 100).toInt()}%"
                coroutineScope.launch {
                    delay(1200)
                    volumeGestureText = null
                }
            },
            onSeekGesture = { deltaMs ->
                val seconds = deltaMs / 1000
                seekGestureText = if (seconds > 0) "FF: +${seconds}s" else "RW: ${seconds}s"
                coroutineScope.launch {
                    delay(1200)
                    seekGestureText = null
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- Custom Interactive Styled Subtitle Screen Overlay ---
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
                        top = if (subtitleStyle.position == SubtitlePosition.TOP) 48.dp else 16.dp,
                        bottom = if (subtitleStyle.position == SubtitlePosition.BOTTOM) 64.dp else 16.dp
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

        // --- Gesture Hud Feedback Indicators ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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

        // --- Top Custom Controller Action Overlay Standard bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        if (video.hasSubtitles) "Auto AI Subtitles Active" else "No Subtitle track (Tap Auto Subtitle icon)",
                        color = if (video.hasSubtitles) Color(0xFF10B981) else Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            Row {
                IconButton(onClick = { viewModel.toggleSubtitles(!subtitlesEnabled) }) {
                    Icon(
                        if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                        contentDescription = "Toggle Subtitles",
                        tint = if (subtitlesEnabled) Color(0xFF6366F1) else Color.White
                    )
                }
                IconButton(onClick = { showSpeedDialog = true }) {
                    Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                }
                IconButton(onClick = { onNavigate("SETTINGS") }) {
                    Icon(Icons.Default.Tune, contentDescription = "Subtitle Style Tuning", tint = Color.White)
                }
                IconButton(onClick = {
                    // Triggers Picture-in-Picture mode on modern devices!
                    val activity = context as? Activity
                    if (activity != null) {
                        activity.enterPictureInPictureMode()
                    }
                }) {
                    Icon(Icons.Default.PictureInPicture, contentDescription = "PiP Playback", tint = Color.White)
                }
            }
        }

        // --- Navigation Quick Action floating overlay details ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick generate
                IconButton(onClick = { onNavigate("GENERATOR") }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Generate Menu", tint = Color(0xFFEC4899))
                }
                // Quick Edit
                IconButton(onClick = { onNavigate("EDITOR") }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Subtitles", tint = Color(0xFFF59E0B))
                }

                Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timeline, contentDescription = "Time", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            formatTimeText(currentSeekPosition),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Export
                IconButton(onClick = { onNavigate("EXPORT") }) {
                    Icon(Icons.Default.IosShare, contentDescription = "Export Subtitles", tint = Color(0xFF10B981))
                }
            }
        }
    }

    // --- Speed Dialog Selection ---
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", color = Color.White) },
            containerColor = Color(0xFF1E293B),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (playbackSpeed == s) Color(0xFF6366F1).copy(alpha = 0.3f) else Color.Transparent)
                                .clickable {
                                    viewModel.updatePlaybackSpeed(s)
                                    showSpeedDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (playbackSpeed == s) Color(0xFF6366F1) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${s}x" + if (s == 1.0f) " (Normal)" else "", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {}
        )
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

private fun formatTimeText(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
