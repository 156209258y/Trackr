package com.trackr.feature.search

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.LocalAppViewModelStoreOwner
import com.trackr.ui.navigation.dropUnlessResumed
import com.trackr.ui.screens.search.SearchScreen
import com.trackr.ui.screens.search.SearchViewModel
import org.koin.compose.viewmodel.koinViewModel

actual fun EntryProviderScope<NavKey>.searchEntries(navigator: Navigator) {
    entry<ScreenKey.Search> {
        // Activity 级：退出搜索再进来关键词和结果还在（和拆分前共用首页 VM 时一样）
        val searchViewModel: SearchViewModel = koinViewModel(
            viewModelStoreOwner = LocalAppViewModelStoreOwner.current
        )

        SearchScreen(
            viewModel = searchViewModel,
            onNavigateBack = dropUnlessResumed {
                navigator.popChildPage()
            },
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
            }
        )
    }
}
