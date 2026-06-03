package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubtitlePosition
import com.example.ui.viewmodel.SubtitlePlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SubtitlePlayerViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val colorsAvailable = listOf(
        "#FFFFFF" to "White",
        "#FFFF00" to "Yellow",
        "#00FF00" to "Green",
        "#00FFFF" to "Cyan",
        "#FF0000" to "Red",
        "#FF00FF" to "Magenta"
    )

    val backgroundColors = listOf(
        "#000000" to "Black",
        "#111827" to "Dark Grey",
        "#1E3A8A" to "Blue Tint",
        "#312E81" to "Violet Tint"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Subtitle Formatting", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B)),
                navigationIcon = {
                    IconButton(onClick = { onNavigate("PLAYER") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Real-time Live Preview Screen Panel ---
                item {
                    Text("Real-Time Preview Panel", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background movie scene simulation
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF020617)) // Deep space view mock
                        ) {
                            // Subtitle layer overlay alignment
                            val verticalAlign = when (subtitleStyle.position) {
                                SubtitlePosition.TOP -> Alignment.TopCenter
                                SubtitlePosition.CENTER -> Alignment.Center
                                SubtitlePosition.BOTTOM -> Alignment.BottomCenter
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = if (subtitleStyle.position == SubtitlePosition.TOP) 12.dp else 4.dp,
                                        bottom = if (subtitleStyle.position == SubtitlePosition.BOTTOM) 12.dp else 4.dp
                                    ),
                                contentAlignment = verticalAlign
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            Color(android.graphics.Color.parseColor(subtitleStyle.backgroundColorHex))
                                                .copy(alpha = subtitleStyle.backgroundOpacity)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val font = when (subtitleStyle.fontFamily) {
                                        "Monospace" -> FontFamily.Monospace
                                        "Serif" -> FontFamily.Serif
                                        "SansSerif" -> FontFamily.SansSerif
                                        else -> FontFamily.Default
                                    }
                                    Text(
                                        "Preview Subtitle Text Styles",
                                        color = Color(android.graphics.Color.parseColor(subtitleStyle.textColorHex)),
                                        fontSize = subtitleStyle.fontSizeSp.sp,
                                        fontFamily = font,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Font Size Adjustment Slider ---
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Caption Text Size", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${subtitleStyle.fontSizeSp.toInt()} sp", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Slider(
                                value = subtitleStyle.fontSizeSp,
                                onValueChange = { viewModel.updateSubtitleStyle(fontSizeSp = it) },
                                valueRange = 12f..32f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF3B82F6),
                                    activeTrackColor = Color(0xFF3B82F6)
                                )
                            )
                        }
                    }
                }

                // --- Font Family Choice Row ---
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Font Face Family", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("SansSerif", "Monospace", "Serif").forEach { font ->
                                    Button(
                                        onClick = { viewModel.updateSubtitleStyle(fontFamily = font) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (subtitleStyle.fontFamily == font) Color(0xFF3B82F6) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(font, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Text Color Palette Selector ---
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Foreground Text Color", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                colorsAvailable.forEach { (hex, name) ->
                                    val isSelected = subtitleStyle.textColorHex == hex
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF3B82F6) else Color.Gray.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.updateSubtitleStyle(textColorHex = hex) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (hex == "#FFFFFF" || hex == "#FFFF00") Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Background Opacity Choice Slider ---
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Container Opacity", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${(subtitleStyle.backgroundOpacity * 100).toInt()}%", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Slider(
                                value = subtitleStyle.backgroundOpacity,
                                onValueChange = { viewModel.updateSubtitleStyle(backgroundOpacity = it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF3B82F6),
                                    activeTrackColor = Color(0xFF3B82F6)
                                )
                            )
                        }
                    }
                }

                // --- Vertical Alignment Position buttons ---
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Subtitle Position", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SubtitlePosition.values().forEach { pos ->
                                    Button(
                                        onClick = { viewModel.updateSubtitleStyle(position = pos) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (subtitleStyle.position == pos) Color(0xFF3B82F6) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(pos.name, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // --- App Theme Switcher ---
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("App Theme Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                    Button(
                                        onClick = { viewModel.updateThemeMode(mode) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (themeMode == mode) Color(0xFF3B82F6) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(mode, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
