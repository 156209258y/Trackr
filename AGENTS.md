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

1. **测试隔离**。单元测试不发起真实 HTTP 请求。
   - ViewModel / 页面测试用 Fake 仓库：`:core:testing` 里按接口各有一个（`FakeSettingsRepository`、`FakeFavoritesRepository`、`FakeRealtimeRepository`、`FakeSearchHistoryRepository`、`FakeLocationRepository`、`FakeAmapKeyRepository`），`:androidApp` 的 `FakeTransitRepository` 把它们合在一起给还拿 `ITransitRepository` 的 ViewModel 用。
   - 网络客户端和 Provider 测试用 Ktor `MockEngine`：`:core:network` 测试里的 `mockHttpClient { request -> MockReply(json) }`。要用真实数据时用录好的响应（`RecordedResponses`），不要在测试里连真服务器；需要重新录时每个接口只调一次。
2. **协程测试**。带轮询的 ViewModel 传入 `enableAutoPolling = false`，避免测试调度器死循环。
3. **验证命令**
   - 运行全量测试：`./gradlew test`
   - 运行单个测试类：`./gradlew :androidApp:testDebugUnitTest --tests "com.trackr.ui.screens.home.HomeViewModelTest"`
   - KMP 模块测试：`./gradlew :core:domain:testAndroidHostTest`（JVM，`:core:network`、`:core:data` 同理）、`./gradlew :core:domain:iosSimulatorArm64Test`（需要 Xcode 和 iOS 模拟器运行时）
   - 导航测试：`./gradlew :core:navigation:testAndroidHostTest`（JVM + Robolectric）
   - 打 release 包：`./gradlew :androidApp:assembleRelease`（产物在 `androidApp/build/outputs/apk/release/`）
   - 生成覆盖率报告：`./gradlew jacocoTestReport`

## 工程结构

