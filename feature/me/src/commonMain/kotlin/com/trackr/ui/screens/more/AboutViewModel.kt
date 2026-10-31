package com.trackr.ui.screens.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.core.domain.platform.UpdateChecker
import com.trackr.core.model.AppUpdateInfo
import com.trackr.core.model.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @param versionProvider 当前安装包的版本号（code to name）；Koin 里由 `AppVersionInfo` 给出
 *   （Android：androidApp 的 `androidAppVersion(context)`，即原 `getDefaultAppVersion`）
 */
class AboutViewModel(
    private val updateChecker: UpdateChecker,
    private val versionProvider: () -> Pair<Int, String> = { 1 to "0.0.1" }
) : ViewModel() {

    private val _updateState = MutableStateFlow<AboutUpdateUiState>(AboutUpdateUiState.Idle)
    val updateState: StateFlow<AboutUpdateUiState> = _updateState.asStateFlow()

    fun checkForUpdates() {
        if (_updateState.value is AboutUpdateUiState.Checking) return
        _updateState.value = AboutUpdateUiState.Checking

        viewModelScope.launch {
            val (currentVersionCode, currentVersionName) = versionProvider()
            val result = updateChecker.checkForUpdates(
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName
            )

            _updateState.value = when (result) {
                is UpdateCheckResult.HasUpdate ->
                    AboutUpdateUiState.HasUpdate(result.updateInfo, result.isForceUpdate)
                is UpdateCheckResult.UpToDate ->
                    AboutUpdateUiState.UpToDate(result.currentVersionName)
                is UpdateCheckResult.Error ->
                    AboutUpdateUiState.Error(result.message)
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateState.value = AboutUpdateUiState.Idle
    }
}
