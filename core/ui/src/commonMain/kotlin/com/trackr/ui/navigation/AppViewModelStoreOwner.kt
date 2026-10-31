package com.trackr.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelStoreOwner

/**
 * 根组合（Activity）的 [ViewModelStoreOwner]，由 `TrackrRoot` 在 `NavDisplay` 外面提供。
 *
 * 第 17 步过渡用：原来 `MainActivity` 在 NavDisplay 外取的几个 ViewModel（首页、搜索、规划 / 收藏）
 * 跨页共享同一个实例，拆进各功能模块后仍取 Activity 级（`koinViewModel(viewModelStoreOwner = …)`），
 * 行为和原来一样；阶段 D 逐页重写时改成 entry 级。
 */
val LocalAppViewModelStoreOwner = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("LocalAppViewModelStoreOwner 没有提供：要在 TrackrRoot 里用")
}
