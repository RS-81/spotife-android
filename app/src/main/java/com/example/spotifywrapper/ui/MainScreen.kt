package com.example.spotifywrapper.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.spotifywrapper.ui.screens.FloatingMiniPlayerPill
import com.example.spotifywrapper.ui.screens.FullScreenNowPlayingModal
import com.example.spotifywrapper.ui.screens.HomeScreen
import com.example.spotifywrapper.ui.screens.LibraryScreen
import com.example.spotifywrapper.ui.screens.SearchScreen
import com.example.spotifywrapper.webview.HeadlessSpotifyWebView

private enum class MainTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Your Library")
}

@Composable
fun MainScreen(
    viewModel: SpotifyViewModel,
    headlessWebView: HeadlessSpotifyWebView
) {
    val playerState by viewModel.playerState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var isFullScreenPlayerOpen by rememberSaveable { mutableStateOf(false) }
    var showLoginWebView by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {

        AndroidView(
            factory = {
                headlessWebView.webView.apply {
                    (parent as? ViewGroup)?.removeView(this)
                    visibility = android.view.View.VISIBLE
                }
            },
            modifier = if (showLoginWebView) {
                Modifier.fillMaxSize()
            } else {
                Modifier.size(1.dp).alpha(0f)
            }
        )

        if (!showLoginWebView) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (!playerState.hasValidToken) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1DB954))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            TextButton(onClick = { showLoginWebView = true }) {
                                Text("Sign in to Spotify Web Player", color = Color.Black)
                            }
                        }
                    }
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xEE121212))
                    ) {
                        FloatingMiniPlayerPill(
                            playerState = playerState,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            onActivateDevice = headlessWebView::triggerDeviceMenuAndActivate,
                            onExpand = { isFullScreenPlayerOpen = true }
                        )

                        NavigationBar(
                            containerColor = Color(0xFF121212),
                            contentColor = Color.White
                        ) {
                            NavigationBarItem(
                                selected = currentTab == MainTab.HOME,
                                onClick = { currentTab = MainTab.HOME },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                colors = navItemColors()
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.SEARCH,
                                onClick = { currentTab = MainTab.SEARCH },
                                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                label = { Text("Search") },
                                colors = navItemColors()
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.LIBRARY,
                                onClick = { currentTab = MainTab.LIBRARY },
                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                                label = { Text("Your Library") },
                                colors = navItemColors()
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (currentTab) {
                        MainTab.HOME -> HomeScreen(
                            playlists = playlists,
                            onItemClick = viewModel::playItem
                        )
                        MainTab.SEARCH -> SearchScreen(
                            query = searchQuery,
                            searchResults = searchResults,
                            onQueryChange = viewModel::onSearchQueryChanged,
                            onItemClick = viewModel::playItem
                        )
                        MainTab.LIBRARY -> LibraryScreen(
                            playlists = playlists,
                            onItemClick = viewModel::playItem
                        )
                    }
                }
            }

            FullScreenNowPlayingModal(
                visible = isFullScreenPlayerOpen,
                playerState = playerState,
                onDismiss = { isFullScreenPlayerOpen = false },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                onSeekTo = viewModel::seekTo
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                TextButton(
                    onClick = { showLoginWebView = false },
                    modifier = Modifier.background(Color(0xCC000000))
                ) {
                    Text("Done Sign-In", color = Color(0xFF1DB954))
                }
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color.White,
    selectedTextColor = Color.White,
    unselectedIconColor = Color(0xFF8E8E8E),
    unselectedTextColor = Color(0xFF8E8E8E),
    indicatorColor = Color.Transparent
)
