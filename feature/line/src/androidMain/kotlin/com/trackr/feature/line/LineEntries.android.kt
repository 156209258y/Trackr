package com.trackr.feature.line

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.screens.linedetail.LineDetailScreen
import com.trackr.ui.screens.linedetail.LineDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

actual fun EntryProviderScope<NavKey>.lineEntries(navigator: Navigator) {
    entry<ScreenKey.LineDetail> { key ->
        val detailViewModel: LineDetailViewModel = koinViewModel(
            key = "detail_${key.cityId}_${key.lineId}_${key.targetOrder}"
        ) { parametersOf(key) }

        LineDetailRoute(viewModel = detailViewModel, navigator = navigator)
    }
}

@Composable
private fun LineDetailRoute(viewModel: LineDetailViewModel, navigator: Navigator) {
    // 只在页面处于前台（RESUMED）时轮询；回桌面、锁屏或被其他页面覆盖时停止（重构计划 §4.4）
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { viewModel.onScreenPaused() }
    }

    LineDetailScreen(
        viewModel = viewModel,
        onNavigateBack = dropUnlessResumed {
            navigator.popChildPage()
        }
    )
}
