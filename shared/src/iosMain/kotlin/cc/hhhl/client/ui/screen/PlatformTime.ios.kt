package cc.hhhl.client.ui.screen

import platform.Foundation.NSDate

internal actual fun currentEpochMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}
