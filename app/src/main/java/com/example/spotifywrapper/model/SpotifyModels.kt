package com.example.spotifywrapper.model

data class PlayerState(
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasValidToken: Boolean = false
)

enum class SpotifyItemType {
    PLAYLIST,
    TRACK,
    ALBUM,
    ARTIST
}

data class SpotifyItem(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val type: SpotifyItemType
)
