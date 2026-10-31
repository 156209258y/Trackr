package com.trackr.ui.screens.favoritesedit

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackr.core.data.local.LocalMigrationFlags
import com.trackr.core.data.route.FavoriteGroupSelection
import com.trackr.core.data.route.FavoritesGroupDataFix
import com.trackr.core.data.route.RouteAlignmentService
import com.trackr.core.data.route.RouteGroupSaver
import com.trackr.core.model.CandidateLine
import com.trackr.core.model.GroupCategory
import com.trackr.core.model.TransitCity
import com.trackr.core.model.TransitGroup
import com.trackr.core.model.TransitLeg
import com.trackr.core.testing.FakeLocalMigrationFlags
import com.trackr.core.testing.FakeRealtimeRepository
import com.trackr.core.testing.FakeSettingsRepository
import com.trackr.core.testing.InMemoryRouteGroupRepository
import com.trackr.core.testing.MainDispatcherHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 第 17 步：原 `FavoritesGroupViewModelTest`（:androidApp，Robolectric）里测筛选、分组增删改、临时路线转存、
 * 通勤补返程、手动生成返程、一次性数据修复的几条，搬到这里测 [FavoritesEditViewModel]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesEditViewModelTest {

    private val main = MainDispatcherHolder()

    private lateinit var settings: FakeSettingsRepository
    private lateinit var realtime: FakeRealtimeRepository
    private lateinit var groupRepository: InMemoryRouteGroupRepository
    private lateinit var selection: FavoriteGroupSelection
    private lateinit var alignment: RouteAlignmentService
    private lateinit var groupSaver: RouteGroupSaver

    @BeforeTest
    fun setup() {
        main.install()
        settings = FakeSettingsRepository(TransitCity(cityId = "033", cityName = "杭州"))
        realtime = FakeRealtimeRepository(currentCity = { settings.selectedCity.value })
        groupRepository = InMemoryRouteGroupRepository(main.dispatcher)
        selection = FavoriteGroupSelection()
        alignment = RouteAlignmentService(settings, realtime, coordinator = null, ioDispatcher = main.dispatcher)
        groupSaver = RouteGroupSaver(groupRepository, alignment, selection)
    }

    @AfterTest
    fun tearDown() {
        main.reset()
    }

    /** 原 createViewModel：一次性修复开着、不记版本（每次都跑） */
    private fun createViewModel(
        migrationFlags: LocalMigrationFlags? = null,
        enableAutoMigration: Boolean = true
    ): FavoritesEditViewModel = FavoritesEditViewModel(
        groupRepository = groupRepository,
        settings = settings,
        alignment = alignment,
        groupSaver = groupSaver,
        selection = selection,
        dataFix = FavoritesGroupDataFix(groupRepository, alignment, migrationFlags),
        ioDispatcher = main.dispatcher,
        enableAutoMigration = enableAutoMigration
    )

    @Test
    fun testInitialGroupsAndCategoryFiltering() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        val initialList = viewModel.groups.value
        assertEquals(0, initialList.size)

        // 添加一个通勤分组与一个休闲分组
        val commuteGroup = TransitGroup(
            id = "group_commute",
            name = "早间通勤",
            category = GroupCategory.COMMUTE,
            legs = listOf(TransitLeg(legIndex = 0, fromStopName = "X", toStopName = "Y"))
        )
        viewModel.saveGroup(commuteGroup)

        val leisureGroup = TransitGroup(
            id = "group_leisure",
            name = "周末西湖",
            category = GroupCategory.LEISURE,
            legs = listOf(TransitLeg(legIndex = 0, fromStopName = "A", toStopName = "B"))
        )
        viewModel.saveGroup(leisureGroup)
        testScheduler.runCurrent()

        // 早间通勤会自动在 home 分组生成返程路线，因此总数为 3
        assertEquals(3, viewModel.groups.value.size)

        // 切换过滤到 LEISURE
        viewModel.selectCategory(GroupCategory.LEISURE)
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredGroups.value.size)
        assertEquals("周末西湖", viewModel.filteredGroups.value.first().name)

        // 切换回全部 (null)
        viewModel.selectCategory(null)
        testScheduler.runCurrent()
        assertEquals(3, viewModel.filteredGroups.value.size)
    }

    @Test
    fun testCustomFavoriteGroupManagementAndFiltering() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        // 默认种子分组存在
        val defaultFavGroups = viewModel.favoriteGroups.value
        assertEquals(2, defaultFavGroups.size)

        // 创建自定义收藏分组
        viewModel.createFavoriteGroup("商圈购物")
        testScheduler.runCurrent()

        val favGroups = viewModel.favoriteGroups.value
        assertEquals(3, favGroups.size)
        val shoppingGroup = favGroups.find { it.name == "商圈购物" }
        assertNotNull(shoppingGroup)
        val shoppingGroupId = shoppingGroup.id

        // 添加属于该自定义分组的路线
        val route1 = TransitGroup(
            id = "route_mall",
            name = "去万象城",
            category = GroupCategory.CUSTOM,
            customGroupId = shoppingGroupId,
            legs = listOf(TransitLeg(legIndex = 0, fromStopName = "家", toStopName = "万象城"))
        )
        val route2 = TransitGroup(
            id = "route_office",
            name = "上班日常",
            category = GroupCategory.COMMUTE,
            customGroupId = "commute",
            legs = listOf(TransitLeg(legIndex = 0, fromStopName = "家", toStopName = "公司"))
        )
        viewModel.saveGroup(route1)
        viewModel.saveGroup(route2)
        testScheduler.runCurrent()

        // 上班日常自动在 home 生成返程，因此总数为 3
        assertEquals(3, viewModel.groups.value.size)

        // 按照自定义分组筛选
        viewModel.selectFavoriteGroup(shoppingGroupId)
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredGroups.value.size)
        assertEquals("去万象城", viewModel.filteredGroups.value.first().name)

        // 重命名分组
        viewModel.renameFavoriteGroup(shoppingGroupId, "大型商业广场")
        testScheduler.runCurrent()
        assertEquals("大型商业广场", viewModel.favoriteGroups.value.find { it.id == shoppingGroupId }?.name)

        // 删除自定义分组
        viewModel.deleteFavoriteGroup(shoppingGroupId)
        testScheduler.runCurrent()

        // 验证分组已删除，但组内路线仍保留在全部路线中
        assertNull(viewModel.favoriteGroups.value.find { it.id == shoppingGroupId })
        viewModel.selectFavoriteGroup(null)
        testScheduler.runCurrent()
        assertEquals(3, viewModel.groups.value.size)
        val kept = viewModel.groups.value.find { it.id == "route_mall" }
        assertNotNull(kept)
        assertNull(kept.customGroupId)
    }

    @Test
    fun testDeleteAndSaveTemporaryGroup() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        val tempGroup = TransitGroup(
            id = "temp_1",
            name = "地图临时方案",
            category = GroupCategory.TEMPORARY,
            isTemporary = true,
            legs = listOf(TransitLeg(legIndex = 0, fromStopName = "起点", toStopName = "终点"))
        )
        viewModel.saveGroup(tempGroup)
        testScheduler.runCurrent()

        viewModel.saveOrConvertTemporaryGroup(
            groupId = "temp_1",
            newName = "已保存的路线",
            newCategory = GroupCategory.CUSTOM
        )
        testScheduler.runCurrent()

        val saved = groupRepository.getGroupById("temp_1")
        assertNotNull(saved)
        assertFalse(saved.isTemporary)
        assertEquals("已保存的路线", saved.name)
        assertEquals(GroupCategory.CUSTOM, saved.category)

        viewModel.deleteGroup("temp_1")
        testScheduler.runCurrent()
        assertNull(groupRepository.getGroupById("temp_1"))
    }

    @Test
    fun testSaveCommuteRoute_automaticallyBuildsReverseRouteInHome() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        val forwardCommute = TransitGroup(
            id = "group_commute_transfer",
            name = "万达金街 ➔ 西徐村",
            category = GroupCategory.COMMUTE,
            customGroupId = "commute",
            legs = listOf(
                TransitLeg(
                    legIndex = 0,
                    fromStopName = "万达金街",
                    toStopName = "西社小区",
                    candidateLines = listOf(
                        CandidateLine(
                            cityId = "0532",
                            lineId = "59",
                            lineName = "59路",
                            direction = 0,
                            fromStopOrder = 1,
                            toStopOrder = 5,
                            startStopName = "万达金街",
                            endStopName = "西社小区"
                        )
                    ),
                    walkingDistanceMeters = 0
                ),
                TransitLeg(
                    legIndex = 1,
                    fromStopName = "西社小区",
                    toStopName = "西徐村",
                    candidateLines = listOf(
                        CandidateLine(
                            cityId = "0532",
                            lineId = "351",
                            lineName = "351路",
                            direction = 0,
                            fromStopOrder = 1,
                            toStopOrder = 6,
                            startStopName = "西社小区",
                            endStopName = "西徐村"
                        )
                    ),
                    walkingDistanceMeters = 0
                )
            )
        )

        viewModel.saveGroup(forwardCommute)
        testScheduler.runCurrent()

        val allGroups = viewModel.groups.value
        assertEquals(2, allGroups.size)

        // 验证正向路线
        val forward = allGroups.find { it.id == "group_commute_transfer" }
        assertNotNull(forward)
        assertEquals("commute", forward.customGroupId)
        assertEquals("万达金街 ➔ 西徐村", forward.name)

        // 验证自动生成的反向路线
        val reverse = allGroups.find { it.customGroupId == "home" }
        assertNotNull(reverse, "保存通勤路线应自动在'回家线路'中生成反向路线")
        assertEquals("西徐村 ➔ 万达金街", reverse.name)
        assertEquals(2, reverse.legs.size)

        // 第 1 程：351路 西徐村 ➔ 西社小区
        val leg0 = reverse.legs[0]
        assertEquals(0, leg0.legIndex)
        assertEquals("西徐村", leg0.fromStopName)
        assertEquals("西社小区", leg0.toStopName)
        assertEquals("351路", leg0.candidateLines[0].lineName)
        assertEquals(1, leg0.candidateLines[0].direction)

        // 第 2 程：59路 西社小区 ➔ 万达金街
        val leg1 = reverse.legs[1]
        assertEquals(1, leg1.legIndex)
        assertEquals("西社小区", leg1.fromStopName)
        assertEquals("万达金街", leg1.toStopName)
        assertEquals("59路", leg1.candidateLines[0].lineName)
        assertEquals(1, leg1.candidateLines[0].direction)
    }

    @Test
    fun testFilteredGroups_properlySeparatesCommuteAndHome() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        val forwardCommute = TransitGroup(
            id = "group_commute_1",
            name = "万达金街 ➔ 西徐村",
            category = GroupCategory.COMMUTE,
            customGroupId = "commute",
            legs = listOf(
                TransitLeg(legIndex = 0, fromStopName = "万达金街", toStopName = "西社小区"),
                TransitLeg(legIndex = 1, fromStopName = "西社小区", toStopName = "西徐村")
            )
        )
        viewModel.saveGroup(forwardCommute)
        testScheduler.runCurrent()

        // 1. 筛选“上班通勤”
        viewModel.selectFavoriteGroup("commute")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredGroups.value.size)
        assertEquals("万达金街 ➔ 西徐村", viewModel.filteredGroups.value.first().name)

        // 2. 筛选“回家线路”
        viewModel.selectFavoriteGroup("home")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredGroups.value.size)
        assertEquals("西徐村 ➔ 万达金街", viewModel.filteredGroups.value.first().name)

        // 3. 筛选“全部”
        viewModel.selectFavoriteGroup(null)
        testScheduler.runCurrent()
        assertEquals(2, viewModel.filteredGroups.value.size)
    }

    @Test
    fun testCreateReverseGroup_manual() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        val testRoute = TransitGroup(
            id = "custom_route_1",
            name = "青岛火车站 ➔ 五四广场",
            category = GroupCategory.CUSTOM,
            customGroupId = "custom_fav",
            legs = listOf(
                TransitLeg(
                    legIndex = 0,
                    fromStopName = "青岛火车站",
                    toStopName = "五四广场",
                    candidateLines = listOf(
                        CandidateLine(
                            cityId = "0532",
                            lineId = "317",
                            lineName = "317路",
                            direction = 0,
                            fromStopOrder = 1,
                            toStopOrder = 8
                        )
                    )
                )
            )
        )
        viewModel.saveGroup(testRoute)
        testScheduler.runCurrent()

        // 点击生成返程
        viewModel.createReverseGroup(testRoute)
        testScheduler.runCurrent()

        val allGroups = viewModel.groups.value
        assertEquals(2, allGroups.size)
        val reversed = allGroups.find { it.id != "custom_route_1" }
        assertNotNull(reversed)
        assertEquals("五四广场 ➔ 青岛火车站", reversed.name)
        assertEquals("五四广场", reversed.legs.first().fromStopName)
        assertEquals("青岛火车站", reversed.legs.first().toStopName)
        assertEquals(1, reversed.legs.first().candidateLines.first().direction)
    }

    @Test
    fun testRunDataMigration_migratesLegacyCommuteOnceAndRecordsVersion() = runTest(main.dispatcher) {
        val migrationFlags = FakeLocalMigrationFlags()

        // 遗留数据：通勤分类但没有 customGroupId
        val legacyCommute = TransitGroup(
            id = "legacy_commute",
            name = "家 ➔ 公司",
            category = GroupCategory.COMMUTE,
            legs = listOf(TransitLeg(legIndex = 0, fromStopName = "家", toStopName = "公司"))
        )
        groupRepository.addGroup(legacyCommute)
        testScheduler.runCurrent()

        fun newViewModel() = createViewModel(migrationFlags = migrationFlags, enableAutoMigration = false)

        val viewModel = newViewModel()
        viewModel.runDataMigrationIfNeeded().join()
        testScheduler.advanceUntilIdle()

        // 迁移补齐 customGroupId 并生成反向回家路线
        val migrated = groupRepository.getGroupById("legacy_commute")
        assertNotNull(migrated)
        assertEquals("commute", migrated.customGroupId)
        assertEquals(2, viewModel.groups.value.size)
        assertEquals(GroupCategory.COMMUTE, migrated.category)
        assertEquals(FavoritesGroupDataFix.CURRENT_MIGRATION_VERSION, migrationFlags.version)

        // 再写入一条新的遗留数据，已记录版本后不再重复迁移
        groupRepository.addGroup(legacyCommute.copy(id = "legacy_commute_2"))
        testScheduler.runCurrent()
        val secondViewModel = newViewModel()
        secondViewModel.runDataMigrationIfNeeded().join()
        testScheduler.advanceUntilIdle()

        assertNull(groupRepository.getGroupById("legacy_commute_2")?.customGroupId)
        assertEquals(3, secondViewModel.groups.value.size)
    }

    @Test
    fun clearingViewModel_resetsSharedFavoriteGroupSelection() = runTest(main.dispatcher) {
        // 选中的分组放在进程级的 FavoriteGroupSelection 里：ViewModel 清掉（Activity 结束）后不能留到下一次
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { createViewModel(enableAutoMigration = false) } }
        )[FavoritesEditViewModel::class]
        viewModel.selectFavoriteGroup("commute")
        assertEquals("commute", selection.selectedFavoriteGroupId.value)

        store.clear()
        assertNull(selection.selectedFavoriteGroupId.value)
    }
}
