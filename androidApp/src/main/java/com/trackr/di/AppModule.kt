package com.trackr.di

import android.content.Context
import com.trackr.core.data.provider.TransitProviderCoordinator
import com.trackr.core.data.repository.DefaultLocationRepository
import com.trackr.core.data.repository.FavoritesRepository
import com.trackr.core.data.repository.LocationRepository
import com.trackr.core.data.repository.RealtimeRepository
import com.trackr.core.data.repository.SearchHistoryRepository
import com.trackr.core.data.repository.SettingsRepository
import com.trackr.core.domain.platform.LocationSource
import com.trackr.core.domain.platform.ReminderAlarms
import com.trackr.core.domain.platform.TrackingController
import com.trackr.core.domain.platform.UpdateChecker
import com.trackr.core.navigation.ScreenKey
import com.trackr.core.network.provider.chelaile.ChelaileTransitProvider
import com.trackr.core.network.provider.mygolbs.MygolbsTransitProvider
import com.trackr.core.network.remote.amap.AmapTransitApiClient
import com.trackr.core.network.remote.chelaile.ChelaileApiClient
import com.trackr.core.network.remote.common.createTrackrHttpClient
import com.trackr.core.network.remote.mygolbs.MygolbsApiClient
import com.trackr.data.location.AndroidLocationSource
import com.trackr.data.location.AndroidMonotonicClock
import com.trackr.data.remote.amap.AmapKeyStorage
import com.trackr.data.remote.amap.AmapSdkKeyApplier
import com.trackr.data.remote.amap.AmapSdkTransitService
import com.trackr.data.remote.amap.AndroidAmapSdkKeyApplier
import com.trackr.data.remote.update.GithubUpdateChecker
import com.trackr.data.repository.IRouteGroupRepository
import com.trackr.data.repository.IScheduledReminderRepository
import com.trackr.data.repository.ITransitRepository
import com.trackr.data.repository.RouteGroupRepository
import com.trackr.data.repository.ScheduledReminderRepository
import com.trackr.data.repository.TransitRepository
import com.trackr.service.AndroidTrackingController
import com.trackr.service.ArrivalAlertNotifier
import com.trackr.service.LiveProgressNotifier
import com.trackr.service.TransferAutoSwitcher
import com.trackr.ui.screens.favoritesgroup.FavoritesGroupViewModel
import com.trackr.ui.screens.home.HomeViewModel
import com.trackr.ui.screens.linedetail.LineDetailViewModel
import com.trackr.ui.screens.more.AboutViewModel
import com.trackr.ui.screens.more.MoreViewModel
import com.trackr.ui.screens.stopdetail.StopDetailViewModel
import com.trackr.util.ReminderAlarmScheduler
import com.trackr.widget.FavoritesShortcutWidgetSync
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

/** 应用级协程作用域的限定名：活得和进程一样久，接收器、小组件、收藏同步都用它 */
val AppScopeQualifier = named("app")

/**
 * 应用级依赖。
 *
 * 网络客户端、数据源和三个仓库原来在 TrackrApp.onCreate 里直接 new，现在由 Koin 构建。
 * 三个仓库仍由 TrackrApp.onCreate 按原顺序取一次（startKoin 之后），读盘时机和顺序都和原来一样。
 */
