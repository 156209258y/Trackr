package com.trackr.feature.line

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.screens.linedetail.LineDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 线路详情页的导航入口（`:composeApp` 组装）。第 17 步旧页面在 androidMain，iOS 先放空壳 */
expect fun EntryProviderScope<NavKey>.lineEntries(navigator: Navigator)

/** 线路详情的 Koin 定义（原 `:androidApp` 的 viewModelModule 里那条） */
val lineModule = module {
    viewModel { (key: ScreenKey.LineDetail) ->
        LineDetailViewModel(
            cityId = key.cityId,
            initialLineId = key.lineId,
            initialTargetOrder = key.targetOrder.takeIf { it > 0 },
            initialStationId = key.stopId.takeIf { it.isNotBlank() },
            initialSource = key.source,
            lineNameHint = key.lineName,
            settings = get(),
            favorites = get(),
            realtime = get(),
            reminderRepository = get(),
            trackingRepository = get(),
            trackingController = get()
        )
    }
}
