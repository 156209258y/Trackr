// :feature:stop：站点详情（重构计划 §2.2）。ViewModel、Koin 模块、entry 声明在 commonMain；
// 第 17 步旧 Screen 原样放 androidMain，阶段 D 重写进 commonMain。共用依赖由约定插件 trackr.kmp.feature 提供。
plugins {
    alias(libs.plugins.trackr.kmp.feature)
}

kotlin {
    android {
        // Compose 资源（页面文案）在 Android 上走 Android 资源 / assets 打进 APK。
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.mp.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.trackr.feature.stop.resources"
}
