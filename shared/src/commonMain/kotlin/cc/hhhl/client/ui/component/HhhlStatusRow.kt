package cc.hhhl.client.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

@Composable
fun HhhlStatusRow(
    text: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            HhhlProgressIndicator(strokeWidth = 2.dp)
        }
        Text(
            text = actionText ?: text,
            color = if (onAction != null) {
                colors.accent
            } else {
                colors.textSecondary
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (onAction != null) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onAction != null) Modifier.clickable { onAction() } else Modifier,
        )
        if (actionText != null) {
            Text(
                text = text,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HhhlDivider()
}
