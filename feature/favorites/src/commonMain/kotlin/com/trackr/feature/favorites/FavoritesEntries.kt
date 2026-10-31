package com.trackr.feature.favorites

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.PendingScreen
import com.trackr.ui.screens.favoritesedit.FavoritesEditViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * 编辑收藏页的导航入口（第 22 步做页面，第 17 步只占位、还没有入口）。
 * 旧收藏页（`ScreenKey.FavoritesGroup`）在 `:composeApp`：它同时要本模块和 `:feature:route` 的 ViewModel。
 */
fun EntryProviderScope<NavKey>.favoritesEntries(navigator: Navigator) {
    entry<ScreenKey.FavoritesEdit> { PendingScreen("编辑收藏") }
}

/** 编辑收藏的 Koin 定义（原 `FavoritesGroupViewModel` 拆出来的分组增删改、筛选部分） */
val favoritesModule = module {
    viewModel {
        FavoritesEditViewModel(
            groupRepository = get(),
            settings = get(),
            alignment = get(),
            groupSaver = get(),
            selection = get(),
            dataFix = get()
        )
    }
}
