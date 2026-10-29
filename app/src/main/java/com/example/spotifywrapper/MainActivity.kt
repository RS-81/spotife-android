package com.example.spotifywrapper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray

data class SpotifyItem(
    val index: Int,
    val title: String,
    val subtitle: String
)

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    private var trackTitle by mutableStateOf("Loading Spotify Backend...")
    private var trackArtist by mutableStateOf("Tap 'Login / Web' if you need to sign in")
    private var isPlaying by mutableStateOf(false)
    private var progressFraction by mutableFloatStateOf(0f)
    private var itemsList = mutableStateListOf<SpotifyItem>()
    private var currentTab by mutableStateOf("home")
    private var showWebView by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")

    private val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotifyAppUI()
        }
    }

    @Composable
    private fun SpotifyAppUI() {
        BackHandler {
            if (showWebView && webView?.canGoBack() == true) {
                webView?.goBack()
            } else if (showWebView) {
                showWebView = false
            } else {
                moveTaskToBack(true)
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
            // Headless backend WebView (revealed only if user taps 'Login / Web')
            AndroidView(
                modifier = if (showWebView) Modifier.fillMaxSize() else Modifier.size(1.dp),
                factory = { context ->
                    createAndConfigureWebView(context).also {
                        webView = it
                    }
                }
            )

            // Native Spotify Mobile Layout
            if (!showWebView) {
                Scaffold(
                    containerColor = Color(0xFF121212),
                    topBar = {
                        TopSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { query ->
                                if (query.isNotBlank()) {
                                    loadSearchInBackend(query)
                                }
                            }
                        )
                    },
                    bottomBar = {
                        Column {
                            MiniPlayerBar(
                                title = trackTitle,
                                artist = trackArtist,
                                isPlaying = isPlaying,
                                progress = progressFraction,
                                onPlayPause = { executeJs("window.__spTogglePlayPause();") },
                                onNext = { executeJs("window.__spNext();") },
                                onPrev = { executeJs("window.__spPrev();") }
                            )

                            BottomNavBar(
                                currentTab = currentTab,
                                onTabSelected = { tab ->
                                    currentTab = tab
                                    when (tab) {
                                        "home" -> executeJs("window.__spLoadHome();")
                                        "search" -> executeJs("window.__spLoadSearch();")
                                        "library" -> executeJs("window.__spExtractList('playlists');")
                                        "web" -> showWebView = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        QuickHeaderRow(
                            onActivateAudio = { activateAudio() },
                            onShowWeb = { showWebView = true }
                        )

                        ItemsListView(
                            items = itemsList,
                            currentTab = currentTab,
                            onItemClick = { item ->
                                if (currentTab == "library") {
                                    executeJs("window.__spClickPlaylist(${item.index});")
                                    Toast.makeText(this@MainActivity, "Opening ${item.title}...", Toast.LENGTH_SHORT).show()
                                } else {
                                    executeJs("window.__spClickTrack(${item.index});")
                                    Toast.makeText(this@MainActivity, "Playing ${item.title}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1DB954)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.clickable { showWebView = false }
                    ) {
                        Text(
                            text = "Back to Native App",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TopSearchBar(
        query: String,
        onQueryChange: (String) -> Unit,
        onSearch: (String) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181818))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = { Text("Search songs, artists, playlists...", fontSize = 13.sp, color = Color(0xFF888888)) },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF282828),
                    unfocusedContainerColor = Color(0xFF282828),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1DB954),
                    unfocusedBorderColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onSearch(query) },
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1DB954), CircleShape)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Black)
            }
        }
    }

    @Composable
    private fun QuickHeaderRow(onActivateAudio: () -> Unit, onShowWeb: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Spotify Mobile",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onActivateAudio() }
                ) {
                    Text(
                        text = "Connect Audio",
                        color = Color(0xFF1DB954),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onShowWeb() }
                ) {
                    Text(
                        text = "Login / Web",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun ItemsListView(
        items: List<SpotifyItem>,
        currentTab: String,
        onItemClick: (SpotifyItem) -> Unit
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading content from Spotify desktop backend...\n\nIf you haven't logged in yet, tap 'Login / Web' at the top right to sign in.",
                    color = Color(0xFFB3B3B3),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(items, key = { "${it.index}_${it.title}" }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onItemClick(item) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF282828)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (currentTab == "library") Icons.Default.LibraryMusic else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF1DB954)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.subtitle.isNotBlank()) item.subtitle else if (currentTab == "library") "Playlist" else "Track",
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MiniPlayerBar(
        title: String,
        artist: String,
        isPlaying: Boolean,
        progress: Float,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrev: () -> Unit
    ) {
        Surface(
            color = Color(0xFF222222),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF1DB954),
                    trackColor = Color(0xFF333333)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist,
                            color = Color(0xFFB3B3B3),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White)
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }
    }

    @Composable
    private fun BottomNavBar(currentTab: String, onTabSelected: (String) -> Unit) {
        NavigationBar(
            containerColor = Color(0xFF121212),
            contentColor = Color.White
        ) {
            NavigationBarItem(
                selected = currentTab == "home",
                onClick = { onTabSelected("home") },
                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1DB954),
                    selectedTextColor = Color(0xFF1DB954),
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFFB3B3B3),
                    unselectedTextColor = Color(0xFFB3B3B3)
                )
            )
            NavigationBarItem(
                selected = currentTab == "search",
                onClick = { onTabSelected("search") },
                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                label = { Text("Search", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1DB954),
                    selectedTextColor = Color(0xFF1DB954),
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFFB3B3B3),
                    unselectedTextColor = Color(0xFFB3B3B3)
                )
            )
            NavigationBarItem(
                selected = currentTab == "library",
                onClick = { onTabSelected("library") },
                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                label = { Text("Your Library", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1DB954),
                    selectedTextColor = Color(0xFF1DB954),
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFFB3B3B3),
                    unselectedTextColor = Color(0xFFB3B3B3)
                )
            )
            NavigationBarItem(
                selected = currentTab == "web",
                onClick = { onTabSelected("web") },
                icon = { Icon(Icons.Default.Web, contentDescription = "Web") },
                label = { Text("Login / Web", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1DB954),
                    selectedTextColor = Color(0xFF1DB954),
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFFB3B3B3),
                    unselectedTextColor = Color(0xFFB3B3B3)
                )
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createAndConfigureWebView(context: Context): WebView {
        val wv = WebView(context)
        val settings = wv.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = desktopUserAgent
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.addJavascriptInterface(SpotifyBridge(), "AndroidBridge")

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                wv,
                SAFE_LINUX_DESKTOP_SPOOF_JS,
                setOf("*")
            )
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("intent://") || url.startsWith("market://") || url.startsWith("spotify:")) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                view?.evaluateJavascript(BRIDGE_EXTRACTOR_JS, null)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request ?: return
                runOnUiThread {
                    try {
                        request.grant(request.resources)
                    } catch (_: Exception) {}
                }
            }
        }

        wv.loadUrl("https://open.spotify.com/")
        return wv
    }

    private fun executeJs(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    private fun loadSearchInBackend(query: String) {
        val encoded = android.net.Uri.encode(query)
        webView?.loadUrl("https://open.spotify.com/search/$encoded")
        currentTab = "search"
    }

    private fun activateAudio() {
        Toast.makeText(this, "Connecting to audio output...", Toast.LENGTH_SHORT).show()
        val script = """
            (function() {
                var btn = document.querySelector('[data-testid="control-button-npv"], [aria-label*="Connect to a device"], button[aria-label*="device"]');
                if (btn) btn.click();
                setTimeout(function() {
                    var items = document.querySelectorAll('button, div[role="button"], li');
                    for (var i = 0; i < items.length; i++) {
                        var txt = (items[i].innerText || '').toLowerCase();
                        if (txt.indexOf('this web browser') !== -1 || txt.indexOf('web player') !== -1) {
                            items[i].click();
                            break;
                        }
                    }
                    var playBtn = document.querySelector('[data-testid="control-button-playpause"]');
                    if (playBtn) playBtn.click();
                }, 600);
            })();
        """.trimIndent()
        executeJs(script)
    }

    inner class SpotifyBridge {
        @JavascriptInterface
        fun onTrackUpdate(title: String, artist: String, isPlayingNow: Boolean, progressPermille: Int) {
            runOnUiThread {
                if (title.isNotBlank()) trackTitle = title
                if (artist.isNotBlank()) trackArtist = artist
                isPlaying = isPlayingNow
                progressFraction = (progressPermille.coerceIn(0, 1000) / 1000f)
            }
        }

        @JavascriptInterface
        fun onItemsExtracted(mode: String, jsonArrayStr: String) {
            runOnUiThread {
                try {
                    val arr = JSONArray(jsonArrayStr)
                    itemsList.clear()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        itemsList.add(
                            SpotifyItem(
                                index = obj.optInt("index", i),
                                title = obj.optString("title", "Untitled"),
                                subtitle = obj.optString("subtitle", "")
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private val SAFE_LINUX_DESKTOP_SPOOF_JS = """
            (function() {
                try {
                    Object.defineProperty(Navigator.prototype, 'platform', {
                        get: function() { return 'Linux x86_64'; },
                        configurable: true
                    });
                    Object.defineProperty(Navigator.prototype, 'maxTouchPoints', {
                        get: function() { return 0; },
                        configurable: true
                    });
                    Object.defineProperty(Navigator.prototype, 'userAgentData', {
                        get: function() {
                            return {
                                brands: [
                                    { brand: 'Chromium', version: '128' },
                                    { brand: 'Google Chrome', version: '128' },
                                    { brand: 'Not;A=Brand', version: '24' }
                                ],
                                mobile: false,
                                platform: 'Linux',
                                getHighEntropyValues: function() {
                                    return Promise.resolve({
                                        architecture: 'x86',
                                        bitness: '64',
                                        mobile: false,
                                        model: '',
                                        platform: 'Linux',
                                        platformVersion: '6.1.0',
                                        uaFullVersion: '128.0.0.0'
                                    });
                                }
                            };
                        },
                        configurable: true
                    });
                    Object.defineProperty(Screen.prototype, 'width', { get: function() { return 1920; }, configurable: true });
                    Object.defineProperty(Screen.prototype, 'height', { get: function() { return 1080; }, configurable: true });
                    Object.defineProperty(Screen.prototype, 'availWidth', { get: function() { return 1920; }, configurable: true });
                    Object.defineProperty(Screen.prototype, 'availHeight', { get: function() { return 1040; }, configurable: true });
                } catch (e) {}
            })();
        """.trimIndent()

        private val BRIDGE_EXTRACTOR_JS = """
            (function() {
                if (window.__spBridgeInitialized) return;
                window.__spBridgeInitialized = true;

                window.__spCurrentMode = 'tracks';

                window.__spLoadHome = function() {
                    window.__spCurrentMode = 'tracks';
                    window.location.href = 'https://open.spotify.com/';
                };

                window.__spLoadSearch = function() {
                    window.__spCurrentMode = 'tracks';
                    window.location.href = 'https://open.spotify.com/search';
                };

                window.__spTogglePlayPause = function() {
                    var btn = document.querySelector('[data-testid="control-button-playpause"]');
                    if (btn) btn.click();
                };

                window.__spNext = function() {
                    var btn = document.querySelector('[data-testid="control-button-skip-forward"]');
                    if (btn) btn.click();
                };

                window.__spPrev = function() {
                    var btn = document.querySelector('[data-testid="control-button-skip-back"]');
                    if (btn) btn.click();
                };

                window.__spExtractList = function(mode) {
                    window.__spCurrentMode = mode || 'tracks';
                    var results = [];
                    if (mode === 'playlists') {
                        var items = document.querySelectorAll('#Desktop_LeftSidebar_Id li, nav[aria-label="Main"] li, [data-testid="rootlist-item"]');
                        for (var i = 0; i < items.length; i++) {
                            var text = (items[i].innerText || '').trim().split('\n');
                            var title = (text[0] || '').trim();
                            var sub = text.slice(1).join(' • ').trim();
                            if (title) {
                                results.push({ index: i, title: title, subtitle: sub });
                            }
                        }
                    } else {
                        var rows = document.querySelectorAll('[data-testid="tracklist-row"], [data-testid="herocard-click-handler"], [data-testid="card-click-handler"]');
                        for (var j = 0; j < rows.length; j++) {
                            var lines = (rows[j].innerText || '').trim().split('\n').filter(Boolean);
                            if (lines.length > 0) {
                                var tTitle = lines.length > 1 && /^\d+$/.test(lines[0]) ? lines[1] : lines[0];
                                var tSub = lines.slice(lines.indexOf(tTitle) + 1).join(' • ');
                                results.push({ index: j, title: tTitle, subtitle: tSub });
                            }
                        }
                    }
                    if (window.AndroidBridge && window.AndroidBridge.onItemsExtracted) {
                        window.AndroidBridge.onItemsExtracted(mode, JSON.stringify(results));
                    }
                };

                window.__spClickPlaylist = function(idx) {
                    var items = document.querySelectorAll('#Desktop_LeftSidebar_Id li, nav[aria-label="Main"] li, [data-testid="rootlist-item"]');
                    if (items[idx]) {
                        var clickable = items[idx].querySelector('a, div[role="button"], button') || items[idx];
                        clickable.click();
                        setTimeout(function() { window.__spExtractList('tracks'); }, 1200);
                    }
                };

                window.__spClickTrack = function(idx) {
                    var rows = document.querySelectorAll('[data-testid="tracklist-row"], [data-testid="herocard-click-handler"], [data-testid="card-click-handler"]');
                    if (rows[idx]) {
                        var playBtn = rows[idx].querySelector('button[aria-label*="Play"], [data-testid="play-button"]');
                        if (playBtn) {
                            playBtn.click();
                        } else {
                            rows[idx].click();
                            var dblEvt = new MouseEvent('dblclick', { bubbles: true, cancelable: true, view: window });
                            rows[idx].dispatchEvent(dblEvt);
                        }
                    }
                };

                setInterval(function() {
                    try {
                        var titleEl = document.querySelector('[data-testid="context-item-info-title"], [data-testid="now-playing-widget"] a');
                        var artistEl = document.querySelector('[data-testid="context-item-info-subtitles"], [data-testid="context-item-info-artist"]');
                        var playBtn = document.querySelector('[data-testid="control-button-playpause"]');

                        var title = titleEl ? (titleEl.innerText || '').trim() : '';
                        var artist = artistEl ? (artistEl.innerText || '').trim() : '';
                        var aria = playBtn ? (playBtn.getAttribute('aria-label') || '').toLowerCase() : '';
                        var isPlaying = aria.indexOf('pause') !== -1;

                        var permille = 0;
                        var posEl = document.querySelector('[data-testid="playback-position"]');
                        var durEl = document.querySelector('[data-testid="playback-duration"]');
                        if (posEl && durEl) {
                            var parseTime = function(s) {
                                var p = (s || '').replace('-', '').split(':').map(Number);
                                return p.length === 2 ? (p[0] * 60 + p[1]) : 0;
                            };
                            var pos = parseTime(posEl.innerText);
                            var dur = parseTime(durEl.innerText);
                            if (dur > 0) permille = Math.round((pos / dur) * 1000);
                        }

                        if (window.AndroidBridge && window.AndroidBridge.onTrackUpdate) {
                            window.AndroidBridge.onTrackUpdate(title, artist, isPlaying, permille);
                        }
                        window.__spExtractList(window.__spCurrentMode || 'tracks');
                    } catch(err) {}
                }, 1500);
            })();
        """.trimIndent()
    }
}
