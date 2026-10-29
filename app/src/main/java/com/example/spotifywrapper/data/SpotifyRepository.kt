package com.example.spotifywrapper.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.spotifywrapper.model.PlayerState
import com.example.spotifywrapper.model.SpotifyItem
import com.example.spotifywrapper.model.SpotifyItemType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SpotifyRepository(
    ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        private const val TAG = "SpotifyRepository"
        const val JS_BRIDGE_NAME = "AndroidSpotifyBridge"
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var attachedWebView: WebView? = null

    private val _bearerToken = MutableStateFlow<String?>(null)
    val bearerToken: StateFlow<String?> = _bearerToken.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyItem>>(emptyList())
    val playlists: StateFlow<List<SpotifyItem>> = _playlists.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SpotifyItem>>(emptyList())
    val searchResults: StateFlow<List<SpotifyItem>> = _searchResults.asStateFlow()

    fun attachWebView(webView: WebView) {
        attachedWebView = webView
        webView.addJavascriptInterface(JsBridgeReceiver(), JS_BRIDGE_NAME)
    }

    fun detachWebView() {
        attachedWebView?.removeJavascriptInterface(JS_BRIDGE_NAME)
        attachedWebView = null
    }

    fun playUri(uri: String) {
        val escapedUri = JSONObject.quote(uri)
        evaluateJs("window.SpotifyBridgeController && window.SpotifyBridgeController.playUri($escapedUri);")
    }

    fun togglePlayPause() {
        evaluateJs("window.SpotifyBridgeController && window.SpotifyBridgeController.togglePlayPause();")
    }

    fun skipNext() {
        evaluateJs("window.SpotifyBridgeController && window.SpotifyBridgeController.skipNext();")
    }

    fun seekTo(positionMs: Long) {
        evaluateJs("window.SpotifyBridgeController && window.SpotifyBridgeController.seekTo($positionMs);")
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        val escapedQuery = JSONObject.quote(query)
        evaluateJs("window.SpotifyBridgeController && window.SpotifyBridgeController.searchCatalog($escapedQuery);")
    }

    private fun evaluateJs(script: String) {
        mainHandler.post {
            attachedWebView?.evaluateJavascript(script, null)
        }
    }

    inner class JsBridgeReceiver {
        @JavascriptInterface
        fun onTokenCaptured(token: String) {
            if (token.isNotBlank()) {
                _bearerToken.value = token
                _playerState.update { it.copy(hasValidToken = true) }
            }
        }

        @JavascriptInterface
        fun onPlayerStateChanged(jsonPayload: String) {
            scope.launch {
                runCatching {
                    val obj = JSONObject(jsonPayload)
                    _playerState.update { current ->
                        current.copy(
                            title = obj.optString("title", current.title),
                            artist = obj.optString("artist", current.artist),
                            artworkUrl = obj.optString("artworkUrl", current.artworkUrl),
                            isPlaying = obj.optBoolean("isPlaying", current.isPlaying),
                            positionMs = obj.optLong("positionMs", current.positionMs),
                            durationMs = obj.optLong("durationMs", current.durationMs)
                        )
                    }
                }.onFailure { e -> Log.e(TAG, "Failed parsing player state", e) }
            }
        }

        @JavascriptInterface
        fun onPathfinderResponse(jsonPayload: String) {
            scope.launch {
                runCatching {
                    val root = JSONObject(jsonPayload)
                    val data = root.optJSONObject("data")?.optJSONObject("data") ?: return@launch
                    parsePathfinderLibraryAndPlaylists(data)
                    parsePathfinderSearch(data)
                }
            }
        }

        @JavascriptInterface
        fun onSearchResults(jsonPayload: String) {
            scope.launch {
                runCatching {
                    val root = JSONObject(jsonPayload)
                    val parsed = parseWebApiSearchResults(root)
                    if (parsed.isNotEmpty()) {
                        _searchResults.value = parsed
                    }
                }
            }
        }

        @JavascriptInterface
        fun onPlayerApiState(jsonPayload: String) {
            scope.launch {
                runCatching {
                    val root = JSONObject(jsonPayload)
                    val item = root.optJSONObject("item") ?: return@launch
                    val title = item.optString("name")
                    val durationMs = item.optLong("duration_ms")
                    val progressMs = root.optLong("progress_ms")
                    val isPlaying = root.optBoolean("is_playing")
                    val artistsArr = item.optJSONArray("artists")
                    val artistNames = buildList {
                        if (artistsArr != null) {
                            for (i in 0 until artistsArr.length()) {
                                artistsArr.optJSONObject(i)?.optString("name")?.let { add(it) }
                            }
                        }
                    }.joinToString(", ")
                    val imagesArr = item.optJSONObject("album")?.optJSONArray("images")
                    val artworkUrl = imagesArr?.optJSONObject(0)?.optString("url").orEmpty()

                    _playerState.update { current ->
                        current.copy(
                            title = title.ifEmpty { current.title },
                            artist = artistNames.ifEmpty { current.artist },
                            artworkUrl = artworkUrl.ifEmpty { current.artworkUrl },
                            isPlaying = isPlaying,
                            positionMs = progressMs,
                            durationMs = durationMs
                        )
                    }
                }
            }
        }
    }

    private fun parsePathfinderLibraryAndPlaylists(data: JSONObject) {
        val discovered = mutableListOf<SpotifyItem>()
        val libraryItems = data.optJSONObject("me")
            ?.optJSONObject("libraryV3")
            ?.optJSONArray("items")

        if (libraryItems != null) {
            for (i in 0 until libraryItems.length()) {
                val itemWrapper = libraryItems.optJSONObject(i)
                    ?.optJSONObject("item")
                    ?.optJSONObject("data") ?: continue
                parseGraphQlEntity(itemWrapper)?.let { discovered.add(it) }
            }
        }

        val homeSections = data.optJSONObject("home")
            ?.optJSONObject("sectionContainer")
            ?.optJSONObject("sections")
            ?.optJSONArray("items")

        if (homeSections != null) {
            for (i in 0 until homeSections.length()) {
                val sectionItems = homeSections.optJSONObject(i)
                    ?.optJSONObject("sectionItems")
                    ?.optJSONArray("items") ?: continue
                for (j in 0 until sectionItems.length()) {
                    val contentData = sectionItems.optJSONObject(j)
                        ?.optJSONObject("content")
                        ?.optJSONObject("data") ?: continue
                    parseGraphQlEntity(contentData)?.let { discovered.add(it) }
                }
            }
        }

        if (discovered.isNotEmpty()) {
            _playlists.update { existing -> (existing + discovered).distinctBy { it.uri } }
        }
    }

    private fun parsePathfinderSearch(data: JSONObject) {
        val searchV2 = data.optJSONObject("searchV2") ?: return
        val results = mutableListOf<SpotifyItem>()

        val tracks = searchV2.optJSONObject("tracksV2")?.optJSONArray("items")
        if (tracks != null) {
            for (i in 0 until tracks.length()) {
                val trackData = tracks.optJSONObject(i)
                    ?.optJSONObject("item")
                    ?.optJSONObject("data") ?: continue
                parseGraphQlEntity(trackData)?.let { results.add(it) }
            }
        }

        val playlists = searchV2.optJSONObject("playlists")?.optJSONArray("items")
        if (playlists != null) {
            for (i in 0 until playlists.length()) {
                val playlistData = playlists.optJSONObject(i)?.optJSONObject("data") ?: continue
                parseGraphQlEntity(playlistData)?.let { results.add(it) }
            }
        }

        if (results.isNotEmpty()) {
            _searchResults.value = results.distinctBy { it.uri }
        }
    }

    private fun parseGraphQlEntity(entity: JSONObject): SpotifyItem? {
        val uri = entity.optString("uri").takeIf { it.isNotBlank() } ?: return null
        val typeName = entity.optString("__typename")
        val title = entity.optString("name").takeIf { it.isNotBlank() } ?: return null

        return when {
            typeName.equals("Playlist", ignoreCase = true) || uri.contains(":playlist:") -> {
                val ownerName = entity.optJSONObject("ownerV2")
                    ?.optJSONObject("data")
                    ?.optString("name")
                    .orEmpty()
                val imageUrl = extractFirstImageUrl(
                    entity.optJSONObject("images")?.optJSONArray("items")
                )
                SpotifyItem(
                    id = uri.substringAfterLast(":"),
                    uri = uri,
                    title = title,
                    subtitle = ownerName.ifEmpty { "Playlist" },
                    imageUrl = imageUrl,
                    type = SpotifyItemType.PLAYLIST
                )
            }
            typeName.equals("Track", ignoreCase = true) || uri.contains(":track:") -> {
                val artistItems = entity.optJSONObject("artists")?.optJSONArray("items")
                val artistName = artistItems?.optJSONObject(0)
                    ?.optJSONObject("profile")
                    ?.optString("name")
                    .orEmpty()
                val coverSources = entity.optJSONObject("albumOfTrack")
                    ?.optJSONObject("coverArt")
                    ?.optJSONArray("sources")
                val imageUrl = coverSources?.optJSONObject(0)?.optString("url").orEmpty()
                SpotifyItem(
                    id = uri.substringAfterLast(":"),
                    uri = uri,
                    title = title,
                    subtitle = artistName.ifEmpty { "Track" },
                    imageUrl = imageUrl,
                    type = SpotifyItemType.TRACK
                )
            }
            typeName.equals("Album", ignoreCase = true) || uri.contains(":album:") -> {
                val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
                val imageUrl = coverSources?.optJSONObject(0)?.optString("url").orEmpty()
                SpotifyItem(
                    id = uri.substringAfterLast(":"),
                    uri = uri,
                    title = title,
                    subtitle = "Album",
                    imageUrl = imageUrl,
                    type = SpotifyItemType.ALBUM
                )
            }
            else -> null
        }
    }

    private fun extractFirstImageUrl(items: JSONArray?): String {
        if (items == null || items.length() == 0) return ""
        val firstItem = items.optJSONObject(0) ?: return ""
        val sources = firstItem.optJSONArray("sources")
        return sources?.optJSONObject(0)?.optString("url")
            ?: firstItem.optString("url").orEmpty()
    }

    private fun parseWebApiSearchResults(root: JSONObject): List<SpotifyItem> {
        val results = mutableListOf<SpotifyItem>()
        val tracks = root.optJSONObject("tracks")?.optJSONArray("items")
        if (tracks != null) {
            for (i in 0 until tracks.length()) {
                val track = tracks.optJSONObject(i) ?: continue
                val uri = track.optString("uri")
                val name = track.optString("name")
                if (uri.isBlank() || name.isBlank()) continue
                val artist = track.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty()
                val imageUrl = track.optJSONObject("album")?.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty()
                results.add(
                    SpotifyItem(
                        id = track.optString("id", uri.substringAfterLast(":")),
                        uri = uri,
                        title = name,
                        subtitle = artist,
                        imageUrl = imageUrl,
                        type = SpotifyItemType.TRACK
                    )
                )
            }
        }
        return results
    }
}
