package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SrtEditorScreen
import com.example.ui.screens.VideoLibraryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SubtitlePlayerViewModel
import com.example.ui.viewmodel.SubtitlePlayerViewModelFactory
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private var shouldEnterPiP = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldEnterPiP) {
            val params = android.app.PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(params)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = application as SubtitleApplication
            val factory = remember {
                SubtitlePlayerViewModelFactory(
                    app,
                    app.videoRepository,
                    app.subtitleRepository
                )
            }

            // Initialize main ViewModel
            val viewModel: SubtitlePlayerViewModel = viewModel(factory = factory)
            val themeMode by viewModel.themeMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode) {
                // Track active visual screen route
                var currentScreen by remember { mutableStateOf("HOME") }
                val selectedVideo by viewModel.selectedVideo.collectAsState()

                LaunchedEffect(currentScreen) {
                    shouldEnterPiP = currentScreen == "PLAYER"
                }

                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? android.app.Activity

                BackHandler(enabled = true) {
                    when (currentScreen) {
                        "PLAYER" -> {
                            currentScreen = "LIBRARY"
                        }
                        "SETTINGS", "EXPORT" -> {
                            currentScreen = "PLAYER"
                        }
                        else -> {
                            activity?.finish()
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        // Display bottom nav bar only for root screens
                        val showNav = currentScreen in listOf("HOME", "LIBRARY", "GENERATOR", "EDITOR")
                        AnimatedVisibility(
                            visible = showNav,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            NavigationBar(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == "HOME",
                                    onClick = { currentScreen = "HOME" },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF6366F1),
                                        selectedTextColor = Color(0xFF6366F1),
                                        indicatorColor = Color(0xFF334155),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "LIBRARY",
                                    onClick = { currentScreen = "LIBRARY" },
                                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                                    label = { Text("Library", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF10B981),
                                        selectedTextColor = Color(0xFF10B981),
                                        indicatorColor = Color(0xFF334155),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "GENERATOR",
                                    onClick = { currentScreen = "GENERATOR" },
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Transcription") },
                                    label = { Text("AI Gen", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFFEC4899),
                                        selectedTextColor = Color(0xFFEC4899),
                                        indicatorColor = Color(0xFF334155),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "EDITOR",
                                    onClick = { currentScreen = "EDITOR" },
                                    icon = { Icon(Icons.Default.Edit, contentDescription = "Subtitle Editor") },
                                    label = { Text("Editor", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFFF59E0B),
                                        selectedTextColor = Color(0xFFF59E0B),
                                        indicatorColor = Color(0xFF334155),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray
                                    )
                                )
                                if (selectedVideo != null) {
                                    NavigationBarItem(
                                        selected = currentScreen == "PLAYER",
                                        onClick = { currentScreen = "PLAYER" },
                                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Now Playing") },
                                        label = { Text("Player", fontSize = 11.sp) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFFEC4899),
                                            selectedTextColor = Color(0xFFEC4899),
                                            indicatorColor = Color(0xFF334155),
                                            unselectedIconColor = Color.White,
                                            unselectedTextColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            "HOME" -> HomeScreen(
                                viewModel = viewModel,
                                onNavigate = { currentScreen = it }
                            )
                            "LIBRARY" -> VideoLibraryScreen(
                                viewModel = viewModel,
                                onNavigate = { currentScreen = it }
                            )
                            "PLAYER" -> PlayerScreen(
                                viewModel = viewModel,
                                onNavigate = { currentScreen = it }
                            )
                            "GENERATOR" -> GeneratorScreen(
                                viewModel = viewModel,
                                onNavigate = { currentScreen = it }
                            )
                            "EDITOR" -> SrtEditorScreen(
                                viewModel = viewModel,
                                onNavigate = { currentScreen = it }
                            )
                            "SETTINGS" -> SettingsScreen(
                                viewModel = viewModel,
                                onNavigate = { currentScreen = it }
                            )
                            "EXPORT" -> ExportScreen(
                                viewModel = viewModel,
                                onNavigate = { currentScreen = it }
                            )
                        }
                    }
                }
            }
        }
    }
}
