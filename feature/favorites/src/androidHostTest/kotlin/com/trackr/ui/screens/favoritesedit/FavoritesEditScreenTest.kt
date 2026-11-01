package com.trackr.ui.screens.favoritesedit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.model.CandidateLine
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.FavoriteGroup
import com.trackr.core.model.GroupCategory
import com.trackr.core.model.TransitGroup
import com.trackr.core.model.TransitLeg
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** v3 编辑收藏页（第 22 步）：分组新建、路线打开、删除 */
@RunWith(RobolectricTestRunner::class)
class FavoritesEditScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val events = mutableListOf<FavoritesEditEvent>()

    private val group = TransitGroup(
        id = "g1",
        name = "家 → 公司",
        category = GroupCategory.COMMUTE,
        legs = listOf(
            TransitLeg(
                legIndex = 0,
                fromStopName = "深大北门",
                toStopName = "高新园",
                candidateLines = listOf(
                    CandidateLine(
                        cityId = "034",
                        lineId = "L325",
                        lineName = "325",
                        source = DataSourceType.CHELAILE,
                        fromStopOrder = 3,
                        toStopOrder = 8
                    )
                )
            )
        ),
        customGroupId = "fav1"
    )
    private val favoriteGroup = FavoriteGroup(id = "fav1", name = "上班", sortOrder = 0)

    private fun setContent() {
        composeTestRule.setContent {
            TrackrTheme {
                FavoritesEditScreen(
                    groups = listOf(group),
                    favoriteGroups = listOf(favoriteGroup),
                    onEvent = { events += it }
                )
            }
        }
    }

    @Test
    fun showsGroupsAndRoutes() {
        setContent()

        composeTestRule.onNodeWithText("分组").assertIsDisplayed()
        composeTestRule.onNodeWithText("上班").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 条路线").assertIsDisplayed()
        composeTestRule.onNodeWithText("家 → 公司").assertIsDisplayed()
    }

    @Test
    fun createGroup_asksNameAndSendsEvent() {
        setContent()

        composeTestRule.onNodeWithTag("btn_create_favorite_group").performClick()
        composeTestRule.onNodeWithTag("input_group_name").performTextInput("健身房")
        composeTestRule.onNodeWithText("创建").performClick()

        assertEquals(
            listOf(FavoritesEditEvent.CreateGroup("健身房")),
            events.filterIsInstance<FavoritesEditEvent.CreateGroup>()
        )
    }

    @Test
    fun tappingRoute_opensRouteDetail() {
        setContent()

        composeTestRule.onNodeWithTag("route_row_g1").performClick()
        assertEquals(FavoritesEditEvent.OpenRoute("g1"), events.single())
    }

    @Test
    fun deleteRoute_asksConfirmationFirst() {
        setContent()

        composeTestRule.onNodeWithTag("btn_delete_route_g1").performClick()
        composeTestRule.onNodeWithText("删除这条收藏？").assertIsDisplayed()
        composeTestRule.onNodeWithText("删除").performClick()

        assertEquals(FavoritesEditEvent.DeleteRoute("g1"), events.single())
    }
}
