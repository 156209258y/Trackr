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

    /**
     * 底栏实际显示的页签（D2）：没有高德 Key（`AmapKeyRepository.hasMapKey` 为 false）时去掉「路线规划」。
     * 顺序同 [tabs]。页签集合变了交给 [Navigator.onTabsChanged]（在被去掉的页签上就清栈回起始页签）。
     */
    fun visibleTabs(hasMapKey: Boolean): List<ScreenKey> =
        if (hasMapKey) tabs else tabs.filterNot { it == ScreenKey.RoutePlan }
}
