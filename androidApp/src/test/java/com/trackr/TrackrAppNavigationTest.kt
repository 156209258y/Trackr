package com.trackr

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.trackr.app.TrackrRoot
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.TransitCity
import com.trackr.core.navigation.ScreenKey
import com.trackr.core.navigation.deeplink.DeepLink
import com.trackr.core.navigation.deeplink.DeepLinkParser
import com.trackr.core.navigation.deeplink.DeepLinkUris
import com.trackr.core.model.FavoriteStationLine
import com.trackr.core.testing.FakeFavoritesRepository
import com.trackr.core.testing.FakeLocationRepository
import com.trackr.core.testing.FakeRealtimeRepository
import com.trackr.core.testing.FakeSettingsRepository
import com.trackr.core.testing.FakeUserLocationStore
import com.trackr.data.repository.FakeScheduledReminderRepository
import com.trackr.feature.bus.home.BusHomeViewModel
import com.trackr.ui.screens.linedetail.LineDetailViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 第 17 步：首页轮询开关从 MainActivity 的 shouldPollHome 挪进首页 Route 的 LifecycleStartEffect（重构计划 §4.4）。
 * 用真实的 TrackrRoot（App 启动时装好的 Koin 图，首页 ViewModel 换成接 Fake 的）走一遍：
 * 进子页 / 切页签后首页停轮询，回来时离上次刷新超过一个周期（15 秒）就立即补刷一次，没超过就不补。
 *
 * 第 18 步：deep link 冷启动（首帧就带着链接）/ 热启动（App 已开着、停在别处）都落成合成返回栈 `[首页, 线路详情]`（§3.6）。
 */
