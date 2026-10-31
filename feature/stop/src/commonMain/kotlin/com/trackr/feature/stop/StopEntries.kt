package com.trackr.feature.stop

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.screens.stopdetail.StopDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 站点详情页的导航入口（`:composeApp` 组装）。第 17 步旧页面在 androidMain，iOS 先放空壳 */
expect fun EntryProviderScope<NavKey>.stopEntries(navigator: Navigator)

/** 站点详情的 Koin 定义（原 `:androidApp` 的 viewModelModule 里那条） */
val stopModule = module {
    viewModel { (key: ScreenKey.StopDetail) ->
        StopDetailViewModel(
            cityId = key.cityId,
            stopId = key.stopId,
            initialStopName = key.stopName,
            physicalStId = key.physicalStId,
            namesakeStId = key.namesakeStId,
            stationLat = key.lat,
            stationLng = key.lng,
            settings = get(),
            favorites = get(),
            realtime = get(),
            userLocationStore = get()
        )
    }
}
