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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.auth.LoginUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HHHL_BRAND_AVATAR_URL
import cc.hhhl.client.ui.component.HhhlTextButton
import coil3.compose.rememberAsyncImagePainter

@Composable
fun LoginScreen(
    state: LoginUiState,
    onLogin: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val neutralLayer = if (isDarkSurface) {
        Color.White.copy(alpha = 0.035f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
    }
    val neutralBorder = if (isDarkSurface) {
        Color.White.copy(alpha = 0.075f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }
    val addButtonContainer = if (isDarkSurface) {
        Color.White.copy(alpha = 0.04f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.025f)
    }
    val addButtonBorder = if (isDarkSurface) {
        Color.White.copy(alpha = 0.075f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
    }
    val addButtonContent = if (isDarkSurface) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.primary
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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val markerShape = RoundedCornerShape(8.dp)
    val markerContainer = when {
        selected && isDarkSurface -> Color.White.copy(alpha = 0.08f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        isDarkSurface -> Color.White.copy(alpha = 0.04f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val markerBorder = when {
        selected && isDarkSurface -> Color.White.copy(alpha = 0.12f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        isDarkSurface -> Color.White.copy(alpha = 0.06f)
        else -> LocalHhhlColors.current.divider.copy(alpha = 0.46f)
    }
    val markerContent = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> LocalHhhlColors.current.subtleText
    }
    val rowButtonContainer = if (isDarkSurface) {
        Color.White.copy(alpha = 0.035f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.025f)
    }
    val rowButtonBorder = if (isDarkSurface) {
        Color.White.copy(alpha = 0.065f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.085f)
    }
    val removeButtonContainer = if (isDarkSurface) {
        Color.White.copy(alpha = 0.028f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
    }
    val removeButtonBorder = if (isDarkSurface) {
        Color.White.copy(alpha = 0.055f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
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
                .size(28.dp)
                .clip(markerShape)
                .background(markerContainer)
                .border(1.dp, markerBorder, markerShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selected) "✓" else accountLabel(account).take(1).uppercase(),
                color = markerContent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = accountLabel(account),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.host,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HhhlTextButton(
            onClick = { onSwitchAccount(account.id) },
            enabled = !selected,
            containerColor = rowButtonContainer,
            contentColor = if (selected) LocalHhhlColors.current.subtleText else MaterialTheme.colorScheme.onBackground,
            borderColor = rowButtonBorder,
        ) {
            Text(if (selected) "当前" else "切换")
        }
        HhhlTextButton(
            onClick = { onRemoveAccount(account.id) },
            containerColor = removeButtonContainer,
            contentColor = MaterialTheme.colorScheme.error.copy(alpha = if (isDarkSurface) 0.86f else 1f),
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
private fun LoginLead() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "登录 HHHL",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "使用 dc.hhhl.cc 账号继续",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoginStatusPanel(state: LoginUiState) {
    val statusMessage = state.statusMessage
    val errorMessage = state.errorMessage
    if (statusMessage == null && errorMessage == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        statusMessage?.let { message ->
            Text(
                text = message,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f))
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
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
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
                color = LocalHhhlColors.current.subtleText,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(
                    text = it,
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HhhlIdentity() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF08090B)),
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
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
