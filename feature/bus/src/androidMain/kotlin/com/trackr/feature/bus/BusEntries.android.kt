package com.trackr.feature.bus

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.LocalAppViewModelStoreOwner
import com.trackr.ui.navigation.dropUnlessResumed
import com.trackr.ui.screens.home.HomeScreen
import com.trackr.ui.screens.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

actual fun EntryProviderScope<NavKey>.busEntries(navigator: Navigator) {
    entry<ScreenKey.Home> {
        // Activity 级的首页 ViewModel（权限回调也用它），和原来一样
        val homeViewModel: HomeViewModel = koinViewModel(viewModelStoreOwner = LocalAppViewModelStoreOwner.current)
        HomeRoute(viewModel = homeViewModel, navigator = navigator)
    }
}

@Composable
private fun HomeRoute(viewModel: HomeViewModel, navigator: Navigator) {
    // 首页轮询只在 App 前台、首页显示着时进行（重构计划 §4.4）：原来由 MainActivity 的 shouldPollHome 算
    // （当前页签是首页 + 首页在栈顶 + Activity ≥ STARTED），现在首页这个 entry 在组合里就满足前两条，
    // 进详情 / 切页签时 entry 离开组合即停；STARTED 这一档沿用原来的口径
    LifecycleStartEffect(viewModel) {
        viewModel.setPollingActive(true)
        onStopOrDispose { viewModel.setPollingActive(false) }
    }

    HomeScreen(
        viewModel = viewModel,
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
        },
        onNavigateToStopDetail = dropUnlessResumed { cityId, stopId, stopName, physicalStId, namesakeStId, lat, lng ->
            val key = ScreenKey.StopDetail(
                cityId = cityId,
                stopId = stopId,
                stopName = stopName,
                physicalStId = physicalStId,
                namesakeStId = namesakeStId,
                lat = lat,
                lng = lng
            )
            navigator.open(key)
        },
        onNavigateToSearch = dropUnlessResumed {
            navigator.open(ScreenKey.Search)
        },
        // 「收藏」不再是页签（第 17 步）：在首页栈上打开旧收藏页
        onNavigateToFavoritesGroup = dropUnlessResumed {
            navigator.open(ScreenKey.FavoritesGroup)
        }
    )
}
