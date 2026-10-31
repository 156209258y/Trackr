package com.trackr.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.trackr.core.data.repository.AmapKeyRepository
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.model.DataSourceType
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.core.navigation.TrackrTransitions
import com.trackr.core.navigation.rememberNavigationState
import com.trackr.feature.bus.busEntries
import com.trackr.feature.favorites.favoritesEntries
import com.trackr.feature.line.lineEntries
import com.trackr.feature.me.meEntries
import com.trackr.feature.route.routeEntries
import com.trackr.feature.search.searchEntries
import com.trackr.feature.stop.stopEntries
import com.trackr.ui.navigation.LocalAppViewModelStoreOwner
import com.trackr.ui.navigation.PendingDeepLink
import com.trackr.ui.navigation.rememberVisibleTabs
import com.trackr.ui.screens.favoritesedit.FavoritesEditViewModel
import com.trackr.feature.bus.home.BusHomeViewModel
import com.trackr.ui.screens.routedetail.RouteDetailViewModel
import com.trackr.ui.screens.routeplan.RoutePlanViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * 应用的根 Composable（重构计划 §2.2、§3.3）：主题、底栏、各页签的返回栈和 NavDisplay。
 * 第 17 步从 `MainActivity` 原样搬来；`MainActivity` 只剩 Intent 处理和 `setContent { TrackrRoot(…) }`。
 */
