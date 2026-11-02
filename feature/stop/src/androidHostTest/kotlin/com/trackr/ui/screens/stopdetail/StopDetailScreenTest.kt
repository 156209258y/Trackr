package com.trackr.ui.screens.stopdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.domain.arrival.ArrivalDisplay
import com.trackr.core.domain.arrival.ArrivalTone
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.NearbyStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** v3 站点详情页（第 21 步）：无状态页面，喂手写的状态、记下发出的事件 */
@RunWith(RobolectricTestRunner::class)
class StopDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val events = mutableListOf<StopDetailEvent>()

    private fun arrival(value: String, unit: String?, detail: String, tone: ArrivalTone = ArrivalTone.Normal) =
        ArrivalDisplay(value = value, unit = unit, detail = detail, short = value, tone = tone, spoken = value)

    private val row325 = StopLineRowUi(
        key = "r325", lineId = "L325", lineName = "325", endStop = "科技园", hours = "06:30–22:30",
        arrival = arrival("4", "分钟", "还有 2 站"), isFavorite = true, selectable = true,
        targetOrder = 5, stopId = "st1", source = DataSourceType.CHELAILE
    )
    private val row19 = row325.copy(
        key = "r19", lineId = "L19", lineName = "19", endStop = "蛇口港", hours = "06:00–23:00", isFavorite = false,
        arrival = arrival("2", "分钟", "还有 1 站", ArrivalTone.Go)
    )

    private val base = StopDetailUiState(
        isLoading = false,
        stopName = "科技园站",
        stops = listOf(NearbyStop(stopId = "st1", name = "科技园站"), NearbyStop(stopId = "st2", name = "科技园站")),
        platformLabels = listOf("往 科技园", "往 蛇口港"),
        subtitle = "120米 · 2 条线",
        rows = listOf(row325, row19),
        transferText = "可换乘：地铁 1 号线"
    )

    private fun setContent(state: StopDetailUiState) {
        composeTestRule.setContent {
            TrackrTheme {
                StopDetailScreen(state = state, onEvent = { events += it })
            }
        }
    }

    @Test
    fun normalMode_showsPlatformsRowsAndNoViewAllLink() {
        setContent(base)
        composeTestRule.onNodeWithText("往 科技园").assertIsDisplayed()
        composeTestRule.onNodeWithText("往 蛇口港").assertIsDisplayed()
        composeTestRule.onNodeWithText("120米 · 2 条线").assertIsDisplayed()
        composeTestRule.onNodeWithText("→ 科技园").assertIsDisplayed()
        composeTestRule.onNodeWithText("06:30–22:30", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("可换乘：地铁 1 号线").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTextCount("查看全线") == 0)
    }

    @Test
    fun normalMode_rowOpensLine_platformSelects_buttonsSendEvents() {
        setContent(base)
        composeTestRule.onNodeWithText("→ 蛇口港").performClick()
        composeTestRule.onNodeWithText("往 蛇口港").performClick()
        composeTestRule.onNodeWithText("选几条一起等").performClick()
        composeTestRule.onNodeWithContentDescription("乘车码").performClick()
        composeTestRule.onNodeWithContentDescription("取消收藏").performClick()
        assertEquals(
            listOf(
                StopDetailEvent.OpenLine(row19),
                StopDetailEvent.SelectPlatform(1),
                StopDetailEvent.StartSelecting,
                StopDetailEvent.OpenTransitCode,
                StopDetailEvent.ToggleFavorite("r325")
            ),
            events
        )
    }

    @Test
    fun selectingMode_countsAndActions() {
        setContent(base.copy(selecting = true, selectedLineKeys = setOf("r325", "r19")))
        composeTestRule.onNodeWithText("已选 2 条").assertIsDisplayed()
        composeTestRule.onNodeWithText("科技园站 · 往 科技园").assertIsDisplayed()
        composeTestRule.onNodeWithText("一起等这 2 条").assertIsEnabled().performClick()
        composeTestRule.onNodeWithText("收藏").performClick()
        composeTestRule.onNodeWithText("→ 科技园").performClick()
        assertEquals(
            listOf(StopDetailEvent.StartWatching, StopDetailEvent.SaveSelectionAsRoute, StopDetailEvent.ToggleLine("r325")),
            events
        )
    }

    @Test
    fun selectingMode_nothingChecked_buttonsDisabled() {
        setContent(base.copy(selecting = true))
        composeTestRule.onNodeWithText("一起等这 0 条").assertIsNotEnabled()
        composeTestRule.onNodeWithText("收藏").assertIsNotEnabled()
    }

    @Test
    fun watchMode_showsFastestFirstAndBoardButton() {
        val watch = WatchTogetherUi(
            lineCount = 2,
            lines = listOf(
                WatchLineUi("L325", "325", "科技园", arrival("1", "分钟", "还有 1 站", ArrivalTone.Go), "粤B·58921", "还有 1 站 · 粤B·58921"),
                WatchLineUi("LM194", "M194", "西丽", arrival("6", "分钟", "还有 3 站"), "", "还有 3 站")
            )
        )
        setContent(base.copy(watch = watch))
        composeTestRule.onNodeWithText("一起等 2 条").assertIsDisplayed()
        composeTestRule.onNodeWithText("325 先到").assertIsDisplayed()
        composeTestRule.onNodeWithText("还有 1 站 · 粤B·58921").assertIsDisplayed()
        // 行可能被底栏盖住（列表留了底栏高度的 contentPadding，测试里不滑），直接走点击语义
        composeTestRule.onNodeWithText("再加一条").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("我上车了").performClick()
        composeTestRule.onNodeWithText("结束").performClick()
        assertEquals(
            listOf(StopDetailEvent.AddLine, StopDetailEvent.ConfirmBoarded, StopDetailEvent.StopWatching),
            events
        )
    }

    @Test
    fun watchMode_onBoard_hidesBoardButton() {
        val watch = WatchTogetherUi(lineCount = 2, lines = emptyList(), isOnBoard = true, boardedTitle = "已上 325", boardedDetail = "还有 5 站到终点")
        setContent(base.copy(watch = watch))
        composeTestRule.onNodeWithText("已上 325").assertIsDisplayed()
        composeTestRule.onNodeWithText("还有 5 站到终点").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTextCount("我上车了") == 0)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
