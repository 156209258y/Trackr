package com.trackr.feature.stop

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.dropUnlessResumed
import com.trackr.ui.screens.stopdetail.StopDetailScreen
import com.trackr.ui.screens.stopdetail.StopDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

actual fun EntryProviderScope<NavKey>.stopEntries(navigator: Navigator) {
    entry<ScreenKey.StopDetail> { key ->
        val stopDetailViewModel: StopDetailViewModel = koinViewModel(
            key = "stop_${key.cityId}_${key.stopId}_${key.physicalStId}_${key.stopName}"
        ) { parametersOf(key) }

        StopDetailRoute(viewModel = stopDetailViewModel, navigator = navigator)
    }
}

@Composable
private fun StopDetailRoute(viewModel: StopDetailViewModel, navigator: Navigator) {
    // 只在页面处于前台（RESUMED）时轮询；回桌面、锁屏或被其他页面覆盖时停止（重构计划 §4.4）
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { viewModel.onScreenPaused() }
    }

    StopDetailScreen(
        viewModel = viewModel,
        onNavigateBack = dropUnlessResumed {
            navigator.popChildPage()
        },
        onNavigateToLineDetail = dropUnlessResumed { cityId, lineId, targetOrder, stopId, source, lineName ->
            val lineDetailKey = ScreenKey.LineDetail(
                cityId = cityId,
                lineId = lineId,
                targetOrder = targetOrder,
                stopId = stopId,
                source = source,
                lineName = lineName
            )
            navigator.open(lineDetailKey)
        }
    )
}
