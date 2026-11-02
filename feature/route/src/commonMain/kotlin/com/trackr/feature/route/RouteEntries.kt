package com.trackr.feature.route

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.TrackrNavigator
import com.trackr.ui.screens.routedetail.RouteDetailViewModel
import com.trackr.ui.screens.routeplan.RoutePlanViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * 路线规划页签和路线详情（第 22 步做，第 17 步空壳）的导航入口（`:composeApp` 组装）。
 * 第 17 步旧规划页在 androidMain，iOS 先放空壳。
 */
expect fun EntryProviderScope<NavKey>.routeEntries(navigator: TrackrNavigator)

/**
 * 规划页、路线（追踪 / 实时到站）的 Koin 定义：原 `FavoritesGroupViewModel` 拆出来的两个。
 * 第 17 步两个都取 Activity 级的一个实例（规划页和收藏页共用 [RouteDetailViewModel]）。
 */
val routeModule = module {
    viewModel {
        RoutePlanViewModel(
            routePlan = get(),
            settings = get(),
            realtime = get(),
            userLocationStore = get(),
            groupRepository = get(),
            groupSaver = get(),
            amapKeys = get(),
            locationRepository = get()
        )
    }
    viewModel {
        RouteDetailViewModel(
            groupRepository = get(),
            settings = get(),
            realtime = get(),
            alignment = get(),
            trackingRepository = get(),
            trackingController = get()
        )
    }
}
