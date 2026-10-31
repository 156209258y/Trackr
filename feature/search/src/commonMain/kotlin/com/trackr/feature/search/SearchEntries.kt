package com.trackr.feature.search

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.ui.screens.search.SearchViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 搜索页的导航入口（`:composeApp` 组装）。第 17 步旧页面在 androidMain，iOS 先放空壳 */
expect fun EntryProviderScope<NavKey>.searchEntries(navigator: Navigator)

/** 搜索的 Koin 定义 */
val searchModule = module {
    viewModel {
        SearchViewModel(
            settings = get(),
            realtime = get(),
            searchHistoryRepository = get(),
            userLocationStore = get(),
            homeCoordinates = get()
        )
    }
}
