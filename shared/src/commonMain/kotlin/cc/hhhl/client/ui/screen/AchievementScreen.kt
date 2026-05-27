package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cc.hhhl.client.model.Achievement
import cc.hhhl.client.model.AchievementRank
import cc.hhhl.client.state.AchievementUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTopBar
import kotlinx.coroutines.delay

@Composable
fun AchievementScreen(
    state: AchievementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onClaimViewMilestone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(ACHIEVEMENT_VIEW_MILESTONE_DELAY_MS)
        onClaimViewMilestone()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "成就",
            supportingText = when {
                state.isLoading -> "同步中"
                state.totalCount == 0 -> "原生成就"
                else -> "${state.unlockedCount}/${state.totalCount} 已解锁"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        AchievementSummaryRow(
            unlockedCount = state.unlockedCount,
            totalCount = state.totalCount,
            isLoading = state.isLoading,
            onRefresh = onRefresh,
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            state.errorMessage?.let { message ->
                item(contentType = "achievement-status") {
                    AchievementStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state.isLoading && state.achievements.isEmpty()) {
                item(contentType = "achievement-status") {
                    AchievementStatusRow(text = "正在加载成就...", loading = true)
                }
            }
            if (!state.isLoading && state.achievements.isEmpty() && state.errorMessage == null) {
                item(contentType = "achievement-status") {
                    AchievementStatusRow(text = "暂无成就数据")
                }
            }
            items(
                items = state.achievements,
                key = { it.name },
                contentType = { "achievement" },
            ) { achievement ->
                AchievementRow(achievement = achievement)
                HhhlDivider()
            }
        }
    }
}

@Composable
private fun AchievementSummaryRow(
    unlockedCount: Int,
    totalCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val percent = if (totalCount == 0) 0 else unlockedCount * 100 / totalCount
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$unlockedCount 个已解锁 · $percent%",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HhhlIconActionButton(
            icon = Icons.Filled.Refresh,
            contentDescription = if (isLoading) "同步成就中" else "刷新成就",
            emphasized = true,
            enabled = !isLoading,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (achievement.isUnlocked) {
                    MaterialTheme.colorScheme.background
                } else {
                    LocalHhhlColors.current.inputBackground.copy(alpha = 0.45f)
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AchievementIcon(achievement)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (achievement.isUnlocked) achievement.title else "???",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = achievement.rank.label,
                    color = achievement.rankColor(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (achievement.isUnlocked) achievement.description else "未解锁",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (achievement.isUnlocked && achievement.unlockedAtLabel.isNotBlank()) {
                Text(
                    text = achievement.unlockedAtLabel,
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun AchievementIcon(achievement: Achievement) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(achievement.rankColor().copy(alpha = if (achievement.isUnlocked) 0.22f else 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (achievement.isUnlocked && achievement.iconUrl != null) {
            AsyncImage(
                model = achievement.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = LocalHhhlColors.current.subtleText,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun AchievementStatusRow(
    text: String,
    loading: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    HhhlStatusRow(
        text = text,
        loading = loading,
        actionText = actionText,
        onAction = onAction,
    )
}

@Composable
private fun Achievement.rankColor() = when (rank) {
    AchievementRank.Bronze -> MaterialTheme.colorScheme.tertiary
    AchievementRank.Silver -> MaterialTheme.colorScheme.secondary
    AchievementRank.Gold -> MaterialTheme.colorScheme.primary
    AchievementRank.Platinum -> MaterialTheme.colorScheme.error
}

private const val ACHIEVEMENT_VIEW_MILESTONE_DELAY_MS = 180_000L
