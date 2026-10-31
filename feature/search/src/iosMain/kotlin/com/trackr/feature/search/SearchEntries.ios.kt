package com.trackr.feature.search

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.PendingScreen

actual fun EntryProviderScope<NavKey>.searchEntries(navigator: Navigator) {
    entry<ScreenKey.Search> { PendingScreen("搜索") }
}
