package com.trackr.ui.screens.more

import com.trackr.core.model.EngineMode
import com.trackr.core.model.ScheduledReminder
import com.trackr.core.model.TransitCodeProvider
import com.trackr.core.testing.FakeAmapKeyRepository
import com.trackr.core.testing.FakeSettingsRepository
import com.trackr.core.testing.MainDispatcherHolder
import com.trackr.data.repository.FakeScheduledReminderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MoreViewModelTest {

    private val main = MainDispatcherHolder()

    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeReminderRepository: FakeScheduledReminderRepository
    private lateinit var fakeAmapKeys: FakeAmapKeyRepository
    private lateinit var viewModel: MoreViewModel

    @BeforeTest
    fun setup() {
        main.install()
        fakeSettingsRepository = FakeSettingsRepository()
        fakeReminderRepository = FakeScheduledReminderRepository()
        fakeAmapKeys = FakeAmapKeyRepository()
        viewModel = MoreViewModel(
            repository = fakeSettingsRepository,
            reminderRepository = fakeReminderRepository,
            amapKeys = fakeAmapKeys
        )
    }

    @AfterTest
    fun tearDown() {
        main.reset()
    }

    @Test
    fun testInitialState_reflectsRepositoryValues() = runTest(main.dispatcher) {
        assertEquals(EngineMode.CHELAILE, viewModel.engineMode.value)
        assertEquals(4, viewModel.nearbyStopsCount.value)
        assertEquals(TransitCodeProvider.ASK_ALWAYS, viewModel.preferredTransitCode.value)
        assertTrue(viewModel.reminders.value.isEmpty())

        // 验证统一 MoreUiState
        assertEquals(EngineMode.CHELAILE, viewModel.uiState.value.engineMode)
        assertEquals(4, viewModel.uiState.value.nearbyStopsCount)
        assertEquals(TransitCodeProvider.ASK_ALWAYS, viewModel.uiState.value.preferredTransitCode)
        assertTrue(viewModel.uiState.value.reminders.isEmpty())
    }

    @Test
    fun testSetEngineMode_updatesRepositoryAndState() = runTest(main.dispatcher) {
        viewModel.setEngineMode(EngineMode.MYGOLBS)
        assertEquals(EngineMode.MYGOLBS, viewModel.engineMode.value)
        assertEquals(EngineMode.MYGOLBS, fakeSettingsRepository.engineMode.value)

        viewModel.setEngineMode(EngineMode.CHELAILE)
        assertEquals(EngineMode.CHELAILE, viewModel.engineMode.value)
        assertEquals(EngineMode.CHELAILE, fakeSettingsRepository.engineMode.value)
    }

    @Test
    fun testDataSource_isSingleChoiceOfTwo_andUiStateFollows() = runTest(main.dispatcher) {
        // D1：数据源只有车来了 / 掌上公交两个，没有聚合
        assertEquals(listOf(EngineMode.CHELAILE, EngineMode.MYGOLBS), EngineMode.entries.toList())

        viewModel.setEngineMode(EngineMode.MYGOLBS)
        testScheduler.advanceUntilIdle()
        assertEquals(EngineMode.MYGOLBS, viewModel.uiState.value.engineMode)

        viewModel.setEngineMode(EngineMode.CHELAILE)
        testScheduler.advanceUntilIdle()
        assertEquals(EngineMode.CHELAILE, viewModel.uiState.value.engineMode)
    }

    @Test
    fun testSetNearbyStopsCount_clampsAndUpdatesRepository() = runTest(main.dispatcher) {
        // 正常设置 8
        viewModel.setNearbyStopsCount(8)
        assertEquals(8, viewModel.nearbyStopsCount.value)
        assertEquals(8, fakeSettingsRepository.nearbyStopsCount.value)

        // 超过最大 16，应被限制为 16
        viewModel.setNearbyStopsCount(25)
        assertEquals(16, viewModel.nearbyStopsCount.value)
        assertEquals(16, fakeSettingsRepository.nearbyStopsCount.value)

        // 小于 1，应被限制为 1
        viewModel.setNearbyStopsCount(-5)
        assertEquals(1, viewModel.nearbyStopsCount.value)
        assertEquals(1, fakeSettingsRepository.nearbyStopsCount.value)
    }

    @Test
    fun testSetPreferredTransitCode_updatesRepository() = runTest(main.dispatcher) {
        viewModel.setPreferredTransitCode(TransitCodeProvider.ALIPAY)
        testScheduler.runCurrent()
        assertEquals(TransitCodeProvider.ALIPAY, viewModel.preferredTransitCode.value)
        assertEquals(TransitCodeProvider.ALIPAY, fakeSettingsRepository.preferredTransitCodeProvider.value)

        viewModel.setPreferredTransitCode(TransitCodeProvider.UNIONPAY)
        testScheduler.runCurrent()
        assertEquals(TransitCodeProvider.UNIONPAY, viewModel.preferredTransitCode.value)
        assertEquals(TransitCodeProvider.UNIONPAY, fakeSettingsRepository.preferredTransitCodeProvider.value)
    }

    @Test
    fun testToggleAndDeleteReminder_updatesReminderRepository() = runTest(main.dispatcher) {
        val reminder = ScheduledReminder(
            id = "rem_001",
            enabled = true,
            hour = 8,
            minute = 30,
            cityId = "034",
            cityName = "上海",
            lineId = "line_101",
            lineName = "101路",
            direction = 0,
            endStopName = "人民广场",
            targetStopId = "stop_05",
            targetStopName = "南京东路",
            targetOrder = 5
        )
        fakeReminderRepository.saveReminder(reminder)
        testScheduler.runCurrent()

        assertEquals(1, viewModel.reminders.value.size)
        assertTrue(viewModel.reminders.value.first().enabled)

        // 切换禁用
        viewModel.toggleReminder("rem_001", false)
        testScheduler.runCurrent()
        assertFalse(viewModel.reminders.value.first().enabled)

        // 删除提醒
        viewModel.deleteReminder("rem_001")
        testScheduler.runCurrent()
        assertTrue(viewModel.reminders.value.isEmpty())
    }

    @Test
    fun testLiveNotificationEnabled_toggle() = runTest(main.dispatcher) {
        assertTrue(viewModel.isLiveNotificationEnabled.value)
        assertTrue(viewModel.uiState.value.isLiveNotificationEnabled)

        viewModel.setLiveNotificationEnabled(false)
        testScheduler.runCurrent()

        assertFalse(viewModel.isLiveNotificationEnabled.value)
        assertFalse(viewModel.uiState.value.isLiveNotificationEnabled)

        viewModel.setLiveNotificationEnabled(true)
        testScheduler.runCurrent()

        assertTrue(viewModel.isLiveNotificationEnabled.value)
        assertTrue(viewModel.uiState.value.isLiveNotificationEnabled)
    }

    @Test
    fun testShowOnLockScreen_defaultOnAndToggle() = runTest(main.dispatcher) {
        assertTrue(viewModel.showOnLockScreen.value)

        viewModel.setShowOnLockScreen(false)
        testScheduler.runCurrent()
        assertFalse(viewModel.showOnLockScreen.value)

        viewModel.setShowOnLockScreen(true)
        testScheduler.runCurrent()
        assertTrue(viewModel.showOnLockScreen.value)
    }

    @Test
    fun amapKeys_androidAndWebSlotsAreWrittenSeparately() {
        assertNull(viewModel.amapAndroidKey.value)
        assertNull(viewModel.amapWebKey.value)
        assertFalse(viewModel.hasMapKey.value)

        // 只填 Web Key：有「路线规划」，不画地图；Android 槽不动
        viewModel.saveAmapWebKey("  web_key_abcdef  ")
        assertEquals("web_key_abcdef", viewModel.amapWebKey.value)
        assertNull(viewModel.amapAndroidKey.value)
        assertTrue(viewModel.hasMapKey.value)
        assertFalse(viewModel.canShowMap.value)
        assertTrue(fakeAmapKeys.androidKeyWrites.isEmpty())

        // 再填 Android Key：画地图；Web 槽不被覆盖
        viewModel.saveAmapAndroidKey("android_key_123456")
        assertEquals("android_key_123456", viewModel.amapAndroidKey.value)
        assertEquals("web_key_abcdef", viewModel.amapWebKey.value)
        assertTrue(viewModel.canShowMap.value)
        assertEquals(listOf<String?>("web_key_abcdef"), fakeAmapKeys.webKeyWrites)
    }

    @Test
    fun amapKeys_blankClearsOnlyThatSlot_andBothEmptyHidesRouteTab() {
        viewModel.saveAmapAndroidKey("android_key_123456")
        viewModel.saveAmapWebKey("web_key_abcdef")

        viewModel.saveAmapAndroidKey("   ")
        assertNull(viewModel.amapAndroidKey.value)
        assertEquals("web_key_abcdef", viewModel.amapWebKey.value)
        assertTrue(viewModel.hasMapKey.value)
        assertFalse(viewModel.canShowMap.value)

        viewModel.saveAmapWebKey("")
        assertNull(viewModel.amapWebKey.value)
        assertFalse(viewModel.hasMapKey.value)
    }

    @Test
    fun amapKeyStatusText_coversThreeStates() {
        assertEquals("未配置，不显示「路线规划」", amapKeyStatusText(hasMapKey = false, canShowMap = false))
        assertEquals("只有 Web 服务 Key：能算路线，不显示地图", amapKeyStatusText(hasMapKey = true, canShowMap = false))
        assertEquals("已配置，路线规划显示地图", amapKeyStatusText(hasMapKey = true, canShowMap = true))
    }
}
