package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.VideoFile
import com.example.ui.viewmodel.SubtitlePlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun VideoLibraryScreen(
    viewModel: SubtitlePlayerViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.filteredVideos.collectAsState()
    val distinctFolders by viewModel.distinctFolders.collectAsState()
    
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

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
        } else {
            showPermissionRationale = true
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
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort Options", tint = Color.White)
                    }
                    if (hasPermission) {
                        IconButton(onClick = { viewModel.scanLocalVideos() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan Videos", tint = Color.White)
                        }
                    }
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Video", color = Color.White)
                    }
                }
            )

            // Search Bar & Filters Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search by name, folder or format...", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                // Horizontal Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Favorites Toggle Chip
                    FilterChip(
                        selected = showFavoritesOnly,
                        onClick = { viewModel.toggleFavoritesOnly(!showFavoritesOnly) },
                        label = { Text("Favorites Only") },
                        leadingIcon = if (showFavoritesOnly) {
                            { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color.White,
                            selectedLabelColor = Color.White,
                            selectedContainerColor = Color(0xFFEC4899)
                        )
                    )

                    // Folders list row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFolder == null,
                                onClick = { viewModel.selectFolder(null) },
                                label = { Text("All Folders") },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = Color.White,
                                    selectedLabelColor = Color.White,
                                    selectedContainerColor = Color(0xFF6366F1)
                                )
                            )
                        }
                        items(distinctFolders) { folder ->
                            FilterChip(
                                selected = selectedFolder == folder,
                                onClick = { viewModel.selectFolder(folder) },
                                label = { Text(folder) },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = Color.White,
                                    selectedLabelColor = Color.White,
                                    selectedContainerColor = Color(0xFF6366F1)
                                )
                            )
                        }
                    }
                }
            }

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
                            contentDescription = "Permission Required",
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
                            "Please grant video file read permission to scan and play video files stored locally on your device.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { permissionLauncher.launch(permission) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                            ) {
                                Text("Grant Permission", color = Color.White)
                            }
                            TextButton(onClick = { showPermissionRationale = true }) {
                                Text("Why?", color = Color.LightGray)
                            }
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
                        Text("No matching videos", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Adjust your search, filter, or import a video url.", color = Color.Gray, fontSize = 13.sp)
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
                            onToggleFavorite = {
                                viewModel.toggleFavorite(video)
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

        // --- Custom Import URL Dialog ---
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

        // --- Sorting Dialog Menu ---
        if (showSortMenu) {
            val sortModes = listOf(
                "DATE_ADDED" to "Date Added (Recent First)",
                "NAME_AZ" to "Name (A-Z)",
                "NAME_ZA" to "Name (Z-A)",
                "DURATION" to "Duration",
                "SIZE" to "File Size"
            )
            AlertDialog(
                onDismissRequest = { showSortMenu = false },
                title = { Text("Sort Library By", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sortModes.forEach { (mode, label) ->
                            val isSelected = sortBy == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        viewModel.updateSortOrder(mode)
                                        showSortMenu = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateSortOrder(mode)
                                        showSortMenu = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6366F1))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // --- Permission Explanation Dialog ---
        if (showPermissionRationale) {
            AlertDialog(
                onDismissRequest = { showPermissionRationale = false },
                title = { Text("Permission Explanation", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Text(
                        "This app requires video media access to index local videos and extract target audio tracks for offline subtitle generation.\n\nIf the system prompt is disabled, you can grant it manually in App Settings.",
                        color = Color.LightGray
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionRationale = false
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                permissionLauncher.launch(permission)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Open Settings", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionRationale = false }) {
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
    onToggleFavorite: () -> Unit,
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
            // Thumbnail / Icon Mock
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        video.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Favorite Toggle Icon
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (video.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "Favorite",
                            tint = if (video.isFavorite) Color(0xFFEC4899) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                val sizeText = if (video.fileSize > 0) " • ${(video.fileSize / (1024 * 1024))} MB" else ""
                val folderText = if (video.folderName.isNotEmpty()) " • ${video.folderName}" else ""
                Text(
                    "Format: ${video.mimeType.substringAfter("/")}  $sizeText$folderText",
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
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
