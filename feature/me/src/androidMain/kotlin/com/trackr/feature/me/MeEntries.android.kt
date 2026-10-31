package com.trackr.feature.me

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.screens.more.AboutScreen
import com.trackr.ui.screens.more.MoreScreen
import com.trackr.ui.screens.more.MoreViewModel
import com.trackr.ui.navigation.dropUnlessResumed
import org.koin.compose.viewmodel.koinViewModel

/** 原 MainActivity 里 `entry<ScreenKey.More>`、`entry<ScreenKey.About>` 两块 */
actual fun EntryProviderScope<NavKey>.meEntries(navigator: Navigator) {
    entry<ScreenKey.More> {
        val moreViewModel: MoreViewModel = koinViewModel()
        MoreScreen(
            viewModel = moreViewModel,
            onNavigateToAbout = dropUnlessResumed {
                navigator.open(ScreenKey.About)
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

    entry<ScreenKey.About> {
        AboutScreen(
            onNavigateBack = dropUnlessResumed {
                navigator.popChildPage()
            }
        )
    }
}
