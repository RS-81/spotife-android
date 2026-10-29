package com.example.spotifywrapper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.spotifywrapper.data.SpotifyRepository
import com.example.spotifywrapper.model.PlayerState
import com.example.spotifywrapper.model.SpotifyItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SpotifyViewModel(
    private val repository: SpotifyRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = repository.playerState
    val playlists: StateFlow<List<SpotifyItem>> = repository.playlists
    val searchResults: StateFlow<List<SpotifyItem>> = repository.searchResults

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(350L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    repository.search(query)
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun playItem(item: SpotifyItem) {
        repository.playUri(item.uri)
    }

    fun togglePlayPause() {
        repository.togglePlayPause()
    }

    fun skipNext() {
        repository.skipNext()
    }

    fun seekTo(positionMs: Long) {
        repository.seekTo(positionMs)
    }

    class Factory(
        private val repository: SpotifyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpotifyViewModel(repository) as T
        }
    }
}
