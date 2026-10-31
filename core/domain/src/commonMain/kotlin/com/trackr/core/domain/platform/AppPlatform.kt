package com.trackr.core.domain.platform

/*
 * 第 17 步从 `:androidApp` 下沉的几个平台能力接口：功能模块（`:feature:*`）看不到 `:androidApp`，
 * 要用的平台能力都经这里的接口注入，实现留在 `:androidApp` 或对应 feature 的 androidMain。
 */

/** 把高德 Android Key 注入 SDK（Android：`AmapSdkManager.applyApiKey`；iOS 第 38 步） */
fun interface AmapSdkKeyApplier {
    fun apply(customKey: String?)
}

/**
 * 应用启动时初始化地图 SDK（隐私合规 + 注入 Key）。
 * [builtInAndroidKey] / [builtInWebKey] 是构建时注入的 Key（Android：`BuildConfig.AMAP_*`），用户没填时用它们。
 */
fun interface MapSdkInitializer {
    fun initialize(userAndroidKey: String?, builtInAndroidKey: String, builtInWebKey: String)
}

/** 当前安装包的版本号（关于页「检查更新」比较用） */
data class AppVersionInfo(val code: Int, val name: String)
