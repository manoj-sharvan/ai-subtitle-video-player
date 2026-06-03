package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoFile
import com.example.ui.viewmodel.SubtitlePlayerViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    viewModel: SubtitlePlayerViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.allVideos.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.scanLocalVideos()
        }
    }
    
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.scanLocalVideos()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar
            TopAppBar(
                title = { Text("Video Library", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B)),
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = { viewModel.scanLocalVideos() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Scan Local Videos",
                                tint = Color.White
                            )
                        }
                    }
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Import", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Video", color = Color.White)
                    }
                }
            )

            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "Permission required",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Local Storage Access Required",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Please grant permission to scan and play video files stored locally on your device.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(permission) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text("Grant Permission", color = Color.White)
                        }
                    }
                }
            }

            if (videos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Movie,
                            contentDescription = "Empty",
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your Library is empty", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Import a live stream or local video playing track.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(videos) { video ->
                        VideoItemCard(
                            video = video,
                            onClick = {
                                viewModel.selectVideo(video)
                                onNavigate("PLAYER")
                            },
                            onTranscribe = {
                                viewModel.selectVideo(video)
                                onNavigate("GENERATOR")
                            },
                            onDelete = {
                                viewModel.deleteVideo(video)
                            }
                        )
                    }
                }
            }
        }

        // --- Custom Import URL Action ---
        if (showImportDialog) {
            var url by remember { mutableStateOf("") }
            var title by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import Video", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Support MP4, MKV, AVI, MOV and WebM network stream links.", color = Color.Gray, fontSize = 12.sp)
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Friendly Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedLabelColor = Color.Gray,
                                focusedLabelColor = Color(0xFF10B981)
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Direct Video Link URL") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedLabelColor = Color.Gray,
                                focusedLabelColor = Color(0xFF10B981)
                            ),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (title.isNotBlank() && url.isNotBlank()) {
                                viewModel.addCustomVideo(title, url)
                                showImportDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Add to Library", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun VideoItemCard(
    video: VideoFile,
    onClick: () -> Unit,
    onTranscribe: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Mock
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircleFilled,
                    contentDescription = "Play",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Format: ${video.mimeType.substringAfter("/")}  •  ${video.duration / 1000}s",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (video.hasSubtitles) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("AI Active CC", fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = Color(0xFF10B981)
                            )
                        )
                    } else {
                        SuggestionChip(
                            onClick = onTranscribe,
                            label = { Text("Auto Subtitle", fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = Color(0xFFCA8A04)
                            )
                        )
                    }
                }
            }

            // Quick Actions Options
            Row {
                IconButton(onClick = onTranscribe) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Transcribe with AI",
                        tint = Color(0xFFEC4899)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Video",
                        tint = Color.Red.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
