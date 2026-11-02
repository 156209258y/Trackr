package com.trackr.feature.line

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.TrackrNavigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.screens.linedetail.LineDetailRoute
import com.trackr.ui.screens.linedetail.LineDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** 线路详情页的导航入口（`:composeApp` 组装）。第 20 步页面重写进 commonMain，Android / iOS 共用 */
fun EntryProviderScope<NavKey>.lineEntries(navigator: TrackrNavigator) {
    entry<ScreenKey.LineDetail> { key ->
        val detailViewModel: LineDetailViewModel = koinViewModel(
            key = "detail_${key.cityId}_${key.lineId}_${key.targetOrder}"
        ) { parametersOf(key) }

        LineDetailRoute(
            viewModel = detailViewModel,
            onBack = dropUnlessResumed { navigator.popChildPage() }
        )
    }
}

/** 线路详情的 Koin 定义 */
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
            trackingController = get(),
            lineStopsCache = get()
        )
    }
}
