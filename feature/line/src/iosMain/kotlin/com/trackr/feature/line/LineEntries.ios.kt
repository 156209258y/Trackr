package com.trackr.feature.line

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.PendingScreen

actual fun EntryProviderScope<NavKey>.lineEntries(navigator: Navigator) {
    entry<ScreenKey.LineDetail> { PendingScreen("线路详情") }
}
