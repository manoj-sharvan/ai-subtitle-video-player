package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubtitleBlock
import com.example.ui.viewmodel.SubtitlePlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrtEditorScreen(
    viewModel: SubtitlePlayerViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val subtitles by viewModel.currentSubtitles.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var replaceTextBy by remember { mutableStateOf("") }
    var showReplaceDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Subtitle Editor", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B)),
                navigationIcon = {
                    IconButton(onClick = { onNavigate("PLAYER") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showReplaceDialog = true }) {
                        Icon(Icons.Default.FindReplace, contentDescription = "Replace Text", tint = Color.White)
                    }
                }
            )

            if (selectedVideo == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a video playing track first.", color = Color.Gray)
                }
            } else {
                // Search Header Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search text block...", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF59E0B),
                        focusedLabelColor = Color(0xFFF59E0B)
                    ),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) }
                )

                val filteredSubtitles = subtitles.filter {
                    it.text.contains(searchQuery, ignoreCase = true)
                }

                if (filteredSubtitles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No subtitles captions match your query.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(filteredSubtitles) { index, block ->
                            EditorBlockRow(
                                block = block,
                                onTextChange = { txt ->
                                    viewModel.updateBlockText(block, txt)
                                },
                                onShiftTime = { shiftMs ->
                                    viewModel.adjustBlockTiming(block, shiftMs)
                                },
                                onMergeNext = {
                                    val next = filteredSubtitles.getOrNull(index + 1)
                                    if (next != null) {
                                        viewModel.mergeSubtitleBlocks(block, next)
                                    }
                                },
                                onSplit = {
                                    val splitIndex = block.text.length / 2
                                    viewModel.splitSubtitleBlock(block, splitIndex)
                                },
                                onDelete = {
                                    viewModel.deleteBlock(block)
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Replace All Dialog ---
        if (showReplaceDialog) {
            var searchWord by remember { mutableStateOf("") }
            var replaceWord by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showReplaceDialog = false },
                title = { Text("Search & Replace", color = Color.White) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchWord,
                            onValueChange = { searchWord = it },
                            label = { Text("Word to Find") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFF59E0B)),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = replaceWord,
                            onValueChange = { replaceWord = it },
                            label = { Text("Replace text with") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFF59E0B)),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (searchWord.isNotEmpty()) {
                                subtitles.forEach { block ->
                                    if (block.text.contains(searchWord)) {
                                        val updatedText = block.text.replace(searchWord, replaceWord)
                                        viewModel.updateBlockText(block, updatedText)
                                    }
                                }
                                showReplaceDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                    ) {
                        Text("Replace All", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplaceDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun EditorBlockRow(
    block: SubtitleBlock,
    onTextChange: (String) -> Unit,
    onShiftTime: (Long) -> Unit,
    onMergeNext: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit
) {
    var editedText by remember(block.text) { mutableStateOf(block.text) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Timing Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${block.formattedTime(block.startTimeMs)} --> ${block.formattedTime(block.endTimeMs)}",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (block.speaker != null) {
                    Badge(containerColor = Color(0xFFEC4899)) {
                        Text(block.speaker, fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Text Area Input Fields
            OutlinedTextField(
                value = editedText,
                onValueChange = {
                    editedText = it
                    onTextChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF59E0B),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                ),
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Triggers Controller Dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shift timing
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { onShiftTime(-100L) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("-100ms", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { onShiftTime(100L) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("+100ms", fontSize = 10.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onSplit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.ContentCut, contentDescription = "Split Block", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }

                    IconButton(onClick = onMergeNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MergeType, contentDescription = "Merge with next", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete Block", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
