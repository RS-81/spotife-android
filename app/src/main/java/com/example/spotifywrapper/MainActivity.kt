package com.example.spotifywrapper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    // Tracks whether the user has completed mobile login and switched to Desktop Mode
    private var isDesktopModeActive by mutableStateOf(false)

    // Toggle between Mobile-Fitted Reflow (100% phone width, big text) vs Chrome 980px Desktop Fit
    private var useCompactMobileReflow by mutableStateOf(true)

    private var trackTitle by mutableStateOf("Spotify Web Player")
    private var trackArtist by mutableStateOf("Log in using Mobile Mode first — auto-switches to Desktop after login")
    private var isPlaying by mutableStateOf(false)
    private var progressFraction by mutableFloatStateOf(0f)

    // 1. Standard Android Mobile User-Agent (used during Login so login fits the phone screen)
    private val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    // 2. Exact Chrome Android "Desktop Site" User-Agent (Linux x86_64, used once Login completes)
    private val linuxDesktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotifyMainScreen()
        }
    }

    /**
     * Checks if Spotify's authentication cookie (sp_dc) is present, meaning login is complete.
     */
    private fun hasSpotifyLoginCookie(): Boolean {
        val openCookies = CookieManager.getInstance().getCookie("https://open.spotify.com") ?: ""
        val accountCookies = CookieManager.getInstance().getCookie("https://accounts.spotify.com") ?: ""
        return openCookies.contains("sp_dc=") || accountCookies.contains("sp_dc=")
    }

    /**
     * Switches the WebView between Mobile Login Mode and Fitted Linux Desktop Mode.
     */
    private fun applyBrowserMode(wv: WebView, desktop: Boolean, reloadPage: Boolean) {
        isDesktopModeActive = desktop
        wv.settings.apply {
            if (desktop) {
                userAgentString = linuxDesktopUserAgent
                useWideViewPort = true
                loadWithOverviewMode = true
            } else {
                userAgentString = mobileUserAgent
                useWideViewPort = true
                loadWithOverviewMode = false
            }
        }
        if (reloadPage) {
            Toast.makeText(
                this,
                if (desktop) "Login complete! Switched to Desktop Mode" else "Switched to Mobile Login Mode",
                Toast.LENGTH_SHORT
            ).show()
            wv.loadUrl("https://open.spotify.com/")
        }
    }

    @Composable
    private fun SpotifyMainScreen() {
        BackHandler {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
            } else {
                moveTaskToBack(true)
            }
        }

        Scaffold(
            containerColor = Color(0xFF121212),
            topBar = {
                TopControlStrip(
                    isDesktopMode = isDesktopModeActive,
                    isCompactReflow = useCompactMobileReflow,
                    onToggleDesktopMode = {
                        webView?.let { wv ->
                            applyBrowserMode(wv, !isDesktopModeActive, reloadPage = true)
                        }
                    },
                    onToggleLayoutFit = {
                        useCompactMobileReflow = !useCompactMobileReflow
                        injectScreenFitFix()
                    },
                    onActivateAudio = { activateAudioDevice() },
                    onReload = { webView?.reload() }
                )
            },
            bottomBar = {
                if (isDesktopModeActive) {
                    BottomPlayBar(
                        title = trackTitle,
                        artist = trackArtist,
                        isPlaying = isPlaying,
                        progress = progressFraction,
                        onPlayPause = { executeJs("window.__spTogglePlayPause && window.__spTogglePlayPause();") },
                        onNext = { executeJs("window.__spNext && window.__spNext();") },
                        onPrev = { executeJs("window.__spPrev && window.__spPrev();") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF121212))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        createConfiguredWebView(context).also { webView = it }
                    }
                )
            }
        }
    }

    @Composable
    private fun TopControlStrip(
        isDesktopMode: Boolean,
        isCompactReflow: Boolean,
        onToggleDesktopMode: () -> Unit,
        onToggleLayoutFit: () -> Unit,
        onActivateAudio: () -> Unit,
        onReload: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181818))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDesktopMode) Color(0xFF1DB954) else Color(0xFF333333)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.clickable { onToggleDesktopMode() }
            ) {
                Text(
                    text = if (isDesktopMode) "Desktop Mode: ON" else "Mobile Login Mode",
                    color = if (isDesktopMode) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDesktopMode) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.clickable { onToggleLayoutFit() }
                    ) {
                        Text(
                            text = if (isCompactReflow) "Fit: Mobile Width" else "Fit: Chrome 980px",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onActivateAudio,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = "Activate Audio",
                            tint = Color(0xFF1DB954)
                        )
                    }
                }

                IconButton(
                    onClick = onReload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = Color.White
                    )
                }
            }
        }
    }

    @Composable
    private fun BottomPlayBar(
        title: String,
        artist: String,
        isPlaying: Boolean,
        progress: Float,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrev: () -> Unit
    ) {
        Surface(
            color = Color(0xFF202020),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(0xFF1DB954),
                    trackColor = Color(0xFF383838)
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
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF1DB954), CircleShape)
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(context: Context): WebView {
        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val alreadyLoggedIn = hasSpotifyLoginCookie()

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Start in Mobile Mode if not logged in; start in Desktop Mode if already logged in
        applyBrowserMode(wv, desktop = alreadyLoggedIn, reloadPage = false)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.addJavascriptInterface(SpotifyBridge(), "AndroidBridge")

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                wv,
                SAFE_LINUX_SPOOF_JS,
                setOf("*")
            )
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // Block deep links that try to kick out to the Play Store or native Spotify app
                if (url.startsWith("intent://") || url.startsWith("market://") || url.startsWith("spotify:")) {
                    return true
                }

                // Always keep accounts.spotify.com login pages in Mobile Mode
                if (url.contains("accounts.spotify.com") && isDesktopModeActive) {
                    view?.let { applyBrowserMode(it, desktop = false, reloadPage = false) }
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val currentUrl = url ?: ""
                CookieManager.getInstance().flush()

                // AUTO-SWITCH: As soon as Mobile Login completes and redirects back to open.spotify.com
                // with the sp_dc session cookie, switch to Desktop Mode and refresh!
                if (!isDesktopModeActive &&
                    currentUrl.contains("open.spotify.com") &&
                    !currentUrl.contains("accounts.spotify.com") &&
                    hasSpotifyLoginCookie()
                ) {
                    view?.let {
                        applyBrowserMode(it, desktop = true, reloadPage = true)
                    }
                    return
                }

                // Once in Desktop Mode on open.spotify.com, inject the Screen Fit & Bridge script
                if (isDesktopModeActive && currentUrl.contains("open.spotify.com")) {
                    injectScreenFitFix()
                }
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

        if (alreadyLoggedIn) {
            wv.loadUrl("https://open.spotify.com/")
        } else {
            wv.loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
        }

        return wv
    }

    /**
     * Fixes the "Monitor Wide / Half of Phone Cut Off" problem on open.spotify.com!
     * - Fit: Mobile Width (Default): Removes Spotify's 800px+ min-width so the desktop interface
     *   fits 100% inside the phone screen width at 1.0x readable mobile size with zero cut-off.
     * - Fit: Chrome 980px: Sets <meta name="viewport" content="width=980"> (matching Chrome Mobile's
     *   native "Desktop site" viewport) so the full desktop layout scales proportionally into the phone width.
     */
    private fun injectScreenFitFix() {
        val compact = if (useCompactMobileReflow) "true" else "false"
        val script = """
            (function() {
                var isCompact = $compact;
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }

                var style = document.getElementById('sp-android-fit-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'sp-android-fit-style';
                    document.head.appendChild(style);
                }

                if (isCompact) {
                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes');
                    style.innerHTML = `
                        html, body, #main, .Root__top-container {
                            min-width: 0 !important;
                            width: 100vw !important;
                            max-width: 100vw !important;
                            overflow-x: hidden !important;
                        }
                        aside[aria-label*="Now Playing"],
                        a[href*="/download"],
                        button[aria-label*="Upgrade"] {
                            display: none !important;
                        }
                        #Desktop_LeftSidebar_Id, nav[aria-label="Main"] {
                            width: 68px !important;
                            min-width: 68px !important;
                            max-width: 68px !important;
                        }
                        .Root__main-view, main {
                            min-width: 0 !important;
                            width: calc(100vw - 72px) !important;
                            max-width: calc(100vw - 72px) !important;
                        }
                        div[data-testid="now-playing-bar"], footer {
                            min-width: 0 !important;
                            width: 100vw !important;
                            max-width: 100vw !important;
                        }
                    `;
                } else {
                    meta.setAttribute('content', 'width=980, user-scalable=yes');
                    style.innerHTML = `
                        html, body, #main, .Root__top-container {
                            min-width: 980px !important;
                            width: 980px !important;
                            max-width: 980px !important;
                        }
                        aside[aria-label*="Now Playing"] {
                            display: none !important;
                        }
                    `;
                }

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

                if (!window.__spTrackerStarted) {
                    window.__spTrackerStarted = true;
                    setInterval(function() {
                        try {
                            var titleEl = document.querySelector('[data-testid="context-item-info-title"], [data-testid="now-playing-widget"] a');
                            var artistEl = document.querySelector('[data-testid="context-item-info-subtitles"], [data-testid="context-item-info-artist"]');
                            var playBtn = document.querySelector('[data-testid="control-button-playpause"]');

                            var title = titleEl ? (titleEl.innerText || '').trim() : '';
                            var artist = artistEl ? (artistEl.innerText || '').trim() : '';
                            var aria = playBtn ? (playBtn.getAttribute('aria-label') || '').toLowerCase() : '';
                            var playing = aria.indexOf('pause') !== -1;

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
                                window.AndroidBridge.onTrackUpdate(title, artist, playing, permille);
                            }
                        } catch (e) {}
                    }, 1000);
                }
            })();
        """.trimIndent()
        executeJs(script)
    }

    private fun activateAudioDevice() {
        Toast.makeText(this, "Activating Web Player audio on this phone...", Toast.LENGTH_SHORT).show()
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

    private fun executeJs(script: String) {
        webView?.evaluateJavascript(script, null)
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
    }

    companion object {
        /**
         * Spoofs Linux Desktop platform ONLY when Desktop Mode is active, and NEVER fakes
         * a 1920px monitor screen width (faking 1920px is what caused half the phone screen to be cut off!).
         */
        private val SAFE_LINUX_SPOOF_JS = """
            (function() {
                try {
                    if (navigator.userAgent.indexOf('X11; Linux x86_64') === -1) return;
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
                } catch (e) {}
            })();
        """.trimIndent()
    }
}
