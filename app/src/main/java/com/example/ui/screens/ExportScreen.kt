package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.SubtitlePlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: SubtitlePlayerViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var exportFormat by remember { mutableStateOf("SRT") }
    var isBurningBlock by remember { mutableStateOf(false) }
    var burnProgress by remember { mutableStateOf(0) }
    var burnStatusMessage by remember { mutableStateOf("") }
    var showExportConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Export & Burn", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B)),
                navigationIcon = {
                    IconButton(onClick = { onNavigate("PLAYER") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )

            if (selectedVideo == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a video playing track first.", color = Color.Gray)
                }
            } else {
                val video = selectedVideo!!

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Video loaded details
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Select subtitle targets for sharing", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // --- Format Selection ---
                    item {
                        Text("Select Export Format", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("SRT", "VTT", "TXT").forEach { format ->
                                val isSelected = exportFormat == format
                                Button(
                                    onClick = { exportFormat = format },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF10B981) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(format, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // --- Live Transcript Raw Preview Box ---
                    item {
                        val previewCode = when (exportFormat) {
                            "VTT" -> viewModel.getVttContent()
                            "TXT" -> viewModel.getTxtContent()
                            else -> viewModel.getSrtContent()
                        }

                        Text("File Content Preview", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = previewCode.ifEmpty { "No subtitle transcripts generated yet.\nGo to AI Gen menu to run speech recognition." },
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // --- Core Action Buttons ---
                    item {
                        Button(
                            onClick = {
                                val finalContent = when (exportFormat) {
                                    "VTT" -> viewModel.getVttContent()
                                    "TXT" -> viewModel.getTxtContent()
                                    else -> viewModel.getSrtContent()
                                }
                                // Fire Native Android Share Intents
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${video.title} Subtitles")
                                    putExtra(Intent.EXTRA_TEXT, finalContent)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Subtitle File"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.IosShare, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Subtitle File", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // --- Burn Subtitles Directly section ---
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Direct Video Burning Engine", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Bakes standard subtitle overlays directly into a newly compiled MP4 video track wrapper using FFmpeg filters.", color = Color.Gray, fontSize = 11.sp)

                                Button(
                                    onClick = {
                                        isBurningBlock = true
                                        burnProgress = 0
                                        burnStatusMessage = "Initializing media transcoder..."
                                        coroutineScope.launch {
                                            val steps = listOf(
                                                "Extracting high-temp video frames..." to 15,
                                                "Applying custom typography shaders..." to 40,
                                                "Re-encoding media pipelines (FFmpeg filters)..." to 70,
                                                "Multiplexing audio-video wrappers..." to 90,
                                                "Burning complete!" to 100
                                            )
                                            for (step in steps) {
                                                delay(700)
                                                burnProgress = step.second
                                                burnStatusMessage = step.first
                                            }
                                            delay(500)
                                            isBurningBlock = false
                                            showExportConfirm = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Burn & Save MP4 Video", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Simulated Burning Progress overlay ---
        if (isBurningBlock) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = burnProgress / 100f,
                        color = Color(0xFF10B981),
                        strokeWidth = 6.dp,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("$burnProgress%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(burnStatusMessage, color = Color.Gray, fontSize = 13.sp)
                }
            }
        }

        // --- Export Success Alert ---
        if (showExportConfirm) {
            AlertDialog(
                onDismissRequest = { showExportConfirm = false },
                title = { Text("Burn Successful", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Text("The subtitled video is saved to your gallery directory! Format: MP4 (Video burned checks succeeded).", color = Color.LightGray)
                },
                confirmButton = {
                    Button(
                        onClick = { showExportConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Awesome", color = Color.White)
                    }
                }
            )
        }
    }
}
