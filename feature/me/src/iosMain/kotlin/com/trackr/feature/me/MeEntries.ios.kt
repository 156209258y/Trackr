package com.trackr.feature.me

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.PendingScreen

/** 页面还在 androidMain（阶段 D 重写进 commonMain），iOS 先放空壳 */
actual fun EntryProviderScope<NavKey>.meEntries(navigator: Navigator) {
    entry<ScreenKey.More> { PendingScreen("更多") }
    entry<ScreenKey.About> { PendingScreen("关于") }
}
