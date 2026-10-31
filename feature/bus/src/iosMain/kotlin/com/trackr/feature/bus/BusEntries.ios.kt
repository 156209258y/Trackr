package com.trackr.feature.bus

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.PendingScreen

actual fun EntryProviderScope<NavKey>.busEntries(navigator: Navigator) {
    entry<ScreenKey.Home> { PendingScreen("首页") }
}
