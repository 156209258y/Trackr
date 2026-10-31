package com.trackr.feature.bus

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.domain.platform.MonotonicClock
import com.trackr.core.navigation.Navigator
import com.trackr.ui.screens.home.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 首页（公交页签根页）的导航入口（`:composeApp` 组装）。第 17 步旧页面在 androidMain，iOS 先放空壳 */
expect fun EntryProviderScope<NavKey>.busEntries(navigator: Navigator)

/** 首页的 Koin 定义（原 `:androidApp` 的 viewModelModule 里那条）；第 17 步仍取 Activity 级的一个实例 */
val busModule = module {
    viewModel {
        // 补刷判断用的单调时钟：原来直接读 SystemClock.elapsedRealtime()，Android 上注入的就是它
        val clock = get<MonotonicClock>()
        HomeViewModel(
            settings = get(),
            favorites = get(),
            realtime = get(),
            userLocationStore = get(),
            locationRepository = get(),
            trackingController = get(),
            trackingRepository = get(),
            elapsedRealtimeMillis = { clock.elapsedMillis() },
            homeCoordinates = get()
        )
    }
}
