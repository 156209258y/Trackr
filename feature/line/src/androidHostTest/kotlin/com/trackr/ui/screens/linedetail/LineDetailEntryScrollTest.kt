package com.trackr.ui.screens.linedetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.model.BusLineDirection
import com.trackr.core.model.BusStop
import com.trackr.core.model.LiveBus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 从某个站点点进线路详情：整页列表（含走向条）直接跳到那一站，不是从第 1 站开始。
 * 没有带站序的入口（搜索、收藏等）保持从顶部开始。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LineDetailEntryScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val stops = (1..30).map { order ->
        BusStop(stopId = "s$order", name = "S" + order.toString().padStart(2, '0'), order = order)
    }

    private val line = BusLineDirection(
        lineId = "line_1",
        name = "1路",
        startStop = "起点站",
        endStop = "终点站",
        firstTime = "06:00",
        lastTime = "22:00"
    )

    private fun stateOf(entryStopOrder: Int?): LineDetailUiState = LineDetailUiState(
        lineNameHint = line.name,
        isLoading = false,
        static = LineStatic(line = line, reverseDirection = null, stops = stops),
        selectedStop = stops.firstOrNull { it.order == entryStopOrder } ?: stops.first(),
        entryStopOrder = entryStopOrder
    )

    private fun show(state: LineDetailUiState) {
        composeTestRule.setContent {
            TrackrTheme {
                LineDetailScreen(state = state, onEvent = {})
            }
        }
    }

    @Test
    fun openedFromStation_jumpsRailToThatStation() {
        show(stateOf(entryStopOrder = 20))

        composeTestRule.onNodeWithText("S20").assertIsDisplayed()
        composeTestRule.onNodeWithText("S01").assertDoesNotExist()
    }

    @Test
    fun openedFromStation_keepsCardVisibleWhileRailJumps() {
        show(stateOf(entryStopOrder = 20))

        // 走向条跳到第 20 站，主卡仍在顶上显示这一站的候车信息
        composeTestRule.onNodeWithText("S20站上车").assertIsDisplayed()
        composeTestRule.onNodeWithText("S20").assertIsDisplayed()
        composeTestRule.onNodeWithText("S01").assertDoesNotExist()
    }

    @Test
    fun openedWithoutStation_startsFromTop() {
        show(stateOf(entryStopOrder = null))

        composeTestRule.onNodeWithText("S01").assertIsDisplayed()
        composeTestRule.onNodeWithText("S20").assertDoesNotExist()
    }

    @Test
    fun busListMode_stillShowsBusRowsInFixedCard() {
        // 主卡固定 + 最多六成高：车辆列表在卡内显示，不被裁掉
        val state = stateOf(entryStopOrder = 20).copy(
            panelMode = PanelMode.BUS_LIST,
            live = LineLive(
                buses = listOf(LiveBus(busId = "b1", plateOrFleetNo = "沪A1234", order = 21, etaSeconds = 120))
            )
        )
        show(state)

        composeTestRule.onNodeWithText("沪A1234").assertIsDisplayed()
    }
}
