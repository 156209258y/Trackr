package com.trackr.ui.screens.routeplan.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** iOS 第 38 步才接高德 iOS SDK（Q15）；之前规划页不画地图区（Q18），定位按钮也就没有 */
actual val isRouteMapSupported: Boolean = false

@Composable
actual fun RouteMap(state: RouteMapState, modifier: Modifier, bottomInset: () -> Dp) {
    NoMapBackground(modifier)
}
