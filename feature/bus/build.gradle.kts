// :feature:bus：公交首页（重构计划 §2.2）。第 19 步起 ViewModel、页面、Koin 模块、entry 都在 commonMain（v3 公交首页），
// androidMain 不再放页面。共用依赖由约定插件 trackr.kmp.feature 提供。
plugins {
    alias(libs.plugins.trackr.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // :core:designsystem 的图标（Res.drawable.*）
            implementation(libs.compose.mp.components.resources)
        }
    }
}
