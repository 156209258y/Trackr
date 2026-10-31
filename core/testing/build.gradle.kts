// 测试辅助：KMP（Android + iosArm64 + iosSimulatorArm64），给各模块的测试用。
// 第 7 步放平台能力接口的 Fake 和 FakeClock；Fake 仓库在第 8 步按接口拆进来。
plugins {
    alias(libs.plugins.trackr.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
        }
    }
}
