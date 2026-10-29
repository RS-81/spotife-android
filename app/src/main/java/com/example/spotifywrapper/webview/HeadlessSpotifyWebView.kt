package com.example.spotifywrapper.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.spotifywrapper.data.SpotifyRepository

@SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
class HeadlessSpotifyWebView(
    private val context: Context,
    private val repository: SpotifyRepository,
    private val onReadyStateChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "HeadlessSpotifyWebView"
        private const val TARGET_URL = "https://open.spotify.com/"
        private const val LINUX_DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    private val scriptHandlers = mutableListOf<ScriptHandler>()

    val webView: WebView = WebView(context.applicationContext).apply {
        visibility = View.GONE
        layoutParams = android.view.ViewGroup.LayoutParams(1920, 1080)
        configureSettings()
        configureClients()
        repository.attachWebView(this)
        installDocumentStartScripts()
    }

    private fun WebView.configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = LINUX_DESKTOP_UA
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@configureSettings, true)
        }
    }

    private fun WebView.installDocumentStartScripts() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "DOCUMENT_START_SCRIPT feature is not supported on this WebView version.")
            return
        }

        val spoofingScript = """
            (function() {
                'use strict';
                const defineGetter = (proto, prop, value) => {
                    try {
                        Object.defineProperty(proto, prop, {
                            get: () => value,
                            configurable: true,
                            enumerable: true
                        });
                    } catch (e) {}
                };

                if (typeof Navigator !== 'undefined' && Navigator.prototype) {
                    defineGetter(Navigator.prototype, 'userAgent', '$LINUX_DESKTOP_UA');
                    defineGetter(Navigator.prototype, 'platform', 'Linux x86_64');
                    defineGetter(Navigator.prototype, 'vendor', 'Google Inc.');
                    defineGetter(Navigator.prototype, 'maxTouchPoints', 0);
                    defineGetter(Navigator.prototype, 'hardwareConcurrency', 8);
                    defineGetter(Navigator.prototype, 'deviceMemory', 8);
                    defineGetter(Navigator.prototype, 'userAgentData', {
                        brands: [
                            { brand: 'Not/A)Brand', version: '8' },
                            { brand: 'Chromium', version: '126' },
                            { brand: 'Google Chrome', version: '126' }
                        ],
                        mobile: false,
                        platform: 'Linux',
                        getHighEntropyValues: async function() {
                            return {
                                architecture: 'x86',
                                bitness: '64',
                                brands: this.brands,
                                mobile: false,
                                model: '',
                                platform: 'Linux',
                                platformVersion: '6.5.0',
                                uaFullVersion: '126.0.0.0'
                            };
                        }
                    });
                }

                if (typeof Screen !== 'undefined' && Screen.prototype) {
                    defineGetter(Screen.prototype, 'width', 1920);
                    defineGetter(Screen.prototype, 'height', 1080);
                    defineGetter(Screen.prototype, 'availWidth', 1920);
                    defineGetter(Screen.prototype, 'availHeight', 1040);
                    defineGetter(Screen.prototype, 'colorDepth', 24);
                    defineGetter(Screen.prototype, 'pixelDepth', 24);
                }

                defineGetter(Document.prototype, 'hidden', false);
                defineGetter(Document.prototype, 'visibilityState', 'visible');

                const activateWebBrowserDevice = () => {
                    const candidates = document.querySelectorAll('button, [role="button"], li, div');
                    for (const el of candidates) {
                        const text = (el.textContent || '').trim();
                        if (text.includes('This web browser')) {
                            const clickable = el.closest('button, [role="button"], li') || el;
                            if (!clickable.dataset.autoActivated) {
                                clickable.dataset.autoActivated = 'true';
                                clickable.click();
                            }
                            break;
                        }
                    }
                };

                const observer = new MutationObserver(() => activateWebBrowserDevice());
                window.addEventListener('DOMContentLoaded', () => {
                    observer.observe(document.body || document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                    activateWebBrowserDevice();
                });
            })();
        """.trimIndent()

        val allowedOrigins = setOf("https://*.spotify.com")
        scriptHandlers.add(
            WebViewCompat.addDocumentStartJavaScript(this, spoofingScript, allowedOrigins)
        )

        runCatching {
            val bridgeJs = context.assets.open("SpotifyBackendBridge.js")
                .bufferedReader()
                .use { it.readText() }
            scriptHandlers.add(
                WebViewCompat.addDocumentStartJavaScript(this, bridgeJs, allowedOrigins)
            )
        }.onFailure { e ->
            Log.e(TAG, "Failed loading SpotifyBackendBridge.js from assets", e)
        }
    }

    private fun WebView.configureClients() {
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val scheme = request.url.scheme ?: return false
                return scheme != "https" && scheme != "http"
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                onReadyStateChanged(true)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val granted = request.resources.filter {
                    it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID ||
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                }.toTypedArray()

                if (granted.isNotEmpty()) {
                    request.grant(granted)
                } else {
                    request.deny()
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(TAG, "[JS:${consoleMessage.lineNumber()}] ${consoleMessage.message()}")
                return true
            }
        }
    }

    fun loadPlayer() {
        webView.loadUrl(TARGET_URL)
    }

    fun triggerDeviceMenuAndActivate() {
        val js = """
            (function() {
                const connectBtn = document.querySelector('[data-testid="device-picker-button"], [aria-label*="Connect to a device"]');
                if (connectBtn) connectBtn.click();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun togglePlayPause() {
        repository.togglePlayPause()
    }

    fun release() {
        scriptHandlers.forEach { it.remove() }
        scriptHandlers.clear()
        repository.detachWebView()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.destroy()
    }
}
