package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiSubtitleService
import com.example.data.model.VideoFile
import com.example.ui.viewmodel.SubtitlePlayerViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    viewModel: SubtitlePlayerViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val progressPercent by viewModel.transcriptionProgress.collectAsState()
    val currentStep by viewModel.transcriptionStep.collectAsState()
    val isProcessingAI by viewModel.isProcessingAI.collectAsState()
    val aiResultText by viewModel.aiResultText.collectAsState()

    var selectedLang by remember { mutableStateOf("English") }
    var isOfflineMode by remember { mutableStateOf(true) }
    var enableNoiseReduction by remember { mutableStateOf(true) }
    var enableSpeakerId by remember { mutableStateOf(true) }

    var showTranslateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("AI Subtitle Generator", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B)),
                navigationIcon = {
                    IconButton(onClick = { onNavigate("HOME") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )

            if (selectedVideo == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI", tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No video loaded", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Select a video from the library to generate captions.", color = Color.Gray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigate("LIBRARY") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text("Go to Library", color = Color.White)
                        }
                    }
                }
            } else {
                val video = selectedVideo!!

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                // Video loaded card details
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${video.mimeType} • ${video.duration / 1000}s", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (isTranscribing) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Engaging AI Speech Transcriptor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                CircularProgressIndicator(
                                    progress = progressPercent / 100f,
                                    color = Color(0xFFEC4899),
                                    strokeWidth = 6.dp,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("$progressPercent%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(currentStep, color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    item {
                        // Settings configurations
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Speech-to-Text Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                                // Spoken Languages
                                Column {
                                    Text("Spoken Language", color = Color.Gray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    var expanded by remember { mutableStateOf(false) }
                                    Box {
                                        Button(
                                            onClick = { expanded = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) {
                                            Text(selectedLang, color = Color.White)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                                        }
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.background(Color(0xFF1E293B))
                                        ) {
                                            listOf(
                                                "English",
                                                "Tamil",
                                                "Hindi",
                                                "Telugu",
                                                "Malayalam",
                                                "Kannada",
                                                "French",
                                                "German",
                                                "Spanish"
                                            ).forEach { lang ->
                                                DropdownMenuItem(
                                                    text = { Text(lang, color = Color.White) },
                                                    onClick = {
                                                        selectedLang = lang
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Mode Toggle Tweak parameters
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Offline Transcription", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Use on-device Whisper Tiny engine (Offline-First)", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = isOfflineMode,
                                        onCheckedChange = { isOfflineMode = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFEC4899))
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Vocal Noise Reduction", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Filter background atmospheric hum checks", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = enableNoiseReduction,
                                        onCheckedChange = { enableNoiseReduction = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFEC4899))
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Speaker Identification", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Isolate and assign speaker track partitions", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = enableSpeakerId,
                                        onCheckedChange = { enableSpeakerId = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFEC4899))
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        viewModel.startTranscription(
                                            video,
                                            selectedLang,
                                            isOfflineMode,
                                            enableNoiseReduction,
                                            enableSpeakerId
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Generator", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (video.hasSubtitles) {
                        item {
                            // Online Translation Tool / Advanced Gemini Features
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Advanced AI Studio Actions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Use Cloud Gemini 3.5 Flash to automatically process transcripts, summarize content, or translate subtitles directly.", color = Color.Gray, fontSize = 12.sp)

                                    if (!GeminiSubtitleService.isApiKeyAvailable()) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text("Note: Add GEMINI_API_KEY inside the Secrets panel to fully unlock these features!", color = Color.White, fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    // Action buttons grid
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { showTranslateDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Translate", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = { viewModel.summarizeSpeechTranscript() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Subject, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Summarize", fontSize = 11.sp)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.extractKeywordsFromTranscript() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Extract Tags", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = { viewModel.generateChaptersFromTranscript() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Chapters", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Processing overlays ---
        if (isProcessingAI) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFEC4899))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("AI is processing details...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- AI Response dialog with results ---
        if (aiResultText != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAiDialog() },
                title = { Text("AI Studio Output", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Text(aiResultText ?: "", color = Color.LightGray)
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissAiDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                    ) {
                        Text("Dismiss", color = Color.White)
                    }
                }
            )
        }

        // --- Translate Target Selection Dialog ---
        if (showTranslateDialog) {
            var targetLangChoice by remember { mutableStateOf("Tamil") }
            AlertDialog(
                onDismissRequest = { showTranslateDialog = false },
                title = { Text("Translate Captions", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Translate subtitle tracks with timing parameters synced.", color = Color.Gray, fontSize = 12.sp)
                        var dropExpanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { dropExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                            ) {
                                Text(targetLangChoice, color = Color.White)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = dropExpanded,
                                onDismissRequest = { dropExpanded = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                listOf(
                                    "English", "Tamil", "Hindi", "Telugu", "Malayalam",
                                    "Kannada", "French", "German", "Spanish"
                                ).forEach { l ->
                                    DropdownMenuItem(
                                        text = { Text(l, color = Color.White) },
                                        onClick = {
                                            targetLangChoice = l
                                            dropExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.translateSubtitlesWithGemini(targetLangChoice)
                            showTranslateDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Translate", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTranslateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
