package com.trackr.feature.stop

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.PendingScreen

actual fun EntryProviderScope<NavKey>.stopEntries(navigator: Navigator) {
    entry<ScreenKey.StopDetail> { PendingScreen("站点详情") }
}
