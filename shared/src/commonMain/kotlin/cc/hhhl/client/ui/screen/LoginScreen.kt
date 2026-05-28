package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.auth.LoginUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HHHL_BRAND_AVATAR_URL
import cc.hhhl.client.ui.component.HhhlProgressIndicator
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.hhhlReadableOnControlColor
import coil3.compose.rememberAsyncImagePainter

@Composable
fun LoginScreen(
    state: LoginUiState,
    onLogin: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.42f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HhhlIdentity()
            LoginLead()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LoginStatusPanel(state = state)
                AccountSwitchPanel(
                    accounts = state.accounts,
                    currentAccountId = state.currentAccountId,
                    onSwitchAccount = onSwitchAccount,
                    onRemoveAccount = onRemoveAccount,
                )
                LoginEntryPanel(
                    state = state,
                    onLogin = onLogin,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AccountSwitchPanel(
    accounts: List<AccountSession>,
    currentAccountId: String?,
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddAccount: (() -> Unit)? = null,
) {
    if (accounts.isEmpty()) return
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val neutralLayer = if (isDarkSurface) {
        loginNeutralLayer().copy(alpha = 0.70f)
    } else {
        colors.surfaceElevated.copy(alpha = 0.74f)
    }
    val neutralBorder = if (isDarkSurface) {
        colors.border.copy(alpha = 0.36f)
    } else {
        colors.focusRing.copy(alpha = 0.08f)
    }
    val addButtonContainer = if (isDarkSurface) {
        loginNeutralLayer().copy(alpha = 0.82f)
    } else {
        colors.buttonSelectedBackground.copy(alpha = 0.25f)
    }
    val addButtonBorder = if (isDarkSurface) {
        colors.border.copy(alpha = 0.38f)
    } else {
        colors.focusRing.copy(alpha = 0.09f)
    }
    val addButtonContent = if (isDarkSurface) {
        colors.textPrimary
    } else {
        colors.accent
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(neutralLayer)
            .border(
                width = 1.dp,
                color = neutralBorder,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(vertical = 6.dp),
    ) {
        accounts.forEach { account ->
            AccountSwitchRow(
                account = account,
                selected = account.id == currentAccountId,
                onSwitchAccount = onSwitchAccount,
                onRemoveAccount = onRemoveAccount,
            )
        }
        onAddAccount?.let { addAccount ->
            HhhlTextButton(
                onClick = addAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                containerColor = addButtonContainer,
                contentColor = addButtonContent,
                borderColor = addButtonBorder,
            ) {
                Text("添加账号")
            }
        }
    }
}

@Composable
private fun AccountSwitchRow(
    account: AccountSession,
    selected: Boolean,
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val markerShape = RoundedCornerShape(10.dp)
    val markerContainer = when {
        selected && isDarkSurface -> loginNeutralLayer().copy(alpha = 0.96f)
        selected -> colors.buttonSelectedBackground.copy(alpha = 0.36f)
        isDarkSurface -> loginNeutralLayer().copy(alpha = 0.66f)
        else -> colors.surfaceElevated.copy(alpha = 0.72f)
    }
    val markerBorder = when {
        selected && isDarkSurface -> colors.textMuted.copy(alpha = 0.28f)
        selected -> colors.focusRing.copy(alpha = 0.16f)
        isDarkSurface -> colors.border.copy(alpha = 0.34f)
        else -> colors.border.copy(alpha = 0.46f)
    }
    val markerContent = when {
        selected -> hhhlReadableOnControlColor(markerContainer, colors.accent)
        else -> colors.textMuted
    }
    val rowButtonContainer = if (isDarkSurface) {
        loginNeutralLayer().copy(alpha = 0.78f)
    } else {
        colors.buttonSelectedBackground.copy(alpha = 0.25f)
    }
    val rowButtonBorder = if (isDarkSurface) {
        colors.textMuted.copy(alpha = 0.20f)
    } else {
        colors.focusRing.copy(alpha = 0.085f)
    }
    val removeButtonContainer = if (isDarkSurface) {
        colors.danger.copy(alpha = 0.12f)
    } else {
        colors.danger.copy(alpha = 0.07f)
    }
    val removeButtonBorder = if (isDarkSurface) {
        colors.danger.copy(alpha = 0.22f)
    } else {
        colors.danger.copy(alpha = 0.16f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(markerShape)
                .background(markerContainer)
                .border(1.dp, markerBorder, markerShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = markerContent,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    text = accountLabel(account).take(1).uppercase(),
                    color = markerContent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = accountLabel(account),
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.host,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HhhlTextButton(
            onClick = { onSwitchAccount(account.id) },
            enabled = !selected,
            containerColor = rowButtonContainer,
            contentColor = if (selected) colors.textMuted else colors.textPrimary,
            borderColor = rowButtonBorder,
        ) {
            Text(if (selected) "当前" else "切换")
        }
        HhhlTextButton(
            onClick = { onRemoveAccount(account.id) },
            containerColor = removeButtonContainer,
            contentColor = colors.danger.copy(alpha = if (isDarkSurface) 0.86f else 1f),
            borderColor = removeButtonBorder,
        ) {
            Text("移除")
        }
    }
}

private fun accountLabel(account: AccountSession): String {
    val user = account.user ?: return "已保存账号"
    return user.displayName.ifBlank { "@${user.username}" }
}

@Composable
private fun loginNeutralLayer() = LocalHhhlColors.current.run {
    surface.blendWith(pageBackground, 0.42f)
        .blendWith(surfaceElevated, 0.20f)
        .desaturate(0.46f)
}

@Composable
private fun LoginLead() {
    val colors = LocalHhhlColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "登录 HHHL",
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "使用 dc.hhhl.cc 账号继续",
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun androidx.compose.ui.graphics.Color.blendWith(
    other: androidx.compose.ui.graphics.Color,
    otherRatio: Float,
): androidx.compose.ui.graphics.Color {
    val ratio = otherRatio.coerceIn(0f, 1f)
    val selfRatio = 1f - ratio
    return androidx.compose.ui.graphics.Color(
        red = red * selfRatio + other.red * ratio,
        green = green * selfRatio + other.green * ratio,
        blue = blue * selfRatio + other.blue * ratio,
        alpha = alpha * selfRatio + other.alpha * ratio,
    )
}

private fun androidx.compose.ui.graphics.Color.desaturate(amount: Float): androidx.compose.ui.graphics.Color {
    val ratio = amount.coerceIn(0f, 1f)
    val grey = red * 0.299f + green * 0.587f + blue * 0.114f
    return androidx.compose.ui.graphics.Color(
        red = red * (1f - ratio) + grey * ratio,
        green = green * (1f - ratio) + grey * ratio,
        blue = blue * (1f - ratio) + grey * ratio,
        alpha = alpha,
    )
}

@Composable
private fun LoginStatusPanel(state: LoginUiState) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val statusMessage = state.statusMessage
    val errorMessage = state.errorMessage
    if (statusMessage == null && errorMessage == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDarkSurface) loginNeutralLayer().copy(alpha = 0.72f) else colors.surfaceElevated.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        statusMessage?.let { message ->
            Text(
                text = message,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                color = colors.danger,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LoginEntryPanel(
    state: LoginUiState,
    onLogin: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (isDarkSurface) loginNeutralLayer().copy(alpha = 0.78f) else colors.surfaceElevated.copy(alpha = 0.78f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LoginMetaRow(
                label = "实例",
                value = "dc.hhhl.cc",
                detail = "通过浏览器完成授权登录",
            )
        }

        HhhlTextButton(
            onClick = onLogin,
            enabled = !state.isLoading,
            emphasized = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.isLoading) {
                HhhlProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (state.pendingSession == null) "浏览器授权登录" else "完成授权",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        state.statusMessage?.let { message ->
            Text(
                text = message,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoginMetaRow(
    label: String,
    value: String,
    detail: String? = null,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(
                    text = it,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HhhlIdentity() {
    val colors = LocalHhhlColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.mediaBackground),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberAsyncImagePainter(HHHL_BRAND_AVATAR_URL),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = "HHHL",
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