- `:androidApp`（目录 `androidApp/`）：Android 应用，applicationId `com.github.u5151561.trackr`，包名 `com.trackr`。
- `:core:designsystem`（目录 `core/designsystem/`）：设计系统，KMP + Compose Multiplatform。代码在 `src/commonMain`，图标和通用文案在 `src/commonMain/composeResources`（生成类 `com.trackr.core.designsystem.resources.Res`），测试在 `src/androidHostTest`。入口 `TrackrTheme(uiMode)`；页面读 `MaterialTheme.typography.*`、`TrackrTheme.colors.*`、`TrackrTheme.dimens.*`。
- `:core:model`（目录 `core/model/`）：领域模型，KMP 纯 Kotlin，包名 `com.trackr.core.model`（`@Serializable` 数据类、枚举、`ProvinceCityCatalog`、`UserFix`）。不跑 Compose 编译器，页面直接用作参数的扁平模型要登记在根目录 `compose_stability.conf`。
- `:core:domain`（目录 `core/domain/`）：领域层，KMP 纯 Kotlin，包名 `com.trackr.core.domain.*`：`arrival`（`ArrivalText`、`ArrivalRules`）、`tracking`（追踪状态机 `TrackingEngine`：开始 / 轮询 / 我上车了 / 下一程 / 轮询间隔 / 下车站判定，产出提醒、换乘提醒、结束等效果；内部用 internal 的 `SingleLegTracker`、`GroupLegTracker`、`BoardingDetector`，以及公开的定位档位 `LocationCadence`）、`math`（`RouteProjectionMath`、`CoordTransform`）、`platform`（平台能力接口 `TrackingController`、`ReminderAlarms`、`LocationSource`、`UpdateChecker` 等，墙钟用 `kotlin.time.Clock`、单调时钟用 `MonotonicClock`）。`commonMain` 不写 `java.*` / `android.*`，「现在」由调用方传入。测试在 `src/commonTest`（`kotlin.test`）。
- `:core:network`（目录 `core/network/`）：网络层，KMP。Ktor 客户端 `createTrackrHttpClient()`（Android 用 OkHttp 引擎，iOS 用 Darwin；总超时 20 秒）、车来了 / 掌上公交 / 高德 Web 客户端、车来了加解密（`expect`/`actual`）、两个数据源的 `ITransitProvider`、`SingleFlightCache`、`AmapLruCache`。解析代码用 `com.trackr.core.network.json` 里和 org.json 同规则的只读 JSON。
- `:core:data`（目录 `core/data/`）：数据层，KMP。仓库接口 `SettingsRepository`、`FavoritesRepository`、`RealtimeRepository`、`SearchHistoryRepository`、`LocationRepository`、`RoutePlanRepository`、`AmapRouteGateway`；追踪会话的唯一数据源 `TrackingRepository`（`DefaultTrackingRepository`：内存一份加锁、每次变化排队写盘，代际号核对丢旧结果，进程重建后读回标成中断、服务接上后恢复）；实现 `DefaultRealtimeRepository`（实时查询、线路详情 6 秒合并缓存）、`DefaultLocationRepository`（经 `LocationSource`）；按数据源选 Provider 的 `TransitProviderCoordinator`。高德 Key 两槽 `AmapKeyRepository`（各槽「用户填的 > 构建时的」、互不回落；由它算出 `hasMapKey`：底栏有没有「路线规划」，`canShowMap`：路线页画不画地图，规则在 `AmapKeyRules`，第 11 步）。收藏合一（第 14 步）：`DefaultFavoritesRepository` 是收藏的车、路线、收藏分组和合一顺序的唯一持有者（`favoriteItems`、`move`、`setGroup`、删分组后收藏变不分组），`:androidApp` 的 `TransitRepository`（收藏部分）和 `RouteGroupRepository` 都转调它。`local/`：仓库下面的存储层 `TransitLocalStore`、`FavoritesStore`、`ReminderStore`、`AmapKeyStore` 及新实现（Room + DataStore）；`androidMain/legacy/`：旧 SharedPreferences 导入（`LegacyLibraryMigration`、只读的 `LegacySettingsMigration` / `LegacyAmapKeyMigration`，旧 key 一律不删）。设置 / 搜索历史的仓库仍是 `:androidApp` 的 `TransitRepository`，存储经 `TransitLocalStore`。
- `:core:database`（目录 `core/database/`）：Room KMP，库文件 `trackr.db`（参与备份），表结构导出在 `core/database/schemas/`。当前版本 2（第 14 步加 `favorite_item`，迁移在 `Migrations.kt`）。改表结构要升版本号、在 `TRACKR_MIGRATIONS` 里加 Migration、在 `:core:data` 的 `TrackrDatabaseMigrationTest` 里按旧版 schema JSON 建库验证，不要开 destructive fallback。装过新版本库的手机不能再装低版本的包。
- `:core:datastore`（目录 `core/datastore/`）：DataStore KMP，`files/datastore/` 下三个文件：`trackr_settings.preferences_pb`（参与备份）、`trackr_amap_keys.preferences_pb`（备份排除）、`tracking_session.json`（追踪会话，`TrackingRepository` 读写；USE_NEW_STORE 关时只在内存里；备份排除）。改这些文件名要同时改 `androidApp/src/main/res/xml/` 的两份备份规则（`BackupRulesTest` 会先失败）。
- 存储开关 USE_NEW_STORE（第 9 步，`androidApp/.../data/local/StoreSwitch.kt`）：默认新存储；`files/store_switch` 写 `old` 后杀进程重开即退回旧 SharedPreferences，写 `new` 或删掉文件恢复。新存储打不开或导入出错时自动退回旧存储。
- `:core:testing`（目录 `core/testing/`）：测试辅助，平台能力接口的 Fake、`FakeClock`、`:core:data` 仓库接口的 Fake。
- `:core:navigation`（目录 `core/navigation/`）：导航，KMP + CMP 版 Navigation 3，包名 `com.trackr.core.navigation`。`ScreenKey`（新增子类要同时登记进 `TrackrSavedStateConfiguration`）、`TopLevel`（页签）、`NavigationState`（每个页签一条返回栈 + 当前页签，`rememberNavigationState()`）、`Navigator`（所有导航动作：`open`、`back`、`selectTab` 等）、`TrackrTransitions`。页面不直接改返回栈，一律调 `Navigator`。测试：`commonTest` 的 `NavigatorTest`、`NavigationKeyTest`，`androidHostTest` 的 `NavigationStateRestorationTest`（Robolectric）。
- `build-logic/convention`：约定插件，统一 SDK、JDK、单元测试与 Compose 编译器配置。
  - `trackr.android.application`：`:androidApp` 用。
  - `trackr.kmp.library` + `trackr.kmp.compose`：KMP 模块用（Android + iosArm64 + iosSimulatorArm64），`:core:designsystem`、`:core:navigation` 两个都用，`:core:model`、`:core:domain`、`:core:network`、`:core:data`、`:core:database`、`:core:datastore`、`:core:testing` 只用 `trackr.kmp.library`；`trackr.kmp.feature` 留给 `:feature:*`。
