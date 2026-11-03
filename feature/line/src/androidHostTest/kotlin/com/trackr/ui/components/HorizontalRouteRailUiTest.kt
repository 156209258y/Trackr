package com.trackr.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.model.BusStop
import com.trackr.core.model.LiveBus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 横排走向条：站名竖排但无障碍读完整站名；点站换候车、点车标跟随。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HorizontalRouteRailUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val stops = listOf(
        BusStop(stopId = "1", name = "汉口路", order = 1),
        BusStop(stopId = "2", name = "人民广场", order = 2),
        BusStop(stopId = "3", name = "上海体育馆", order = 3)
    )

    @Test
    fun rendersAllStops_clickSelectsStation() {
        var selected: BusStop? = null

        composeTestRule.setContent {
            TrackrTheme {
                HorizontalRouteRail(
                    stops = stops,
                    buses = emptyList(),
                    selectedStop = stops[1],
                    selectedBusId = null,
                    scrollState = rememberScrollState(),
                    onSelectStop = { selected = it },
                    onSelectBus = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("汉口路").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("人民广场").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("上海体育馆").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("上海体育馆").performClick()

        assertEquals("上海体育馆", selected?.name)
        assertEquals(3, selected?.order)
    }

    @Test
    fun clickBusChip_followsBus() {
        var followed: LiveBus? = null
        val bus = LiveBus(
            busId = "bus_101",
            plateOrFleetNo = "沪A1234",
            order = 2,
            segmentProgress = 0.5f,
            wgsLat = 31.0,
            wgsLng = 121.0
        )

        composeTestRule.setContent {
            TrackrTheme {
                HorizontalRouteRail(
                    stops = stops,
                    buses = listOf(bus),
                    selectedStop = stops[1],
                    selectedBusId = null,
                    scrollState = rememberScrollState(),
                    onSelectStop = {},
                    onSelectBus = { followed = it }
                )
            }
        }

        // 车牌在窄车标里缩成 4 位
        composeTestRule.onNodeWithText("1234").performClick()

        assertEquals("bus_101", followed?.busId)
    }

    @Test
    fun emptyStops_rendersNothingAndDoesNotCrash() {
        composeTestRule.setContent {
            TrackrTheme {
                HorizontalRouteRail(
                    stops = emptyList(),
                    buses = emptyList(),
                    selectedStop = null,
                    selectedBusId = null,
                    scrollState = rememberScrollState(),
                    onSelectStop = {},
                    onSelectBus = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("汉口路").assertDoesNotExist()
    }
}
