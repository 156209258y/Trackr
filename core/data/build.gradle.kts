// 数据层：KMP（Android + iosArm64 + iosSimulatorArm64）。
// 拆开的仓库接口（设置 / 收藏 / 实时查询 / 搜索历史 / 定位 / 路线规划）、按数据源选 Provider 的协调器、
// 不依赖平台存储的实现（实时查询、定位）。存储相关的实现第 9 步换成 Room / DataStore。重构计划 §2.5。
plugins {
    alias(libs.plugins.trackr.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:domain"))
            api(project(":core:network"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.atomicfu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core:testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
        }
    }
}
