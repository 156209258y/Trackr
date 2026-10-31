# Agent 指南

工程开发、重构与测试指南。

## 开发规范与最佳实践

遵循 Google 官方推荐的 **MAD (Modern Android Development)** 最佳实践：

1. **架构规范**：严格遵循官方应用架构指南（Guide to app architecture），采用分层架构（UI 层、可选 Domain 层、Data 层）与单向数据流（UDF, Unidirectional Data Flow）。
2. **现代 UI 开发**：优先使用 Jetpack Compose 构建声明式界面，规范状态提升（State Hoisting）与生命周期感知，遵循 Compose 性能最佳实践。
3. **异步与响应式流**：全面采用 Kotlin Coroutines 与 Flow 处理异步任务和数据流，结合 `viewModelScope` 及生命周期感知 API（如 `repeatOnLifecycle` / `collectAsStateWithLifecycle`）安全收集。
4. **组件解耦与依赖注入**：坚持单一职责与依赖倒置原则，面向接口/抽象编程，保障模块高内聚低耦合与高可测试性。
5. **现代 Jetpack 库生态**：积极采用最新的 AndroidX / Jetpack 推荐组件，避免引入已废弃（Deprecated）或过时的旧 API。

## 测试策略

所有代码变更提交前需通过测试验证。详细说明见 [测试策略与指南](docs/testing.md)。

### 准则

1. **测试隔离**。单元测试不发起真实 HTTP 请求，所有数据层测试使用 `FakeTransitRepository`。
2. **协程测试**。带轮询的 ViewModel 传入 `enableAutoPolling = false`，避免测试调度器死循环。
3. **验证命令**
   - 运行全量测试：`./gradlew test`
   - 运行单个测试类：`./gradlew :androidApp:testDebugUnitTest --tests "com.trackr.ui.screens.home.HomeViewModelTest"`
   - 领域层测试：`./gradlew :core:domain:testAndroidHostTest`（JVM）、`./gradlew :core:domain:iosSimulatorArm64Test`（需要 Xcode 和 iOS 模拟器运行时）
   - 打 release 包：`./gradlew :androidApp:assembleRelease`（产物在 `androidApp/build/outputs/apk/release/`）
   - 生成覆盖率报告：`./gradlew jacocoTestReport`

## 工程结构

- `:androidApp`（目录 `androidApp/`）：Android 应用，applicationId `com.github.u5151561.trackr`，包名 `com.trackr`。
- `:core:designsystem`（目录 `core/designsystem/`）：设计系统，KMP + Compose Multiplatform。代码在 `src/commonMain`，图标和通用文案在 `src/commonMain/composeResources`（生成类 `com.trackr.core.designsystem.resources.Res`），测试在 `src/androidHostTest`。入口 `TrackrTheme(uiMode)`；页面读 `MaterialTheme.typography.*`、`TrackrTheme.colors.*`、`TrackrTheme.dimens.*`。
- `:core:model`（目录 `core/model/`）：领域模型，KMP 纯 Kotlin，包名 `com.trackr.core.model`（`@Serializable` 数据类、枚举、`ProvinceCityCatalog`、`UserFix`）。不跑 Compose 编译器，页面直接用作参数的扁平模型要登记在根目录 `compose_stability.conf`。
- `:core:domain`（目录 `core/domain/`）：领域层，KMP 纯 Kotlin，包名 `com.trackr.core.domain.*`：`arrival`（`ArrivalText`、`ArrivalRules`）、`tracking`（`SingleLegTracker`、`GroupLegTracker`、`BoardingDetector`、`LocationCadence`）、`math`（`RouteProjectionMath`、`CoordTransform`）、`platform`（平台能力接口 `TrackingController`、`ReminderAlarms`、`LocationSource`、`UpdateChecker` 等，墙钟用 `kotlin.time.Clock`、单调时钟用 `MonotonicClock`）。`commonMain` 不写 `java.*` / `android.*`，「现在」由调用方传入。测试在 `src/commonTest`（`kotlin.test`）。
- `:core:testing`（目录 `core/testing/`）：测试辅助，平台能力接口的 Fake 和 `FakeClock`。
- `build-logic/convention`：约定插件，统一 SDK、JDK、单元测试与 Compose 编译器配置。
  - `trackr.android.application`：`:androidApp` 用。
  - `trackr.kmp.library` + `trackr.kmp.compose`：KMP 模块用（Android + iosArm64 + iosSimulatorArm64），`:core:designsystem` 两个都用，`:core:model`、`:core:domain`、`:core:testing` 只用 `trackr.kmp.library`；`trackr.kmp.feature` 留给 `:feature:*`。