val appModule: Module = module {
    // Ktor（OkHttp 引擎）：总超时 20 秒，连接 12 秒、读 15 秒、写 12 秒，和原来的 OkHttpClient 一样
    single<HttpClient> { createTrackrHttpClient() } onClose { it?.close() }
    single { ChelaileApiClient(get()) }
    single { MygolbsApiClient(get()) }
    single { ChelaileTransitProvider(get()) }
    single { MygolbsTransitProvider(get()) }
    single {
        TransitProviderCoordinator(
            chelaileProvider = get<ChelaileTransitProvider>(),
            mygolbsProvider = get<MygolbsTransitProvider>()
        )
    }
    single { AmapTransitApiClient(get()) }
    single<ITransitRepository> {
        TransitRepository(context = androidContext(), coordinator = get())
    }
    // 拆开的仓库接口（重构计划 §2.5）：第 9 步换存储之前都是同一个 TransitRepository
    single<SettingsRepository> { get<ITransitRepository>() }
    single<FavoritesRepository> { get<ITransitRepository>() }
    single<RealtimeRepository> { get<ITransitRepository>() }
    single<SearchHistoryRepository> { get<ITransitRepository>() }
    single<IScheduledReminderRepository> {
        ScheduledReminderRepository(context = androidContext())
    }
    single<IRouteGroupRepository> {
        RouteGroupRepository(context = androidContext())
    }

    // 应用级作用域：SupervisorJob，一个任务失败不影响别的；Koin 关闭时取消
    single<CoroutineScope>(AppScopeQualifier) { CoroutineScope(SupervisorJob() + Dispatchers.Default) } onClose { it?.cancel() }

    // 收藏真变了才同步桌面快捷方式和小组件（原来挂在 MainActivity 的组合里）
    single { FavoritesShortcutWidgetSync(androidContext(), get(), get(AppScopeQualifier)) }

    // 原来是 object 的有状态组件，改成类后由 Koin 提供唯一实例；旧 object 转发到这里
    single {
        LiveProgressNotifier(
            liveNotificationSetting = { get<ITransitRepository>().isLiveNotificationEnabled.value },
            nextLegLineNameOf = { legIndex -> get<TransferAutoSwitcher>().nextLegLineName(legIndex) }
        )
    }
    single { ArrivalAlertNotifier(get()) }
    single { TransferAutoSwitcher(get()) }
    single<ReminderAlarms> { ReminderAlarmScheduler(androidContext()) }
    single { AmapKeyStorage(androidContext()) }

    // 平台能力：ViewModel 不再直接拿 Context
    single<LocationSource> { AndroidLocationSource(androidContext()) }
    single<LocationRepository> { DefaultLocationRepository(source = get(), clock = AndroidMonotonicClock) }
    single<TrackingController> { AndroidTrackingController(androidContext()) }
    single<AmapSdkKeyApplier> { AndroidAmapSdkKeyApplier(androidContext()) }

    // 原来每个 AboutViewModel 都新建一个检查器，这里保持 factory
    factory<UpdateChecker> { GithubUpdateChecker(get()) }
}

/** 6 个 ViewModel。带参数的页面用 `parametersOf(key)` 传入导航 key */
val viewModelModule: Module = module {
    viewModel {
        HomeViewModel(
            repository = get(),
            locationRepository = get(),
            trackingController = get()
        )
    }
    viewModel {
        val context = androidContext()
        FavoritesGroupViewModel(
            groupRepository = get(),
            transitRepository = get(),
            coordinator = get(),
            amapApiClient = get(),
            amapSdkService = runCatching { AmapSdkTransitService(context) }.getOrNull(),
            prefs = runCatching { context.getSharedPreferences(TRACKR_PREFS, Context.MODE_PRIVATE) }.getOrNull(),
            amapKeyPrefs = get<AmapKeyStorage>().prefs(),
            amapSdkKeyApplier = get(),
            locationRepository = get()
        )
    }
    viewModel { (key: ScreenKey.LineDetail) ->
        LineDetailViewModel(
            cityId = key.cityId,
            initialLineId = key.lineId,
            initialTargetOrder = key.targetOrder.takeIf { it > 0 },
            initialStationId = key.stopId.takeIf { it.isNotBlank() },
            initialSource = key.source,
            lineNameHint = key.lineName,
            repository = get(),
            reminderRepository = get()
        )
    }
    viewModel { (key: ScreenKey.StopDetail) ->
        StopDetailViewModel(
            cityId = key.cityId,
            stopId = key.stopId,
            initialStopName = key.stopName,
            physicalStId = key.physicalStId,
            namesakeStId = key.namesakeStId,
            stationLat = key.lat,
            stationLng = key.lng,
            repository = get()
        )
    }
    viewModel { MoreViewModel(androidApplication(), get(), get()) }
    viewModel { AboutViewModel(androidApplication(), get()) }
}

/** 启动时装载的全部 Koin 模块；`KoinGraphVerifyTest` 逐个校验 */
val appModules: List<Module> = listOf(appModule, viewModelModule)

private const val TRACKR_PREFS = "trackr_prefs"
