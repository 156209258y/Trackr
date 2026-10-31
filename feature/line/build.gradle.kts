// :feature:line：线路详情 + 时刻表 + 定时提醒入口（重构计划 §2.2）。ViewModel、Koin 模块、entry 声明在 commonMain；
// 第 17 步旧 Screen 原样放 androidMain，阶段 D 重写进 commonMain。共用依赖由约定插件 trackr.kmp.feature 提供。
plugins {
    alias(libs.plugins.trackr.kmp.feature)
}

kotlin {
    android {
        // 乘车码弹层的文案（androidMain/res，中 / 英）
        androidResources.enable = true
    }
}
