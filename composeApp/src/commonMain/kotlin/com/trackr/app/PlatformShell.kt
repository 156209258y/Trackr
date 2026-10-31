package com.trackr.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey

/** 包在 NavDisplay 外面的平台部分。Android：Activity 级的高德地图会话（切页签不销毁地图，§3.4） */
@Composable
internal expect fun PlatformHost(content: @Composable () -> Unit)

/** 首次打开时的定位 / 通知权限说明与申请。Android：原 MainActivity 里那段；iOS 阶段 G 再做 */
@Composable
internal expect fun PermissionOnboarding()

/** 底栏图标。Android 照旧用 Material 图标；iOS 用设计系统的 Compose 资源 */
@Composable
internal expect fun bottomTabIcon(key: ScreenKey): ImageVector

/**
 * 旧收藏页（第 22 步删）。第 17 步起不再是页签，压在首页栈上；它同时用
 * `:feature:favorites` 和 `:feature:route` 的 ViewModel，所以放在这里而不是某个 feature。
 */
internal expect fun EntryProviderScope<NavKey>.favoritesGroupEntries(navigator: Navigator, hasMapKey: Boolean)
