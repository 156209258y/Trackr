package com.trackr.feature.route

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.data.repository.AmapRouteGateway
import com.trackr.core.domain.platform.AmapSdkKeyApplier
import com.trackr.core.domain.platform.MapSdkInitializer
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.data.remote.amap.AmapSdkTransitService
import com.trackr.data.remote.amap.AndroidAmapRouteGateway
import com.trackr.data.remote.amap.AndroidAmapSdkKeyApplier
import com.trackr.data.remote.amap.AndroidMapSdkInitializer
import com.trackr.ui.navigation.LocalAppViewModelStoreOwner
import com.trackr.ui.navigation.PendingScreen
import com.trackr.ui.navigation.dropUnlessResumed
import com.trackr.ui.screens.routedetail.RouteDetailViewModel
import com.trackr.ui.screens.routeplan.LocalAmapMapSession
import com.trackr.ui.screens.routeplan.RoutePlanScreen
import com.trackr.ui.screens.routeplan.RoutePlanViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.module

actual fun EntryProviderScope<NavKey>.routeEntries(navigator: Navigator) {
    entry<ScreenKey.RoutePlan> {
        // 原来 MainActivity 在 NavDisplay 外取一个 FavoritesGroupViewModel 给规划页和收藏页共用；拆开后仍取 Activity 级
        val appOwner = LocalAppViewModelStoreOwner.current
        val routePlanViewModel: RoutePlanViewModel = koinViewModel(viewModelStoreOwner = appOwner)
        val routeDetailViewModel: RouteDetailViewModel = koinViewModel(viewModelStoreOwner = appOwner)
        RoutePlanScreen(
            viewModel = routePlanViewModel,
            routeDetailViewModel = routeDetailViewModel,
            mapSession = LocalAmapMapSession.current,
            // 「收藏」不再是页签（第 17 步）：切到首页页签，让收藏页成为栈顶（已有就退到它，没有就回根再打开）
            onNavigateToFavorites = dropUnlessResumed {
                navigator.showInTab(ScreenKey.Home, ScreenKey.FavoritesGroup)
            },
            onNavigateToLineDetail = dropUnlessResumed { cityId, lineId, targetOrder, stopId, source, lineName ->
                val key = ScreenKey.LineDetail(
                    cityId = cityId,
                    lineId = lineId,
                    targetOrder = targetOrder,
                    stopId = stopId,
                    source = source,
                    lineName = lineName
                )
                navigator.open(key)
            }
        )
    }

    // 路线详情：第 22 步做页面，第 17 步只占位（还没有入口）
    entry<ScreenKey.RouteDetail> { PendingScreen("路线详情") }
}

/** 高德 Android SDK 相关的绑定（§2.9：SDK 封装在 :feature:route 的 androidMain） */
val routeAndroidModule = module {
    // SDK 优先、失败才走 Web 的高德通道（原 FavoritesGroupViewModel 的内部类）；SDK 建不出来时只走 Web
    single<AmapRouteGateway> {
        AndroidAmapRouteGateway(
            amapSdkService = runCatching { AmapSdkTransitService(androidContext()) }.getOrNull(),
            amapApiClient = get(),
            amapKeys = get()
        )
    }
    single<AmapSdkKeyApplier> { AndroidAmapSdkKeyApplier(androidContext()) }
    single<MapSdkInitializer> { AndroidMapSdkInitializer(androidContext()) }
}
