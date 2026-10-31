package com.trackr.feature.me

import com.trackr.core.domain.platform.AppVersionInfo
import com.trackr.ui.screens.more.AboutViewModel
import com.trackr.ui.screens.more.MoreViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 「更多 / 关于」的 ViewModel（第 17 步从 androidApp 的 viewModelModule 搬来） */
val meModule = module {
    viewModel { MoreViewModel(repository = get(), reminderRepository = get(), amapKeys = get()) }
    viewModel {
        val version = get<AppVersionInfo>()
        AboutViewModel(updateChecker = get(), versionProvider = { version.code to version.name })
    }
}
