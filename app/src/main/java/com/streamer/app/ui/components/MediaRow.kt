package com.streamer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamer.app.data.model.MediaItem
import com.streamer.app.ui.platform.AdaptiveDimens
import com.streamer.app.ui.platform.LocalIsTv

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val horizontalPadding = AdaptiveDimens.horizontalPadding()

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = horizontalPadding, bottom = 12.dp)
        )

        if (LocalIsTv.current) {
            TvLazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items.size, key = { items[it].indexItem.name }) { index ->
                    val item = items[index]
                    MediaRowItem(item = item, onClick = { onItemClick(item) })
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.indexItem.name }) { item ->
                    MediaRowItem(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun MediaRowItem(item: MediaItem, onClick: () -> Unit) {
    if (item.indexItem.isFolder) {
        FolderCard(item = item, onClick = onClick)
    } else {
        MediaCard(item = item, onClick = onClick)
    }
}