// 屏幕给大一点：默认的小屏上收藏区在底栏下面，点不到
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class TrackrAppNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val city = TransitCity(cityId = "034", cityName = "上海")
    private val settings = FakeSettingsRepository(city)
    private val realtime = FakeRealtimeRepository(currentCity = { settings.selectedCity.value })
    private var now = 1_000_000L
    private lateinit var homeViewModel: BusHomeViewModel

    /** 线路详情页按哪个 Key 建的 ViewModel（证明 deep link 打开的是哪条线） */
    private val openedLines = mutableListOf<ScreenKey.LineDetail>()

    @Before
    fun setUp() {
        // 已授权：不弹首次定位说明（它是另一个窗口，会挡住点击）
        shadowOf(ApplicationProvider.getApplicationContext<TrackrApp>()).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        loadKoinModules(
            module {
                viewModel {
                    BusHomeViewModel(
                        settings = settings,
                        // 有一条收藏，首页才显示「编辑」
                        favorites = FakeFavoritesRepository(initialFavorites = listOf(SAMPLE_FAVORITE)),
                        realtime = realtime,
                        userLocationStore = FakeUserLocationStore(),
                        locationRepository = FakeLocationRepository(),
                        trackingController = get(),
                        trackingRepository = get(),
                        transitCodeLauncher = get(),
                        enableAutoPolling = true,
                        elapsedRealtimeMillis = { now }
                    )
                }
                // 线路详情接 Fake 实时数据，不发真请求
                viewModel { (key: ScreenKey.LineDetail) ->
                    openedLines += key
                    LineDetailViewModel(
                        cityId = key.cityId,
                        initialLineId = key.lineId,
                        initialTargetOrder = key.targetOrder.takeIf { it > 0 },
                        initialStationId = key.stopId.takeIf { it.isNotBlank() },
                        initialSource = key.source,
                        lineNameHint = key.lineName,
                        settings = settings,
                        favorites = FakeFavoritesRepository(),
                        realtime = realtime,
                        reminderRepository = FakeScheduledReminderRepository(),
                        trackingRepository = get(),
                        trackingController = get(),
                        lineStopsCache = get(),
                        enableAutoPolling = false
                    )
                }
            }
        )
    }

    /** 和 MainActivity 一样：链接放进一个状态，TrackrRoot 落完回调清掉 */
    private var deepLink by mutableStateOf<DeepLink?>(null)
    private var consumedLinks = 0

    @Composable
    private fun Content() {
        TrackrRoot(
            deepLink = deepLink,
            onDeepLinkConsumed = {
                consumedLinks++
                deepLink = null
            }
        )
        // TrackrRoot 把 Activity 当作 Activity 级的 ViewModelStoreOwner：这里取到的和首页是同一个实例
        homeViewModel = koinViewModel(viewModelStoreOwner = composeTestRule.activity)
    }

    private fun launch() {
        composeTestRule.setContent { Content() }
        composeTestRule.waitForIdle()
    }

    @After
    fun tearDown() {
        if (::homeViewModel.isInitialized) homeViewModel.pausePolling()
    }

    private fun pressBack() {
        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    @Test
    fun homeAtRoot_isPolling() {
        launch()
        assertTrue("首页显示着就在轮询", homeViewModel.isPollingActive)
    }

    @Test
    fun switchToMoreTab_stopsHomePolling_andBackAfterOneInterval_catchesUpImmediately() {
        launch()
        composeTestRule.onNodeWithText("更多").performClick()
        composeTestRule.waitForIdle()
        assertFalse("切到「更多」后首页停轮询", homeViewModel.isPollingActive)

        val callsBefore = realtime.nearbyStopsCalls.size
        now += 16_000L
        composeTestRule.onNodeWithText("首页").performClick()
        composeTestRule.waitForIdle()

        assertTrue("回到首页恢复轮询", homeViewModel.isPollingActive)
        assertEquals("离上次刷新超过 15 秒：立即补刷一次", callsBefore + 1, realtime.nearbyStopsCalls.size)
    }

    @Test
    fun openSearch_stopsHomePolling_andBackWithinInterval_doesNotRefreshImmediately() {
        launch()
        composeTestRule.onNodeWithText("搜线路、车站").performClick()
        composeTestRule.waitForIdle()
        assertFalse("进搜索页（首页栈上的子页）后首页停轮询", homeViewModel.isPollingActive)

        val callsBefore = realtime.nearbyStopsCalls.size
        now += 5_000L
        pressBack()

        assertTrue("返回首页恢复轮询", homeViewModel.isPollingActive)
        assertEquals("不到 15 秒：不补刷", callsBefore, realtime.nearbyStopsCalls.size)
    }

    @Test
    fun favoritesGroup_isChildPageOfHome_andBackReturnsHome() {
        launch()
        // 「收藏」不再是页签：首页收藏的「编辑」把旧收藏页压在首页栈上（新编辑页第 22 步）
        // 底栏页签带 Selected 语义；不应再有叫「收藏」的页签
        val favoriteTabs = composeTestRule.onAllNodes(
            hasText("收藏") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).fetchSemanticsNodes()
        assertTrue("底栏不应再有「收藏」页签", favoriteTabs.isEmpty())
        composeTestRule.onNodeWithText("编辑").performClick()
        composeTestRule.waitForIdle()
        assertFalse("收藏页盖住首页：首页停轮询", homeViewModel.isPollingActive)

        pressBack()
        composeTestRule.onNodeWithText("编辑").assertExists()
        assertTrue(homeViewModel.isPollingActive)
    }

    @Test
    fun restoredOntoAnotherTab_newHomeViewModelDoesNotPoll() {
        // 进程被杀后恢复到「更多」：首页 ViewModel 是新建的（默认开着轮询），首页 Route 没组合，不能一直在后台轮询
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { Content() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("更多").performClick()
        composeTestRule.waitForIdle()
        val before = homeViewModel

        // 清掉 Activity 级的 ViewModel，模拟进程重建后重新创建
        composeTestRule.runOnIdle { composeTestRule.activity.viewModelStore.clear() }
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        assertNotSame("恢复后是新建的首页 ViewModel", before, homeViewModel)
        composeTestRule.onNodeWithText("编辑").assertDoesNotExist()
        assertFalse("首页没显示：新建的首页 ViewModel 不轮询", homeViewModel.isPollingActive)

        composeTestRule.onNodeWithText("首页").performClick()
        composeTestRule.waitForIdle()
        assertTrue("切回首页开始轮询", homeViewModel.isPollingActive)
    }

    // ---- 第 18 步：deep link（合成返回栈，§3.6） ----

    private val line49 = ScreenKey.LineDetail(
        cityId = "034",
        lineId = "line_49",
        targetOrder = 4,
        stopId = "stop_004",
        source = DataSourceType.MYGOLBS,
        lineName = "49路"
    )

    /** 小组件 / 快捷方式 / 提醒通知发的那种链接，经解析器得到 */
    private val line49Link: DeepLink =
        requireNotNull(DeepLinkParser.parse(DeepLinkUris.line("034", "line_49", DataSourceType.MYGOLBS, "stop_004", 4, "49路")))

    private fun assertOnLineDetail() {
        assertEquals("打开的是链接里那条线", line49, openedLines.lastOrNull())
        composeTestRule.onNodeWithText("编辑").assertDoesNotExist()
        composeTestRule.onNodeWithText("更多").assertDoesNotExist() // 子页不显示底栏
    }

    private fun assertOnHomeRoot() {
        composeTestRule.onNodeWithText("编辑").assertExists()
        composeTestRule.onNodeWithText("更多").assertExists()
    }

    @Test
    fun coldStart_lineDeepLink_opensLineDetailOverHomeRoot_andBackReturnsToHomeRoot() {
        deepLink = line49Link
        launch()

        assertOnLineDetail()
        assertEquals("一次性消费", 1, consumedLinks)
        assertFalse("线路页盖住首页：首页停轮询", homeViewModel.isPollingActive)

        pressBack()
        assertOnHomeRoot()
        assertTrue(homeViewModel.isPollingActive)
    }

    @Test
    fun warmStart_onAnotherTab_switchesToHomeTabAndOpensLineDetail() {
        launch()
        composeTestRule.onNodeWithText("更多").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { deepLink = line49Link }
        composeTestRule.waitForIdle()

        assertOnLineDetail()
        pressBack()
        assertOnHomeRoot()
    }

    @Test
    fun warmStart_onHomeSubPage_resetsHomeStack_soBackSkipsTheOldSubPage() {
        launch()
        composeTestRule.onNodeWithText("搜线路、车站").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("编辑").assertDoesNotExist() // 在搜索页

        composeTestRule.runOnIdle { deepLink = line49Link }
        composeTestRule.waitForIdle()
        assertOnLineDetail()

        // 合成返回栈：[首页, 线路详情]，返回直接回首页根页（main 上会先回到搜索页）
        pressBack()
        assertOnHomeRoot()
    }

    @Test
    fun replayedDeepLink_doesNotStackASecondLineDetail() {
        deepLink = line49Link
        launch()
        assertOnLineDetail()

        // 同一个 Intent 再来一次（比如又点了一次小组件）
        composeTestRule.runOnIdle { deepLink = line49Link }
        composeTestRule.waitForIdle()
        assertEquals(2, consumedLinks)
        assertOnLineDetail()

        pressBack()
        assertOnHomeRoot()
    }

    @Test
    fun chooseBoardedBusLink_beforeItsPageExists_leavesStackAlone() {
        // 「上了哪辆？」弹层第 21 步才做：现在只把 App 拉到前台
        launch()
        composeTestRule.runOnIdle { deepLink = DeepLink.ChooseBoardedBus("s-1") }
        composeTestRule.waitForIdle()

        assertEquals(1, consumedLinks)
        assertOnHomeRoot()
        assertTrue(openedLines.isEmpty())
    }

    private companion object {
        val SAMPLE_FAVORITE = FavoriteStationLine(
            id = "fav_1",
            cityId = "034",
            cityName = "上海",
            lineId = "l1",
            lineName = "49路",
            startStop = "A",
            endStop = "B",
            targetStopId = "s1",
            targetStopName = "人民广场",
            targetOrder = 2
        )
    }
}
