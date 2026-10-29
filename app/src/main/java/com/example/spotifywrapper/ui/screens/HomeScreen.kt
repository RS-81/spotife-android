package com.example.spotifywrapper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifywrapper.model.SpotifyItem

private val SpotifyGreen = Color(0xFF1DB954)
private val CardBackground = Color(0xFF282828)
private val ChipUnselected = Color(0xFF2A2A2A)

@Composable
fun HomeScreen(
    playlists: List<SpotifyItem>,
    onItemClick: (SpotifyItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Music", "Podcasts")

    val quickAccessItems = remember(playlists) { playlists.take(8) }
    val madeForYouItems = remember(playlists) {
        if (playlists.size > 4) playlists.drop(4) else playlists
    }
    val recentItems = remember(playlists) { playlists.reversed() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            FilterPillsRow(
                filters = filters,
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )
        }

        if (quickAccessItems.isNotEmpty()) {
            item {
                QuickAccessGrid(
                    items = quickAccessItems,
                    onItemClick = onItemClick
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalMediaCarousel(
                title = "Made For You",
                items = madeForYouItems,
                onItemClick = onItemClick
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalMediaCarousel(
                title = "Jump back in",
                items = recentItems,
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
private fun FilterPillsRow(
    filters: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFE91E63)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        filters.forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                shape = CircleShape,
                color = if (isSelected) SpotifyGreen else ChipUnselected,
                modifier = Modifier.clickable { onFilterSelected(filter) }
            ) {
                Text(
                    text = filter,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(
    items: List<SpotifyItem>,
    onItemClick: (SpotifyItem) -> Unit
) {
    val rows = items.chunked(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CardBackground)
                            .clickable { onItemClick(item) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkThumbnail(imageUrl = item.imageUrl, modifier = Modifier.size(56.dp))
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp).weight(1f)
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HorizontalMediaCarousel(
    title: String,
    items: List<SpotifyItem>,
    onItemClick: (SpotifyItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { "${title}_${it.uri}" }) { item ->
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clickable { onItemClick(item) }
                ) {
                    ArtworkThumbnail(
                        imageUrl = item.imageUrl,
                        modifier = Modifier
                            .size(148.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
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

@Composable
fun ArtworkThumbnail(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xFF333333)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color(0xFF777777),
            modifier = Modifier.size(24.dp)
        )
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
