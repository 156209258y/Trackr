// :feature:favorites：编辑收藏 + 收藏弹层；第 17 步放旧收藏页用的组件（重构计划 §2.2）。ViewModel、Koin 模块、entry 声明在 commonMain；
// 第 17 步旧 Screen 原样放 androidMain，阶段 D 重写进 commonMain。共用依赖由约定插件 trackr.kmp.feature 提供。
plugins {
    alias(libs.plugins.trackr.kmp.feature)
}

kotlin {
    android {
        // 主机测试里也要能读到 :core:designsystem 的 Compose 资源（图标）
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            // 第 22 步：编辑收藏页进 commonMain，图标用 :core:designsystem 的 Compose 资源
            implementation(libs.compose.mp.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.trackr.feature.favorites.resources"
}
