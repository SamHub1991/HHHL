package cc.hhhl.client.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun ConfigurePlatformSystemBars(colors: HhhlColors) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    val statusBarColor = colors.pageBackground.toArgb()
    val navigationBarColor = colors.bottomNavBackground.toArgb()
    val useDarkStatusIcons = colors.pageBackground.luminance() > 0.5f
    val useDarkNavigationIcons = colors.bottomNavBackground.luminance() > 0.5f

    SideEffect {
        window.statusBarColor = statusBarColor
        window.navigationBarColor = navigationBarColor
        window.setSystemBarIconColors(
            decorView = view,
            useDarkStatusIcons = useDarkStatusIcons,
            useDarkNavigationIcons = useDarkNavigationIcons,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Suppress("DEPRECATION")
private fun Window.setSystemBarIconColors(
    decorView: View,
    useDarkStatusIcons: Boolean,
    useDarkNavigationIcons: Boolean,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val statusAppearance = if (useDarkStatusIcons) {
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        } else {
            0
        }
        val navigationAppearance = if (useDarkNavigationIcons) {
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        } else {
            0
        }
        insetsController?.setSystemBarsAppearance(
            statusAppearance or navigationAppearance,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    } else {
        var flags = decorView.systemUiVisibility
        flags = if (useDarkStatusIcons) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        flags = if (useDarkNavigationIcons && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        decorView.systemUiVisibility = flags
    }
}
