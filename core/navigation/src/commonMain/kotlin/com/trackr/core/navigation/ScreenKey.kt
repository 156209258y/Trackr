package com.trackr.core.navigation

import androidx.navigation3.runtime.NavKey
import com.trackr.core.model.DataSourceType
import kotlinx.serialization.Serializable

/**
 * 所有页面的 Navigation 3 Key。第 16 步只从 `:androidApp` 原样搬来（字段、类名不变，旧返回栈仍能恢复）；
 * 改名（Home→Bus、More→Me）、`LineRef` 与新增页面等第 17 步起按 §3.2 做。
 *
 * 新增子类时要同时登记到 [TrackrSavedStateConfiguration]，否则非 Android 平台恢复返回栈会失败
 * （`NavigationStateRestorationTest` 会挡住漏登记）。
 */
@Serializable
sealed interface ScreenKey : NavKey {
    @Serializable
    data object Home : ScreenKey

    @Serializable
    data object RoutePlan : ScreenKey

    @Serializable
    data object FavoritesGroup : ScreenKey

    @Serializable
    data object Search : ScreenKey

    @Serializable
    data class LineDetail(
        val cityId: String,
        val lineId: String,
        val targetOrder: Int = 0,
        val stopId: String = "",
        val source: DataSourceType = DataSourceType.CHELAILE,
        val lineName: String = ""
    ) : ScreenKey

    @Serializable
    data class StopDetail(
        val cityId: String,
        val stopId: String,
        val stopName: String = "",
        val physicalStId: String = "",
        val namesakeStId: String = "",
        val lat: Double = 0.0,
        val lng: Double = 0.0
    ) : ScreenKey

    @Serializable
    data object More : ScreenKey

    @Serializable
    data object About : ScreenKey

    /** 路线详情（§3.1 公交页签子页，第 22 步做页面；第 17 步只占位） */
    @Serializable
    data class RouteDetail(val groupId: String) : ScreenKey

    /** 编辑收藏（§3.1 公交页签子页，第 22 步做页面；第 17 步只占位） */
    @Serializable
    data object FavoritesEdit : ScreenKey
}
