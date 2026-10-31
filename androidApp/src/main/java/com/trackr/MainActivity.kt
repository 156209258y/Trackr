package com.trackr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.TransitCodeProvider
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.core.navigation.TopLevel
import com.trackr.core.navigation.TrackrTransitions
import com.trackr.core.navigation.rememberNavigationState
import com.trackr.ui.navigation.PendingDeepLink
import com.trackr.ui.navigation.dropUnlessResumed
import com.trackr.ui.screens.favoritesgroup.FavoritesGroupScreen
import com.trackr.ui.screens.favoritesgroup.FavoritesGroupViewModel
import com.trackr.ui.screens.home.HomeScreen
import com.trackr.ui.screens.home.HomeViewModel
import com.trackr.ui.screens.linedetail.LineDetailScreen
import com.trackr.ui.screens.linedetail.LineDetailViewModel
import com.trackr.ui.screens.more.AboutScreen
import com.trackr.ui.screens.more.MoreScreen
import com.trackr.ui.screens.more.MoreViewModel
import com.trackr.ui.screens.routeplan.AmapMapSession
import com.trackr.ui.screens.routeplan.RoutePlanScreen
import com.trackr.ui.screens.search.SearchScreen
import com.trackr.ui.screens.stopdetail.StopDetailScreen
import com.trackr.ui.screens.stopdetail.StopDetailViewModel
import com.trackr.util.TransitCodeHelper
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<PendingDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        handleTransitCodeIntent(intent)

        // 仅在首次启动时消费 Deep Link（避免旋转屏幕等配置变更时重复追加到栈顶）
        if (savedInstanceState == null) {
            extractAndConsumeDeepLink(intent)
        }

        setContent {
            TrackrTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrackrNavApp(
                        pendingDeepLink = pendingDeepLink,
                        onDeepLinkConsumed = { pendingDeepLink = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTransitCodeIntent(intent)
        extractAndConsumeDeepLink(intent)
    }

    private fun extractAndConsumeDeepLink(intent: Intent?) {
        if (intent == null) return
        val cityId = intent.getStringExtra("extra_city_id")
        val lineId = intent.getStringExtra("extra_line_id")
        if (!cityId.isNullOrBlank() && !lineId.isNullOrBlank()) {
            pendingDeepLink = PendingDeepLink(
                cityId = cityId,
                lineId = lineId,
                targetOrder = intent.getIntExtra("extra_target_order", 0),
                stopId = intent.getStringExtra("extra_stop_id").orEmpty(),
                sourceName = intent.getStringExtra("extra_source").orEmpty(),
                lineName = intent.getStringExtra("extra_line_name").orEmpty()
            )
            // 一次性消费：移除 Intent 中的 Deep Link 参数，杜绝后续重放
            intent.removeExtra("extra_city_id")
            intent.removeExtra("extra_line_id")
            intent.removeExtra("extra_target_order")
            intent.removeExtra("extra_stop_id")
            intent.removeExtra("extra_source")
            intent.removeExtra("extra_line_name")
        }
    }

    private fun handleTransitCodeIntent(intent: Intent?) {
        if (intent?.action == "com.trackr.action.OPEN_TRANSIT_CODE" ||
            intent?.action == "com.github.u5151561.trackr.action.OPEN_TRANSIT_CODE"
        ) {
            val providerStr = intent.getStringExtra("extra_provider")
            val provider = if (providerStr == "alipay") TransitCodeProvider.ALIPAY else TransitCodeProvider.UNIONPAY
            TransitCodeHelper.openTransitCode(this, provider)
            intent.removeExtra("extra_provider")
            intent.action = null
        }
    }
}

/** 底栏页签（顺序同 [TopLevel.tabs]）。 */
private data class BottomTab(val key: ScreenKey, val label: String, val icon: ImageVector)

private val bottomTabs: List<BottomTab> = TopLevel.tabs.map { key ->
    when (key) {
        ScreenKey.Home -> BottomTab(key, "首页", Icons.Default.DirectionsBus)
        ScreenKey.RoutePlan -> BottomTab(key, "路线规划", Icons.AutoMirrored.Filled.AltRoute)
        ScreenKey.FavoritesGroup -> BottomTab(key, "收藏", Icons.Default.Star)
        ScreenKey.More -> BottomTab(key, "更多", Icons.Default.MoreHoriz)
        else -> error("$key 不是顶层页签")
    }
}

@Composable
fun TrackrNavApp(
    pendingDeepLink: PendingDeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current

    // 地图实例托管到 Activity 级会话：切走 tab 只把地图从窗口摘下，回来复用同一实例，
    // 不会重新初始化 GL / 重新拉瓦片 / 丢失相机位置。
    val mapSession = remember(context) {
        (context as? Activity)?.let { AmapMapSession(it) }
    }
    DisposableEffect(mapSession) {
        onDispose { mapSession?.destroy() }
    }

    // 按 Home / 锁屏时暂停地图（内置定位 4s 一次 + GL 渲染），回到前台再恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapSession) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> mapSession?.onHostPause()
                Lifecycle.Event.ON_RESUME -> mapSession?.onHostResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 4 个页签各一条返回栈 + 当前页签，跨进程重建恢复（:core:navigation，重构计划 §3.3）
    val navigationState = rememberNavigationState()
    val navigator = remember(navigationState) { Navigator(navigationState) }
    // 子页左上角返回：只在当前栈里退一页，不切页签
    val popChildPage: () -> Unit = {
        if (navigationState.currentStack.size > 1) {
            navigator.back()
        }
    }

    val homeViewModel: HomeViewModel = koinViewModel()
    val favoritesGroupViewModel: FavoritesGroupViewModel = koinViewModel()

    // 首页轮询只在 App 前台、当前 tab 是首页且首页在回退栈顶时进行；切 tab / 进详情 / 回桌面即暂停
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val shouldPollHome = navigationState.isShowingRootOf(ScreenKey.Home) &&
        lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(shouldPollHome) {
        homeViewModel.setPollingActive(shouldPollHome)
    }

    // 收藏变化后同步桌面快捷方式与小组件：第 4 步起在应用级作用域里做（见 FavoritesShortcutWidgetSync）

    // 申请定位与通知权限
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            homeViewModel.refreshLocationAndNearby()
        }
    }

    var showLocationDisclosureDialog by rememberSaveable { mutableStateOf(false) }

    val needsLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        if (needsLocation) {
            showLocationDisclosureDialog = true
        } else if (needsNotification) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    if (showLocationDisclosureDialog) {
        AlertDialog(
            onDismissRequest = {
                showLocationDisclosureDialog = false
                if (needsNotification) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "位置信息权限说明",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "为了向您展示附近的公交站点、候车站距离以及途经线路的实时到站动态，追客需要在您使用应用期间获取设备的大致或精准位置信息。\n\n我们承诺：位置信息仅在本地用于附近公交站点匹配，不会用于追踪个人身份或与任何第三方共享。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLocationDisclosureDialog = false
                        val needed = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (needsNotification) {
                            needed.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(needed.toTypedArray())
                    }
                ) {
                    Text("同意并授权")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLocationDisclosureDialog = false
                        if (needsNotification) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    }
                ) {
                    Text("暂不授权")
                }
            }
        )
    }

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
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = navigationState.topLevel == tab.key,
                            // 点当前页签回到它的根页；点别的页签切过去，各自的返回栈保留
                            onClick = dropUnlessResumed { navigator.selectTab(tab.key) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { _ ->
        NavDisplay(
            // 只显示当前页签的栈（和第 16 步之前一样）：非首页页签根页按返回交给系统，切页签走横向滑动
            entries = navigationState.toDecoratedEntries(startUnderneath = false, entryProvider = entryProvider {
                entry<ScreenKey.Home> {
                    HomeScreen(
                        viewModel = homeViewModel,
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
                        onNavigateToFavoritesGroup = dropUnlessResumed {
                            navigator.selectTab(ScreenKey.FavoritesGroup)
                        }
                    )
                }

                entry<ScreenKey.RoutePlan> {
                    RoutePlanScreen(
                        viewModel = favoritesGroupViewModel,
                        mapSession = mapSession,
                        onNavigateToFavorites = dropUnlessResumed {
                            navigator.selectTab(ScreenKey.FavoritesGroup)
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
                        }
                    )
                }

                entry<ScreenKey.FavoritesGroup> {
                    FavoritesGroupScreen(
                        viewModel = favoritesGroupViewModel,
                        onNavigateToRoutePlan = dropUnlessResumed {
                            navigator.selectTab(ScreenKey.RoutePlan)
                        },
                        onNavigateBack = if (navigationState.currentStack.size > 1) {
                            dropUnlessResumed { popChildPage() }
                        } else null,
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
                        }
                    )
                }

                entry<ScreenKey.Search> {
                    SearchScreen(
                        viewModel = homeViewModel,
                        onNavigateBack = dropUnlessResumed {
                            popChildPage()
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

                entry<ScreenKey.LineDetail> { key ->
                    val detailViewModel: LineDetailViewModel = koinViewModel(
                        key = "detail_${key.cityId}_${key.lineId}_${key.targetOrder}"
                    ) { parametersOf(key) }

                    LineDetailScreen(
                        viewModel = detailViewModel,
                        onNavigateBack = dropUnlessResumed {
                            popChildPage()
                        }
                    )
                }

                entry<ScreenKey.StopDetail> { key ->
                    val stopDetailViewModel: StopDetailViewModel = koinViewModel(
                        key = "stop_${key.cityId}_${key.stopId}_${key.physicalStId}_${key.stopName}"
                    ) { parametersOf(key) }

                    StopDetailScreen(
                        viewModel = stopDetailViewModel,
                        onNavigateBack = dropUnlessResumed {
                            popChildPage()
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
                            popChildPage()
                        }
                    )
                }
            }),
            onBack = { navigator.back() },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = TrackrTransitions.push,
            popTransitionSpec = TrackrTransitions.pop,
            predictivePopTransitionSpec = TrackrTransitions.predictivePop
        )
    }
}
