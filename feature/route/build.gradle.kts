// :feature:route：路线规划（地图）+ 路线详情（重构计划 §2.2）。ViewModel、Koin 模块、entry 声明在 commonMain；
// 第 17 步旧 Screen 和高德 Android SDK 封装（§2.9）放 androidMain。共用依赖由约定插件 trackr.kmp.feature 提供。
plugins {
    alias(libs.plugins.trackr.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
        }
        androidMain.dependencies {
            // 高德 3D 地图 + 定位 + 搜索一体化 SDK
            implementation(libs.amap.search)
        }
    }
}
