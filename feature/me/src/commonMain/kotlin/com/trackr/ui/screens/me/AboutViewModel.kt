package com.trackr.ui.screens.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.core.domain.platform.UpdateChecker
import com.trackr.core.model.AppUpdateInfo
import com.trackr.core.model.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 关于页版本更新 UI 状态
 */
sealed interface AboutUpdateUiState {
    data object Idle : AboutUpdateUiState
    data object Checking : AboutUpdateUiState
    data class HasUpdate(val updateInfo: AppUpdateInfo, val isForce: Boolean) : AboutUpdateUiState
    data class UpToDate(val currentVersionName: String) : AboutUpdateUiState
    data class Error(val message: String) : AboutUpdateUiState
}

/**
 * @param canCheckUpdates 有没有应用内「检查更新」：只有 Android 有（§2.9，iOS 走 App Store）
 * @param showLiveUpdateNote 显示「实时通知支持哪些系统」那段说明（只对 Android 有意义，跟 [canCheckUpdates] 同一个平台条件）
 */
data class AboutUiState(
    val versionName: String = "",
    val canCheckUpdates: Boolean = false,
    val showLiveUpdateNote: Boolean = false,
    val update: AboutUpdateUiState = AboutUpdateUiState.Idle
)

sealed interface AboutEvent {
    data object CheckForUpdates : AboutEvent
    data object DismissUpdateDialog : AboutEvent
    data object Back : AboutEvent
}

/** 项目主页 */
const val PROJECT_URL: String = "https://github.com/5151561/Trackr"

/**
 * @param updateChecker 应用内更新检查；平台没有（iOS）时为 null，页面不显示「检查更新」
 * @param versionProvider 当前安装包的版本号（code to name）；Koin 里由 `AppVersionInfo` 给出
 *   （Android：androidApp 的 `androidAppVersion(context)`，即原 `getDefaultAppVersion`）
 */
class AboutViewModel(
    private val updateChecker: UpdateChecker?,
    private val versionProvider: () -> Pair<Int, String> = { 1 to "0.0.1" }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AboutUiState(
            versionName = versionProvider().second,
            canCheckUpdates = updateChecker != null,
            showLiveUpdateNote = updateChecker != null
        )
    )
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    fun onEvent(event: AboutEvent) {
        when (event) {
            AboutEvent.CheckForUpdates -> checkForUpdates()
            AboutEvent.DismissUpdateDialog -> dismissUpdateDialog()
            AboutEvent.Back -> Unit
        }
    }

    fun checkForUpdates() {
        val checker = updateChecker ?: return
        if (_uiState.value.update is AboutUpdateUiState.Checking) return
        setUpdate(AboutUpdateUiState.Checking)

        viewModelScope.launch {
            val (currentVersionCode, currentVersionName) = versionProvider()
            val result = checker.checkForUpdates(
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName
            )

            setUpdate(
                when (result) {
                    is UpdateCheckResult.HasUpdate ->
                        AboutUpdateUiState.HasUpdate(result.updateInfo, result.isForceUpdate)
                    is UpdateCheckResult.UpToDate ->
                        AboutUpdateUiState.UpToDate(result.currentVersionName)
                    is UpdateCheckResult.Error ->
                        AboutUpdateUiState.Error(result.message)
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        setUpdate(AboutUpdateUiState.Idle)
    }

    private fun setUpdate(update: AboutUpdateUiState) {
        _uiState.update { it.copy(update = update) }
    }
}
