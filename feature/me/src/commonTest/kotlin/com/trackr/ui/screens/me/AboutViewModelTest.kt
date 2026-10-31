package com.trackr.ui.screens.me

import com.trackr.core.model.AppUpdateInfo
import com.trackr.core.model.UpdateCheckResult
import com.trackr.core.testing.FakeUpdateChecker
import com.trackr.core.testing.MainDispatcherHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private val main = MainDispatcherHolder()

    @BeforeTest
    fun setup() {
        main.install()
    }

    @AfterTest
    fun tearDown() {
        main.reset()
    }

    @Test
    fun initialState_isIdle() {
        val fakeChecker = FakeUpdateChecker(UpdateCheckResult.UpToDate("0.0.2"))
        val viewModel = AboutViewModel(
            updateChecker = fakeChecker,
            versionProvider = { 2 to "0.0.2" }
        )

        assertEquals(AboutUpdateUiState.Idle, viewModel.uiState.value.update)
        // Android：有检查更新，版本号来自安装包
        assertTrue(viewModel.uiState.value.canCheckUpdates)
        assertTrue(viewModel.uiState.value.showLiveUpdateNote)
        assertEquals("0.0.2", viewModel.uiState.value.versionName)
    }

    @Test
    fun noUpdateChecker_hidesCheckUpdates_andCheckDoesNothing() = runTest(main.dispatcher) {
        // iOS 没有应用内更新（§2.9）：不显示「检查更新」，调了也不变
        val viewModel = AboutViewModel(updateChecker = null, versionProvider = { 2 to "0.0.2" })

        assertFalse(viewModel.uiState.value.canCheckUpdates)
        assertFalse(viewModel.uiState.value.showLiveUpdateNote)
        viewModel.onEvent(AboutEvent.CheckForUpdates)
        advanceUntilIdle()
        assertEquals(AboutUpdateUiState.Idle, viewModel.uiState.value.update)
    }

    @Test
    fun checkForUpdates_whileChecking_doesNotStartSecondCheck() = runTest(main.dispatcher) {
        val fakeChecker = FakeUpdateChecker(UpdateCheckResult.UpToDate("0.0.2"))
        val viewModel = AboutViewModel(updateChecker = fakeChecker, versionProvider = { 2 to "0.0.2" })

        viewModel.onEvent(AboutEvent.CheckForUpdates)
        viewModel.onEvent(AboutEvent.CheckForUpdates)
        advanceUntilIdle()

        assertEquals(1, fakeChecker.checkCount)
    }

    @Test
    fun checkForUpdates_whenHasUpdate_updatesStateToHasUpdate() = runTest(main.dispatcher) {
        val updateInfo = AppUpdateInfo(
            versionCode = 3,
            versionName = "0.0.3",
            changelog = "全新发布",
            downloadUrl = "https://github.com/5151561/Trackr/releases",
            forceUpdate = false
        )
        val fakeChecker = FakeUpdateChecker(UpdateCheckResult.HasUpdate(updateInfo, isForceUpdate = false))
        val viewModel = AboutViewModel(
            updateChecker = fakeChecker,
            versionProvider = { 2 to "0.0.2" }
        )

        viewModel.checkForUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value.update
        assertTrue(state is AboutUpdateUiState.HasUpdate)
        val hasUpdate = state as AboutUpdateUiState.HasUpdate
        assertEquals("0.0.3", hasUpdate.updateInfo.versionName)
        assertEquals(false, hasUpdate.isForce)
        assertEquals(1, fakeChecker.checkCount)
    }

    @Test
    fun checkForUpdates_whenUpToDate_updatesStateToUpToDate() = runTest(main.dispatcher) {
        val fakeChecker = FakeUpdateChecker(UpdateCheckResult.UpToDate("0.0.2"))
        val viewModel = AboutViewModel(
            updateChecker = fakeChecker,
            versionProvider = { 2 to "0.0.2" }
        )

        viewModel.checkForUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value.update
        assertTrue(state is AboutUpdateUiState.UpToDate)
        assertEquals("0.0.2", (state as AboutUpdateUiState.UpToDate).currentVersionName)
    }

    @Test
    fun checkForUpdates_whenError_updatesStateToError() = runTest(main.dispatcher) {
        val fakeChecker = FakeUpdateChecker(UpdateCheckResult.Error("网络连接超时"))
        val viewModel = AboutViewModel(
            updateChecker = fakeChecker,
            versionProvider = { 2 to "0.0.2" }
        )

        viewModel.checkForUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value.update
        assertTrue(state is AboutUpdateUiState.Error)
        assertEquals("网络连接超时", (state as AboutUpdateUiState.Error).message)
    }

    @Test
    fun dismissUpdateDialog_resetsStateToIdle() = runTest(main.dispatcher) {
        val fakeChecker = FakeUpdateChecker(UpdateCheckResult.UpToDate("0.0.2"))
        val viewModel = AboutViewModel(
            updateChecker = fakeChecker,
            versionProvider = { 2 to "0.0.2" }
        )

        viewModel.checkForUpdates()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.update is AboutUpdateUiState.UpToDate)

        viewModel.dismissUpdateDialog()
        assertEquals(AboutUpdateUiState.Idle, viewModel.uiState.value.update)
    }
}
