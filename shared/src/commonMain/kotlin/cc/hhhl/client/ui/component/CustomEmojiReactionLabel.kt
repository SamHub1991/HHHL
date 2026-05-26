package cc.hhhl.client.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun CustomEmojiReactionLabel(
    reaction: String,
    modifier: Modifier = Modifier,
) {
    val emojiUrl = LocalCustomEmojiUrls.current[reaction]
    var imageLoaded by remember(emojiUrl) { mutableStateOf(false) }
    if (emojiUrl != null) {
        Box(
            modifier = modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = emojiUrl,
                contentDescription = reaction,
                contentScale = ContentScale.Fit,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
                modifier = Modifier.fillMaxSize(),
            )
            if (!imageLoaded) {
                Text(
                    text = reaction,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    } else {
        Text(
            text = reaction,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = modifier,
        )
    }
}
