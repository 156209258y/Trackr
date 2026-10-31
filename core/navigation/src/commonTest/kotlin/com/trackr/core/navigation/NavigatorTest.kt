package com.trackr.core.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.trackr.core.model.DataSourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    private val line49 = ScreenKey.LineDetail(
        cityId = "021",
        lineId = "line_49",
        targetOrder = 4,
        stopId = "stop_004",
        source = DataSourceType.CHELAILE,
        lineName = "49路",
    )
    private val stop = ScreenKey.StopDetail(cityId = "021", stopId = "stop_555", stopName = "陕西南路")

    private fun newState(): NavigationState = NavigationState(
        start = TopLevel.start,
        current = mutableStateOf(TopLevel.start),
        stacks = TopLevel.tabs.associateWith { NavBackStack<NavKey>(it) },
    )

    @Test
    fun initialState_startsAtHomeRoot_withFourTabStacks() {
        val state = newState()
        assertEquals(ScreenKey.Home, state.topLevel)
        assertEquals(ScreenKey.Home, state.currentKey)
        assertTrue(state.isAtTabRoot)
        assertEquals(TopLevel.tabs.toSet(), state.stacks.keys)
        TopLevel.tabs.forEach { tab -> assertEquals(listOf<NavKey>(tab), state.stacks.getValue(tab).toList()) }
        assertEquals(listOf<NavKey>(ScreenKey.Home), state.keysShown())
    }

    @Test
    fun open_pushesOnCurrentTab_andDedupsTop() {
        val state = newState()
        val navigator = Navigator(state)

        navigator.open(ScreenKey.Search)
        navigator.open(line49)
        navigator.open(line49) // 连点 / 重放：栈顶已是它，不再压栈

        assertEquals(listOf(ScreenKey.Home, ScreenKey.Search, line49), state.currentStack.toList())
        assertFalse(state.isAtTabRoot)
        // 别的页签的栈不受影响
        assertEquals(listOf<NavKey>(ScreenKey.More), state.stacks.getValue(ScreenKey.More).toList())
    }

    @Test
    fun open_sameKeyNotOnTop_isPushedAgain() {
        val state = newState()
        val navigator = Navigator(state)

        navigator.open(line49)
        navigator.open(stop)
        navigator.open(line49)

        assertEquals(listOf(ScreenKey.Home, line49, stop, line49), state.currentStack.toList())
    }

    @Test
    fun back_popsChildPagesOneByOne_thenReturnsFalseAtStartRoot() {
        val state = newState()
        val navigator = Navigator(state)
        navigator.open(ScreenKey.Search)
        navigator.open(line49)

        assertTrue(navigator.back())
        assertEquals(ScreenKey.Search, state.currentKey)
        assertTrue(navigator.back())
        assertEquals(ScreenKey.Home, state.currentKey)
        assertFalse(navigator.back(), "首页根页按返回交给系统退出")
        assertEquals(ScreenKey.Home, state.topLevel)
        assertEquals(listOf<NavKey>(ScreenKey.Home), state.currentStack.toList())
    }

    @Test
    fun back_atNonStartTabRoot_returnsToStartTab_keepingItsStack() {
        val state = newState()
        val navigator = Navigator(state)
        navigator.open(line49)
        navigator.selectTab(ScreenKey.More)
        navigator.open(ScreenKey.About)

        assertTrue(navigator.back())
        assertEquals(ScreenKey.More, state.topLevel)
        assertEquals(ScreenKey.More, state.currentKey)

        assertTrue(navigator.back())
        assertEquals(ScreenKey.Home, state.topLevel)
        assertEquals(line49, state.currentKey, "回到首页页签时它的子页还在")
    }

    @Test
    fun selectTab_switchingPreservesEachTabsHistory() {
        val state = newState()
        val navigator = Navigator(state)

        navigator.open(ScreenKey.Search)
        navigator.selectTab(ScreenKey.More)
        navigator.open(ScreenKey.About)
        navigator.selectTab(ScreenKey.RoutePlan)
        navigator.open(line49)
        navigator.selectTab(ScreenKey.FavoritesGroup)
        navigator.open(line49)

        navigator.selectTab(ScreenKey.Home)
        assertEquals(ScreenKey.Search, state.currentKey)
        navigator.selectTab(ScreenKey.More)
        assertEquals(ScreenKey.About, state.currentKey)
        navigator.selectTab(ScreenKey.RoutePlan)
        assertEquals(line49, state.currentKey)
        navigator.selectTab(ScreenKey.FavoritesGroup)
        assertEquals(listOf(ScreenKey.FavoritesGroup, line49), state.currentStack.toList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun selectTab_reselectingCurrentTab_popsToRoot_andEmitsReselect() = runTest {
        val state = newState()
        val navigator = Navigator(state)
        val reselected = mutableListOf<ScreenKey>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { navigator.reselect.toList(reselected) }

        navigator.open(ScreenKey.Search)
        navigator.open(line49)
        navigator.selectTab(ScreenKey.Home)

        assertEquals(listOf<NavKey>(ScreenKey.Home), state.currentStack.toList())
        assertEquals(listOf<ScreenKey>(ScreenKey.Home), reselected)

        // 切到别的页签不算再点
        navigator.selectTab(ScreenKey.More)
        assertEquals(listOf<ScreenKey>(ScreenKey.Home), reselected)
        job.cancel()
    }

    @Test
    fun selectTab_unknownTab_isRejected() {
        val navigator = Navigator(newState())
        assertFailsWith<IllegalArgumentException> { navigator.selectTab(ScreenKey.Search) }
    }

    @Test
    fun stacksInUse_putsStartTabUnderCurrentTab() {
        val state = newState()
        val navigator = Navigator(state)
        navigator.open(line49)
        navigator.selectTab(ScreenKey.More)
        navigator.open(ScreenKey.About)

        assertEquals(listOf(ScreenKey.Home, ScreenKey.More), state.stacksInUse)
        assertEquals(listOf(ScreenKey.Home, line49, ScreenKey.More, ScreenKey.About), state.keysShown(startUnderneath = true))

        navigator.selectTab(ScreenKey.Home)
        assertEquals(listOf(ScreenKey.Home), state.stacksInUse)
        assertEquals(listOf<NavKey>(ScreenKey.Home, line49), state.keysShown(startUnderneath = true))
    }

    @Test
    fun stacksShown_withoutStartUnderneath_showsOnlyCurrentTab() {
        // 第 16 步 MainActivity 用这种：和旧版一样，非首页页签根页没有上一页，按返回交给系统
        val state = newState()
        val navigator = Navigator(state)
        navigator.open(line49)
        navigator.selectTab(ScreenKey.More)

        assertEquals(listOf(ScreenKey.More), state.stacksShown(startUnderneath = false))
        assertEquals(listOf<NavKey>(ScreenKey.More), state.keysShown(startUnderneath = false))

        navigator.open(ScreenKey.About)
        assertEquals(listOf<NavKey>(ScreenKey.More, ScreenKey.About), state.keysShown(startUnderneath = false))

        navigator.selectTab(ScreenKey.Home)
        assertEquals(listOf<NavKey>(ScreenKey.Home, line49), state.keysShown(startUnderneath = false))
    }

    @Test
    fun openInTab_switchesWithoutPoppingAndDedupsReplay() {
        val state = newState()
        val navigator = Navigator(state)
        navigator.open(ScreenKey.Search)
        navigator.selectTab(ScreenKey.More)

        navigator.openInTab(ScreenKey.Home, line49)
        navigator.openInTab(ScreenKey.Home, line49) // 同一个通知 / 小组件 Intent 重放

        assertEquals(ScreenKey.Home, state.topLevel)
        assertEquals(listOf(ScreenKey.Home, ScreenKey.Search, line49), state.currentStack.toList())
        assertTrue(navigator.back())
        assertEquals(ScreenKey.Search, state.currentKey)
    }

    @Test
    fun onTabsChanged_currentTabHidden_clearsItAndReturnsToStart() {
        val state = newState()
        val navigator = Navigator(state)
        navigator.open(ScreenKey.Search)
        navigator.selectTab(ScreenKey.RoutePlan)
        navigator.open(line49)

        navigator.onTabsChanged(setOf(ScreenKey.Home, ScreenKey.FavoritesGroup, ScreenKey.More))

        assertEquals(ScreenKey.Home, state.topLevel)
        assertEquals(ScreenKey.Search, state.currentKey, "起始页签的栈保留")
        assertEquals(listOf<NavKey>(ScreenKey.RoutePlan), state.stacks.getValue(ScreenKey.RoutePlan).toList())
    }

    @Test
    fun onTabsChanged_currentTabStillVisible_isNoOp() {
        val state = newState()
        val navigator = Navigator(state)
        navigator.selectTab(ScreenKey.More)
        navigator.open(ScreenKey.About)

        navigator.onTabsChanged(setOf(ScreenKey.Home, ScreenKey.More))

        assertEquals(ScreenKey.More, state.topLevel)
        assertEquals(ScreenKey.About, state.currentKey)
    }

    @Test
    fun isShowingRootOf_matchesOldShouldPollHomeTabCondition() {
        // 旧算法：selectedTab == "home" && homeBackStack.last() is Home（再加 Activity ≥ STARTED，由页面层判断）
        val state = newState()
        val navigator = Navigator(state)
        assertTrue(state.isShowingRootOf(ScreenKey.Home))

        navigator.open(line49)
        assertFalse(state.isShowingRootOf(ScreenKey.Home), "进详情停轮询")

        navigator.back()
        assertTrue(state.isShowingRootOf(ScreenKey.Home))

        navigator.selectTab(ScreenKey.More)
        assertFalse(state.isShowingRootOf(ScreenKey.Home), "切页签停轮询")
        assertTrue(state.isShowingRootOf(ScreenKey.More))
    }
}
