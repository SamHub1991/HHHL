package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal data class HhhlTopBarMetrics(
    val containerHeight: Int,
    val horizontalPadding: Int,
    val slotMinSize: Int,
    val titleHorizontalPadding: Int,
    val backButtonSize: Int,
    val backIconSize: Int,
)

internal fun hhhlTopBarMetrics(): HhhlTopBarMetrics = HhhlTopBarMetrics(
    containerHeight = 44,
    horizontalPadding = 12,
    slotMinSize = 34,
    titleHorizontalPadding = 0,
    backButtonSize = 30,
    backIconSize = 18,
)

@Composable
fun HhhlTopBar(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val metrics = hhhlTopBarMetrics()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.containerHeight.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = metrics.horizontalPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.widthIn(min = metrics.slotMinSize.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            navigation?.invoke()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = metrics.titleHorizontalPadding.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            TopBarTitleBlock(
                title = title,
                supportingText = supportingText,
            )
        }
        Box(
            modifier = Modifier.widthIn(min = metrics.slotMinSize.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            action?.invoke()
        }
    }
}

@Composable
private fun TopBarTitleBlock(
    title: String,
    supportingText: String?,
) {
    val cleanSupportingText = supportingText?.takeIf { it.isNotBlank() }
    if (cleanSupportingText == null) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = cleanSupportingText,
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HhhlBackButton(
    onClick: () -> Unit,
    label: String = "返回",
) {
    val metrics = hhhlTopBarMetrics()

    Box(
        modifier = Modifier
            .size(metrics.backButtonSize.dp)
            .clip(RoundedCornerShape(HhhlControlCornerRadius))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.78f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = label,
            modifier = Modifier.size(metrics.backIconSize.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
