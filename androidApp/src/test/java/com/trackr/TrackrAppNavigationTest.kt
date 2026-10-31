package com.trackr

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.trackr.app.TrackrRoot
import com.trackr.core.model.TransitCity
import com.trackr.core.model.FavoriteStationLine
import com.trackr.core.testing.FakeFavoritesRepository
import com.trackr.core.testing.FakeLocationRepository
import com.trackr.core.testing.FakeRealtimeRepository
import com.trackr.core.testing.FakeSettingsRepository
import com.trackr.core.testing.FakeUserLocationStore
import com.trackr.feature.bus.home.BusHomeViewModel
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
            }
        )
    }

    @Composable
    private fun Content() {
        TrackrRoot()
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
