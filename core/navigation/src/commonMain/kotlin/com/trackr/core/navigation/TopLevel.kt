package com.trackr.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * 顶层页签（每个页签一条返回栈）。第 16 步仍是 4 个页签：首页、路线规划、收藏、更多；
 * 第 17 步改成 §3.1 的 3 个（公交、路线、我的）。
 */
object TopLevel {
    /** 起始页签：非起始页签在根页按返回回到这里；它的根页再按返回就退出（exit-through-home）。 */
    val start: ScreenKey = ScreenKey.Home

    /** 底栏顺序。 */
    val tabs: List<ScreenKey> = listOf(
        ScreenKey.Home,
        ScreenKey.RoutePlan,
        ScreenKey.FavoritesGroup,
        ScreenKey.More,
    )

    fun isTopLevel(key: NavKey?): Boolean = key != null && key in tabs
}