@Composable
fun TrackrRoot(
    pendingDeepLink: PendingDeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    TrackrTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // 原来在 NavDisplay 外取、跨页共享的 ViewModel（首页、搜索、规划 / 收藏）仍挂在这一层
            val appViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
                "TrackrRoot 要放在有 ViewModelStoreOwner 的地方（Activity / ComposeUIViewController）"
            }
            CompositionLocalProvider(LocalAppViewModelStoreOwner provides appViewModelStoreOwner) {
                // Android：地图会话（切页签不销毁地图）；iOS：直接显示
                PlatformHost {
                    TrackrNavApp(
                        pendingDeepLink = pendingDeepLink,
                        onDeepLinkConsumed = onDeepLinkConsumed
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackrNavApp(
    pendingDeepLink: PendingDeepLink?,
    onDeepLinkConsumed: () -> Unit
) {
    // 3 个页签各一条返回栈 + 当前页签，跨进程重建恢复（:core:navigation，重构计划 §3.3）
    val navigationState = rememberNavigationState()
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // 原来在这里（NavDisplay 外）取、跨页共享的 ViewModel：首页（首页 / 权限回调）、FavoritesGroupViewModel（规划页 / 收藏页）。
    // 拆开后仍在这里先取一次 Activity 级的实例，创建时机和原来一样（收藏的自动 ETA 刷新、一次性数据修复都从这时开始）
    val appViewModelStoreOwner = LocalAppViewModelStoreOwner.current
    val homeViewModel = koinViewModel<BusHomeViewModel>(viewModelStoreOwner = appViewModelStoreOwner)
    // 首页 ViewModel 建好就开着轮询；恢复到别的页签、或首页栈顶是子页时首页 Route 不组合，没人关它。
    // 和原来 shouldPollHome 第一帧一样：首页根页没显示就先关掉，之后由首页 Route 的 LifecycleStartEffect 开关
    LaunchedEffect(homeViewModel) {
        if (!navigationState.isShowingRootOf(ScreenKey.Home)) homeViewModel.setPollingActive(false)
    }
    koinViewModel<RoutePlanViewModel>(viewModelStoreOwner = appViewModelStoreOwner)
    koinViewModel<RouteDetailViewModel>(viewModelStoreOwner = appViewModelStoreOwner)
    koinViewModel<FavoritesEditViewModel>(viewModelStoreOwner = appViewModelStoreOwner)

    // D2：没有高德 Key 就不显示「路线规划」；Key 被清空且正停在那个页签时回首页、清掉它的栈
    val visibleTabs = rememberVisibleTabs(koinInject<AmapKeyRepository>(), navigator)
    val hasMapKey = ScreenKey.RoutePlan in visibleTabs

    // 收藏变化后同步桌面快捷方式与小组件：第 4 步起在应用级作用域里做（见 FavoritesShortcutWidgetSync）
    // 首页轮询开关：第 17 步起由首页 Route 的 LifecycleStartEffect 控制（重构计划 §4.4）

    // 申请定位与通知权限（Android：首次打开时弹说明再申请；iOS 阶段 G 再做）
    PermissionOnboarding()

    // 处理来自桌面小组件、快捷方式或通知栏的直接跳转（一次性消费，防止重复入栈）
    LaunchedEffect(pendingDeepLink) {
        val deepLink = pendingDeepLink ?: return@LaunchedEffect
        val source = runCatching { DataSourceType.valueOf(deepLink.sourceName) }
            .getOrDefault(DataSourceType.CHELAILE)
        val lineDetailKey = ScreenKey.LineDetail(
            cityId = deepLink.cityId,
            lineId = deepLink.lineId,
            targetOrder = deepLink.targetOrder,
            stopId = deepLink.stopId,
            source = source,
            lineName = deepLink.lineName
        )
        navigator.openInTab(ScreenKey.Home, lineDetailKey)
        onDeepLinkConsumed()
    }

    val isTopLevel = navigationState.isAtTabRoot

    // 控制滑动时底部导航栏平滑收起与展开
    var isNavigationBarVisible by rememberSaveable { mutableStateOf(true) }

    // 切换 Tab 或从子页面返回根页面时，确保导航栏重置为显示状态
    LaunchedEffect(isTopLevel, navigationState.topLevel) {
        if (isTopLevel) {
            isNavigationBarVisible = true
        }
    }

    val nestedScrollConnection = remember(isTopLevel) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isTopLevel) {
                    // 手指向上推，浏览下方更多内容 -> 向下滑出隐藏导航栏
                    if (consumed.y < -8f) {
                        isNavigationBarVisible = false
                    }
                    // 手指向下拉，回看上方内容或在顶部下拉 -> 向上滑入显示导航栏
                    else if (consumed.y > 8f || available.y > 8f) {
                        isNavigationBarVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    val navBarTranslationY by animateFloatAsState(
        targetValue = if (isNavigationBarVisible) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navBarTranslationY"
    )

    Scaffold(
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            AnimatedVisibility(
                visible = isTopLevel,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(280)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(280)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                NavigationBar(
                    modifier = Modifier.graphicsLayer {
                        translationY = navBarTranslationY * size.height
                    }
                ) {
                    visibleTabs.forEach { key ->
                        val label = bottomTabLabel(key)
                        NavigationBarItem(
                            selected = navigationState.topLevel == key,
                            // 点当前页签回到它的根页；点别的页签切过去，各自的返回栈保留
                            onClick = dropUnlessResumed { navigator.selectTab(key) },
                            icon = {
                                Icon(
                                    imageVector = bottomTabIcon(key),
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { _ ->
        NavDisplay(
            // 只显示当前页签的栈（和第 16 步之前一样）：非首页页签根页按返回交给系统，切页签走横向滑动
            entries = navigationState.toDecoratedEntries(startUnderneath = false, entryProvider = entryProvider {
                busEntries(navigator)
                routeEntries(navigator)
                favoritesGroupEntries(navigator, hasMapKey = hasMapKey)
                searchEntries(navigator)
                lineEntries(navigator)
                stopEntries(navigator)
                meEntries(navigator)
                favoritesEntries(navigator)
            }),
            onBack = { navigator.back() },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = TrackrTransitions.push,
            popTransitionSpec = TrackrTransitions.pop,
            predictivePopTransitionSpec = TrackrTransitions.predictivePop
        )
    }
}

/** 底栏文字（第 17 步页签改成 3 个，文字照旧；改名留给第 19 / 25 步） */
internal fun bottomTabLabel(key: ScreenKey): String = when (key) {
    ScreenKey.Home -> "首页"
    ScreenKey.RoutePlan -> "路线规划"
    ScreenKey.More -> "更多"
    else -> error("$key 不是顶层页签")
}
