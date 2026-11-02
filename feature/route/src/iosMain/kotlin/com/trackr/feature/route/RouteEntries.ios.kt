package com.trackr.feature.route

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.TrackrNavigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.PendingScreen

actual fun EntryProviderScope<NavKey>.routeEntries(navigator: TrackrNavigator) {
    entry<ScreenKey.RoutePlan> { PendingScreen("路线规划") }
    entry<ScreenKey.RouteDetail> { PendingScreen("路线详情") }
}
