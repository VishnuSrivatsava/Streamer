package com.streamer.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamer.app.ui.platform.AdaptiveDimens
import com.streamer.app.ui.theme.StreamerLightGray

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    breadcrumbs: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val horizontalPadding = AdaptiveDimens.horizontalPadding()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (breadcrumbs.isNotEmpty()) {
            breadcrumbs.forEachIndexed { index, (name, _) ->
                if (index > 0) {
                    Text(
                        text = " > ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = StreamerLightGray
                    )
                }
                val isLast = index == breadcrumbs.lastIndex
                Text(
                    text = name,
                    style = if (isLast) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(color = StreamerLightGray)
                    }
                )
            }
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
