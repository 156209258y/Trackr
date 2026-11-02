package com.trackr.feature.bus

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.domain.platform.MonotonicClock
import com.trackr.core.navigation.TrackrNavigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.feature.bus.home.BusHomeRoute
import com.trackr.feature.bus.home.BusHomeViewModel
import com.trackr.ui.navigation.LocalAppViewModelStoreOwner
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 公交首页（公交页签根页）的导航入口（`:composeApp` 组装）。第 19 步起页面在 commonMain，Android / iOS 共用 */
fun EntryProviderScope<NavKey>.busEntries(navigator: TrackrNavigator) {
    entry<ScreenKey.Home> {
        // Activity 级的首页 ViewModel（权限回调、TrackrRoot 首帧关轮询也用它），和原来一样
        val viewModel: BusHomeViewModel = koinViewModel(viewModelStoreOwner = LocalAppViewModelStoreOwner.current)
        BusHomeRoute(viewModel = viewModel, navigator = navigator)
    }
}

/** 首页的 Koin 定义；仍取 Activity 级的一个实例 */
val busModule = module {
    viewModel {
        // 补刷判断用的单调时钟：原来直接读 SystemClock.elapsedRealtime()，Android 上注入的就是它
        val clock = get<MonotonicClock>()
        BusHomeViewModel(
            settings = get(),
            favorites = get(),
            realtime = get(),
            userLocationStore = get(),
            locationRepository = get(),
            trackingController = get(),
            trackingRepository = get(),
            transitCodeLauncher = get(),
            elapsedRealtimeMillis = { clock.elapsedMillis() },
            homeCoordinates = get()
        )
    }
}
