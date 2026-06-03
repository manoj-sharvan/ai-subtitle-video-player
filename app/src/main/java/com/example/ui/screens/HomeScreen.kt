package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoFile
import com.example.ui.viewmodel.SubtitlePlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SubtitlePlayerViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val recentVideos by viewModel.recentVideos.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    var showAddDialog by remember { mutableStateFlowOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)), // Deep Slate Theme
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- Header Section ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "AI Subtitle Player",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "AI-Powered Speech-to-Text Video Player",
                                fontSize = 12.sp,
                                color = Color(0xFFC7D2FE)
                            )
                        }
                        IconButton(
                            onClick = { showAddDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Video Url")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = "AI Tip",
                                tint = Color(0xFFFCD34D),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Tip: Choose Sintel or Big Buck Bunny to immediately experience auto-syncing subtitle playback styles!",
                                fontSize = 11.sp,
                                color = Color.White,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // --- Quick Actions Row ---
        item {
            Text(
                "Quick Navigation",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    title = "Media Lib",
                    icon = Icons.Default.VideoLibrary,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("LIBRARY") }
                )
                QuickActionCard(
                    title = "AI Gen",
                    icon = Icons.Default.AutoAwesome,
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("GENERATOR") }
                )
                QuickActionCard(
                    title = "Editor",
                    icon = Icons.Default.Edit,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("EDITOR") }
                )
                QuickActionCard(
                    title = "Subtitle FX",
                    icon = Icons.Default.Settings,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("SETTINGS") }
                )
            }
        }

        // --- Statistics Widget ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatisticItem(value = "${allVideos.size}", label = "Total Videos")
                    VerticalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.height(30.dp))
                    StatisticItem(value = "${allVideos.count { it.hasSubtitles }}", label = "Subtitled")
                    VerticalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.height(30.dp))
                    StatisticItem(value = "Whisper", label = "Engine")
                }
            }
        }

        // --- Recent Videos Section ---
        item {
            Text(
                "Recently Played",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (recentVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No videos played yet.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentVideos) { video ->
                        RecentVideoCard(
                            video = video,
                            onClick = {
                                viewModel.selectVideo(video)
                                onNavigate("PLAYER")
                            }
                        )
                    }
                }
            }
        }

        // --- Platform Guides ---
        item {
            Text(
                "How AI Generation Works",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GuideStepItem(
                        number = "1",
                        title = "Extract Audio Track",
                        description = "Separates vocal speech patterns from backings."
                    )
                    GuideStepItem(
                        number = "2",
                        title = "Offline Whisper Engine",
                        description = "Runs local acoustic models to transcript syllables."
                    )
                    GuideStepItem(
                        number = "3",
                        title = "Punctuation & Timestamps",
                        description = "Auto-groups characters into synchronized timing sentences."
                    )
                }
            }
        }
    }

    // --- Add Direct URL Video Dialog ---
    if (showAddDialog) {
        var videoTitle by remember { mutableStateOf("") }
        var videoUrl by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Stream New Video", color = Color.White) },
            containerColor = Color(0xFF1E293B),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = videoTitle,
                        onValueChange = { videoTitle = it },
                        label = { Text("Video Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedLabelColor = Color.Gray,
                            focusedLabelColor = Color(0xFF6366F1)
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it },
                        label = { Text("Direct MP4 Stream URL") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedLabelColor = Color.Gray,
                            focusedLabelColor = Color(0xFF6366F1)
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (videoTitle.isNotBlank() && videoUrl.isNotBlank()) {
                            viewModel.addCustomVideo(videoTitle, videoUrl)
                            showAddDialog = false
                            onNavigate("LIBRARY")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("Add Stream", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clickable { onClick() }
            .height(72.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                title,
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun StatisticItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            label,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun RecentVideoCard(
    video: VideoFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .width(160.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircleOutline,
                    contentDescription = "Recent Playback",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                video.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (video.hasSubtitles) "CC Sync" else "No Subs",
                    fontSize = 10.sp,
                    color = if (video.hasSubtitles) Color(0xFF10B981) else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = "Tending",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun GuideStepItem(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color(0xFF6366F1), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

fun <T> mutableStateFlowOf(value: T) = mutableStateOf(value)
