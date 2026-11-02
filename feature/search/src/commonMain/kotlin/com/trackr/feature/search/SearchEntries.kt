package com.trackr.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.TrackrNavigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.screens.search.SearchEvent
import com.trackr.ui.screens.search.SearchScreen
import com.trackr.ui.screens.search.SearchViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 搜索页的导航入口（`:composeApp` 组装） */
fun EntryProviderScope<NavKey>.searchEntries(navigator: TrackrNavigator) {
    entry<ScreenKey.Search> { SearchRoute(navigator) }
}

/** 搜索的 Koin 定义 */
val searchModule = module {
    viewModel {
        SearchViewModel(
            settings = get(),
            realtime = get(),
            searchHistoryRepository = get(),
            userLocationStore = get(),
            homeCoordinates = get(),
            savedStateHandle = get()
        )
    }
}

/**
 * 搜索页 Route：取 entry 级的 [SearchViewModel]（重构计划 §3.4），把 [SearchEvent] 翻成 VM 调用和导航。
 * 跳转只在页面 RESUMED 时做，防连点重复入栈（和原来的 dropUnlessResumed 一样）。
 */
@Composable
fun SearchRoute(
    navigator: TrackrNavigator,
    viewModel: SearchViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    fun whenResumed(block: () -> Unit) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block()
    }
    /** 点进结果前把当前词记进最近搜过（原来就是这样） */
    fun rememberQuery() {
        val query = viewModel.uiState.value.searchQuery
        if (query.isNotBlank()) viewModel.addSearchHistory(query.trim())
    }

    SearchScreen(
        state = state,
        // 输入框直接读 ViewModel 的 Compose 快照，中文输入法组字不会被 Flow 重组打断。
        // 不再 `state.copy(searchQuery = …)` 覆盖状态：两份真值会让「输入框文字」和「上一次真正发起的搜索词」混成一个字段。
        queryText = viewModel.queryText,
        onEvent = { event ->
            when (event) {
                is SearchEvent.QueryChanged -> viewModel.updateSearchQuery(event.query)
                SearchEvent.Submit -> rememberQuery()
                is SearchEvent.HistoryClicked -> {
                    viewModel.updateSearchQuery(event.query)
                    viewModel.addSearchHistory(event.query)
                }
                is SearchEvent.RemoveHistory -> viewModel.removeSearchHistory(event.query)
                SearchEvent.ClearHistory -> viewModel.clearSearchHistory()
                is SearchEvent.LineClicked -> {
                    rememberQuery()
                    val direction = event.direction
                    whenResumed {
                        navigator.open(
                            ScreenKey.LineDetail(
                                cityId = viewModel.uiState.value.currentCity.cityId,
                                lineId = direction.lineId,
                                targetOrder = 1,
                                stopId = "",
                                source = direction.source,
                                lineName = direction.name
                            )
                        )
                    }
                }
                is SearchEvent.StopClicked -> {
                    rememberQuery()
                    val stop = event.stop
                    whenResumed {
                        navigator.open(
                            ScreenKey.StopDetail(
                                cityId = viewModel.uiState.value.currentCity.cityId,
                                stopId = stop.stopId,
                                stopName = stop.name,
                                physicalStId = stop.physicalStId.orEmpty(),
                                namesakeStId = stop.namesakeStId.orEmpty(),
                                lat = stop.wgsLat,
                                lng = stop.wgsLng
                            )
                        )
                    }
                }
                SearchEvent.Back -> whenResumed { navigator.popChildPage() }
            }
        }
    )
}
