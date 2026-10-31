package com.trackr.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.ui.navigation.dropUnlessResumed
import com.trackr.ui.screens.favoritesedit.FavoritesEditViewModel
import com.trackr.ui.screens.favoritesgroup.FavoritesGroupScreen
import com.trackr.ui.screens.routedetail.RouteDetailViewModel
import com.trackr.ui.navigation.LocalAppViewModelStoreOwner
import com.trackr.feature.bus.home.BusHomeViewModel
import com.trackr.ui.screens.routeplan.ProvideAmapMapSession
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal actual fun PlatformHost(content: @Composable () -> Unit) = ProvideAmapMapSession(content)

@Composable
internal actual fun bottomTabIcon(key: ScreenKey): ImageVector = when (key) {
    ScreenKey.Home -> Icons.Default.DirectionsBus
    ScreenKey.RoutePlan -> Icons.AutoMirrored.Filled.AltRoute
    ScreenKey.More -> Icons.Default.MoreHoriz
    else -> error("$key 不是顶层页签")
}

/** 申请定位与通知权限（原 MainActivity 里那段，原样搬来） */
@Composable
internal actual fun PermissionOnboarding() {
    val context = LocalContext.current
    // 首页 ViewModel 是 Activity 级的，和首页拿到的是同一个实例
    val homeViewModel: BusHomeViewModel = koinViewModel(viewModelStoreOwner = LocalAppViewModelStoreOwner.current)

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
}

/** 旧收藏页（第 22 步删），压在首页栈上 */
internal actual fun EntryProviderScope<NavKey>.favoritesGroupEntries(navigator: Navigator, hasMapKey: Boolean) {
    entry<ScreenKey.FavoritesGroup> {
        val appOwner = LocalAppViewModelStoreOwner.current
        val favoritesEditViewModel: FavoritesEditViewModel = koinViewModel(viewModelStoreOwner = appOwner)
        val routeDetailViewModel: RouteDetailViewModel = koinViewModel(viewModelStoreOwner = appOwner)
        FavoritesGroupScreen(
            viewModel = favoritesEditViewModel,
            routeDetailViewModel = routeDetailViewModel,
            onNavigateToRoutePlan = if (hasMapKey) {
                dropUnlessResumed { navigator.selectTab(ScreenKey.RoutePlan) }
            } else null,
            // 第 17 步起它总是首页栈的子页（栈深 > 1），左上角返回总在
            onNavigateBack = dropUnlessResumed { navigator.popChildPage() },
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
}
