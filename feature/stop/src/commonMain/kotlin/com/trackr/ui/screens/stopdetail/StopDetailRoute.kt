package com.trackr.ui.screens.stopdetail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey

/**
 * 站点详情的有状态外壳：收集状态、轮询随 RESUMED 启停（§4.4）、一次性提示、导航。页面本身见 [StopDetailScreen]。
 */
@Composable
internal fun StopDetailRoute(viewModel: StopDetailViewModel, navigator: Navigator) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 只在页面处于前台（RESUMED）时轮询；回桌面、锁屏或被其他页面覆盖时停止（重构计划 §4.4）
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { viewModel.onScreenPaused() }
    }

    LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    // 只在 RESUMED 时导航：防连点重复入栈、防转场中再点（同 dropUnlessResumed）
    val lifecycleOwner = LocalLifecycleOwner.current
    fun resumed() = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    StopDetailScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                StopDetailEvent.Back -> if (resumed() && !viewModel.handleBack()) navigator.popChildPage()
                is StopDetailEvent.OpenLine -> if (resumed()) {
                    val row = event.row
                    navigator.open(
                        ScreenKey.LineDetail(
                            cityId = viewModel.cityId,
                            lineId = row.lineId,
                            targetOrder = row.targetOrder,
                            stopId = row.stopId,
                            source = row.source,
                            lineName = row.lineName
                        )
                    )
                }
                else -> viewModel.onEvent(event)
            }
        },
        snackbarHostState = snackbarHostState
    )
}
