package com.trackr.feature.stop

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.screens.stopdetail.StopDetailRoute
import com.trackr.ui.screens.stopdetail.StopDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** 站点详情页的导航入口（`:composeApp` 组装）。第 21 步：v3 页面写进 commonMain，旧 androidMain 页面删掉 */
fun EntryProviderScope<NavKey>.stopEntries(navigator: Navigator) {
    entry<ScreenKey.StopDetail> { key ->
        val stopDetailViewModel: StopDetailViewModel = koinViewModel(
            key = "stop_${key.cityId}_${key.stopId}_${key.physicalStId}_${key.stopName}"
        ) { parametersOf(key) }

        StopDetailRoute(viewModel = stopDetailViewModel, navigator = navigator)
    }
}

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
            userLocationStore = get(),
            trackingRepository = get(),
            trackingController = get(),
            transitCodeLauncher = get()
        )
    }
}
