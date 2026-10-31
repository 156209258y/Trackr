# 测试策略与指南

本项目测试涵盖核心算法、ViewModel 业务逻辑、Navigation 3 路由、Compose 界面行为与 JaCoCo 代码覆盖率。

---

## 1. 测试架构概览

- 单元测试框架：JUnit 4 (`junit:junit:4.13.2`), `kotlinx-coroutines-test:1.10.2`
- 无头环境与平台测试：Robolectric (`org.robolectric:robolectric:4.14.1`)，使用预置 `android-all-14-robolectric-10818077` 离线桩
- Compose UI 测试：`androidx.compose.ui:ui-test-junit4`, `androidx.compose.ui:ui-test-manifest`
- 协程与数据流测试：`MainDispatcherRule` (基于 `StandardTestDispatcher` 与 `Dispatchers.setMain`), `Turbine`
- 代码覆盖率：JaCoCo Gradle Plugin (`jacocoTestReport`)

---

## 2. 内存隔离机制 (FakeTransitRepository)

单元测试不发起真实网络请求，也不依赖本地存储。测试套件通过接口 `ITransitRepository` 注入 `FakeTransitRepository`（位于 `androidApp/src/test/java/com/trackr/data/repository/FakeTransitRepository.kt`）。

支持配置以下模拟响应：
- `supportedCitiesResult`：城市列表
- `stopDetailResult`：站点详情与多站台途经线路
- `lineDetailResult`：线路在途公交与反向线路
- `searchResult`：线路与站点混合搜索
- `favorites`：收藏夹变更

### 协程后台轮询参数化

为避免 ViewModel 中 `while(isActive) { delay(...) }` 轮询导致协程测试调度器死循环，所有带后台轮询的 ViewModel 均提供 `enableAutoPolling: Boolean = true` 构造参数：
- 生产环境：使用默认值 `true`，执行 10s 或 15s 轮询。
- 测试环境：传入 `enableAutoPolling = false`，通过 `testScheduler.runCurrent()` 或 `testScheduler.advanceTimeBy(ms)` 控制执行时序。

---

## 3. 测试目录与套件清单

`./gradlew test` 共跑 78 个测试类：`:androidApp` 43 个，`:core:designsystem` 2 个，`:core:domain` 6 个，`:core:navigation` 3 个，`:core:network` 12 个，`:core:data` 9 个，`:core:database` 1 个，`:core:datastore` 1 个，`:core:testing` 1 个（`:core:model` 没有测试）。KMP 模块的 `test` 由约定插件 `trackr.kmp.library` 补上，只跑 Android 主机测试（`testAndroidHostTest`）；iOS 另跑 `./gradlew :core:domain:iosSimulatorArm64Test`（`:core:network`、`:core:data` 同理，需要 Xcode 和 iOS 模拟器运行时），`:core:database` 的 DAO 测试同样在 iOS 模拟器上跑（`./gradlew :core:database:iosSimulatorArm64Test`，用 BundledSQLiteDriver），设计系统只做编译检查 `./gradlew :core:designsystem:compileKotlinIosSimulatorArm64`，导航做编译检查加测试链接 `./gradlew :core:navigation:linkDebugTestIosSimulatorArm64`。

Koin 由 `TrackrApp.onCreate` 启动，Robolectric 用例里每个用例都会重新启动一次。`TransferAutoSwitchManager`、`ArrivalAlertManager`、`LiveProgressNotificationHelper` 这三个旧 object 转发到 Koin 里的实例，只能在 Robolectric（带 `TrackrApp`）用例里调用；纯 JVM 用例里调用会因为 Koin 没启动而抛 `IllegalStateException`。`ReminderScheduler`、`AmapKeyStore` 转发到伴生对象，不受影响。

第 9 步起 `TrackrApp.onCreate` 会打开本地存储（`StorageBackend`：默认 Room + DataStore，并把旧 SharedPreferences 导入一次）。带 `TrackrApp` 的 Robolectric 用例因此用的是新存储；要测旧存储，直接 new 仓库的旧构造函数（`RouteGroupRepository(context)`、`ScheduledReminderRepository(context)`），要单独测存储本身，用 `@Config(application = Application::class)` 不启动 `TrackrApp`（同一进程里同一个 DataStore 文件只能开一个）。

`:androidApp` 的测试位于 `androidApp/src/test/java/com/trackr/`，下表按子目录列出全部测试类（辅助类 `FakeTransitRepository`、`FakeScheduledReminderRepository`、`MainDispatcherRule` 不含测试；`FakeTransitRepository` 由 `:core:testing` 里按接口拆开的 Fake 合成）。`:core:designsystem` 的测试位于 `core/designsystem/src/androidHostTest/kotlin/com/trackr/core/designsystem/`：`rail/RouteRailDrawUtilsTest`（走向条绘制工具常量与绘制不崩溃）、`theme/TrackrThemeTest`（v3 蓝浅 / 深两套且不用动态取色、`TrackrColors` 与旧名别名、`eta` / `item` 字号、圆角与尺寸、Compose 资源在 Android 上能读到），都是 Robolectric Compose UI。单跑：`./gradlew :core:designsystem:testAndroidHostTest`。

`:core:domain` 的测试放 `core/domain/src/commonTest/kotlin/com/trackr/core/domain/`，用 `kotlin.test`，Android 主机和 iOS 模拟器各跑一遍：`arrival/ArrivalTextTest`、`math/CoordTransformTest`、`tracking/GroupLegTrackerTest`、`tracking/SingleLegTrackerTest`、`tracking/BoardingDetectionTest`（第 7 步从 `:androidApp` 原样搬来，覆盖范围见下表）。`src/androidHostTest` 里的 `JvmParityTest` 只在 JVM 上跑：逐个比对第 7 步换掉的 JVM API（`String.format(Locale.CHINA, "%.1f公里")`、`Math.toRadians`、`Math.round`、`"%02d:%02d".format`）和新写法结果一致。`:core:testing` 放平台能力接口的 Fake（`FakeClock`、`FakeTrackingController`、`FakeAnnouncer`、`FakeLocationSource`、`FakeReminderAlarms` 等），`FakesTest` 测这些 Fake 本身。单跑：`./gradlew :core:domain:testAndroidHostTest`。

| 目录 | 测试类 | 覆盖范围 | 运行方式 |
| :--- | :--- | :--- | :--- |
| 根目录 | `TransitUnitTest` | 核心算法（`RouteProjectionMath` 坐标插值与站名纠错、线路与城市模型；车来了加解密第 8 步搬到 `:core:network` 的 `ChelaileCryptoTest`） | 本地 JVM |
| `data/remote/amap` | `AmapSdkManagerTest` | 高德 Key 优先级、隐私合规与 Key 注入 | Robolectric (JVM) |
|  | `AmapSdkKeySyncTest` | 改了 Android Key 才重新注入 SDK：跳过启动时的值、改 Web Key 不注入、重复 `start()` 不开第二个收集 | 本地 JVM |
|  | `AmapSdkTransitServiceTest` | 高德 SDK 公交路径转换为方案、无 Key 时的提示 | Robolectric (JVM) |
| `data/remote/update` | `GithubUpdateCheckerTest` | 更新检查多源回退、强制更新判定、下载地址 https 与域名白名单校验、取消后不再试剩下的源（Ktor `MockEngine`） | Robolectric (JVM) |
| `data/local` | `StorageBackendTest` | 整套存储端到端：同一份旧数据经新存储和旧存储读出来完全相同、旧文件不被改动、新存储写入重开读回、USE_NEW_STORE 关了再开能带回旧存储期间的改动、库打不开时退回旧存储、旧的聚合配置在新旧存储里读成同一个主数据源 | Robolectric (JVM) |
|  | `BackupRulesTest` | `backup_rules.xml` 和 `data_extraction_rules.xml` 两段：排除高德 Key（新旧两个文件）和追踪会话、不排除 `trackr.db` 和设置、没有 include、三段一样、Manifest 都接上了 | Robolectric (JVM) |
| `data/repository` | `RouteGroupRepositoryTest` | 旧存储（SharedPreferences）上：路线分组与收藏分组增删改排序、并发写入不丢更新、JSON 损坏备份（第 14 步起经收藏合一仓库） | Robolectric (JVM) |
|  | `RoomRouteGroupRepositoryTest` | 新存储（Room）上：读回逐字段一致（程、候选线、轨迹点）、清理假数据、删分组时路线一起改成不分组、排序和加候选线持久化、并发写入不丢更新 | Robolectric (JVM) |
|  | `ScheduledReminderRepositoryTest` | 旧存储上：定时提醒增删改与持久化、并发写入不丢更新、JSON 损坏备份、计划触发时间记录 | Robolectric (JVM) |
|  | `RoomScheduledReminderRepositoryTest` | 新存储上：增删改与重开读回、并发保存 / 开关不丢更新、计划触发时刻存进 `reminder.lastScheduledTriggerAt` 并且改别的提醒时不被冲掉 | Robolectric (JVM) |
| `di` | `KoinGraphVerifyTest` | Koin 依赖图：每个模块 `verify()` 静态检查构造参数都有绑定；Robolectric 里把每个定义实际取一次（带参数的 ViewModel 用样例 key） | Robolectric (JVM) |
| `receiver` | `ScheduledReminderReceiverTest` | 定时提醒触发、自动跟车需精确闹钟、前台服务启动失败降级 | Robolectric (JVM) |
|  | `BootCompletedReceiverTest` | 开机禁用已错过的一次性提醒、时间变化重新调度、无关广播忽略 | Robolectric (JVM) |
| `service` | `ArrivalAlertManagerTest` | 到站/临近提醒与实时通知并存时的去重 | Robolectric (JVM) |
|  | `LiveProgressNotificationHelperTest` | 实时进度通知进度计算与文案 | Robolectric (JVM) |
|  | `TransferAutoSwitchManagerTest` | 自适应轮询退避与下车到站判定 | Robolectric (JVM) |
| `service/vendor` | `LiveUpdatePromotionTest` | Android 16 Live Update 推广通知构建与厂商识别 | Robolectric (JVM) |
|  | `XiaomiFocusAdapterTest` | 小米焦点通知 JSON 负载构建 | Robolectric (JVM) |
| `ui` | `ScreenshotGeneratorTest` | 主要页面明暗主题截图生成 | Robolectric Compose UI |
| `ui/components` | `BusArrivalBadgeTest` | 到站状态徽标文字与高亮（走 `ArrivalText`） | 本地 JVM |
|  | `ExpressiveRouteRailUiTest` | 经典横向与纵向走向条渲染、逐字竖排与点击事件响应 | Robolectric Compose UI |
| `ui/components/citypicker` | `CityPickerItemRowTest` | 城市选择列表项渲染与点击 | Robolectric Compose UI |
| `ui/components/timetable` | `TimetableBottomSheetTest` | 时刻表底部弹窗渲染 | Robolectric Compose UI |
| `ui/screens/favoritesgroup` | `FavoritesGroupNavigationEventTest` | 分组编辑弹窗未保存时的返回确认 | Robolectric Compose UI |
|  | `FavoritesGroupScreenTest` | 收藏分组卡片渲染、候选线路切换与分组管理 | Robolectric Compose UI |
|  | `FavoritesGroupViewModelTest` | 分组筛选、候选线路、多线路 ETA 聚合、临时分组；高德 Key 只读、跟着仓库走（Key 在「更多」页改） | Robolectric (JVM) |
|  | `TransitGroupCardTextTest` | 分组卡片乘车站数、摘要与上车时间文案 | 本地 JVM |
|  | `TransitGroupCardTrackingTest` | 分组卡片跟车中各阶段展示 | Robolectric Compose UI |
| `ui/screens/home` | `HomeViewModelTest` | 首页城市切换、城市汉字与拼音前缀过滤、搜索防抖、收藏状态监听 | Robolectric (JVM) |
| `ui/screens/linedetail` | `BusFollowContentTest` | 跟车内容渲染与空车辆处理 | Robolectric Compose UI |
|  | `LineDetailViewModelTest` | 线路详情加载、站点选择、单车行程跟踪、走向条横纵向切换、收藏切换 | 本地 JVM |
|  | `StationArrivalContentTest` | 线路页到站大字：在路上、到站了、车都过了只给下一班始发时间、首班、末班已过 | Robolectric Compose UI |
| `ui/screens/more` | `AboutViewModelTest` | 关于页更新检查状态流转 | Robolectric (JVM) |
|  | `MoreViewModelTest` | 设置项读写（数据源只有车来了 / 掌上公交二选一）与定时提醒开关/删除；高德 Key 两槽各写各的、清空一槽不动另一槽、区块状态文字 | Robolectric (JVM) |
| `ui/navigation` | `NavigationFlowTest` | D2 底栏「路线规划」：有 / 无 Key、只有 Web Key、只有 Android Key、只有构建时 Key 时显示与否；停在该页签时 Key 被清空回首页并清栈；恢复到该页签但已无 Key 时回首页 | Robolectric Compose UI |
| `ui/screens/routeplan` | `RoutePlanScreenTest` | 路线规划搜索台、起终点互换、方案卡片与特征标签；没有「配置Key」胶囊；只有 Web Key / 没有地图会话时不画地图、没有回中按钮 | Robolectric Compose UI |
| `ui/screens/stopdetail` | `StopDetailViewModelTest` | 站点详情物理站台切换、途经线路实时 ETA、收藏增删 | 本地 JVM |
| `ui/util` | `PollingCoordinatorTest` | 定时/倒计时轮询策略与生命周期控制 | 本地 JVM |
| `util` | `ReminderSchedulerTest` | 提醒下次触发时间计算、错过的一次性提醒判定 | 本地 JVM |
|  | `SignatureUtilsTest` | 包名与签名 SHA-1（取签名历史中的当前证书） | Robolectric (JVM) |
|  | `TransitCodeHelperTest` | 支付宝/云闪付乘车码跳转 Intent | Robolectric (JVM) |
| `widget` | `FavoritesShortcutWidgetSyncTest` | 应用级收藏同步：启动时不刷新，收藏真变一次只刷新一次快捷方式与小组件，重复 `start()` 不开第二个收集 | Robolectric (JVM) |

`:core:domain`、`:core:navigation`、`:core:network`、`:core:data` 和 `:core:testing` 的测试类（`:core:navigation` 单跑：`./gradlew :core:navigation:testAndroidHostTest`；网络测试一律用 Ktor `MockEngine`，不发真实 HTTP）：

| 模块 / 目录 | 测试类 | 覆盖范围 | 运行方式 |
| :--- | :--- | :--- | :--- |
| `:core:domain` `arrival` | `ArrivalTextTest` | 到站文字 8 种状态（在路上、快到/已到、车都过了、还没发车、末班已过、停运/无数据、加载中、没网）的大字/小字/色调/朗读文本，到站按 `ArrivalRules` 判，边界：0 站、≤3 分钟、末班刚过 | commonTest（JVM + iOS 模拟器） |
| `:core:domain` `math` | `CoordTransformTest` | WGS-84 / GCJ-02 坐标转换 | commonTest（JVM + iOS 模拟器） |
| `:core:domain` `tracking` | `BoardingDetectionTest` | 定位跟车检测（待确认、上车、没赶上、超时兜底、错锁纠正）与定位频率档位 | commonTest（JVM + iOS 模拟器） |
|  | `GroupLegTrackerTest` | 换乘分组单段跟车状态机（候车锁车、乘车到站、信号丢失） | commonTest（JVM + iOS 模拟器） |
|  | `SingleLegTrackerTest` | 单线路追踪选车、换车重置提醒、车辆短暂消失保持原车 | commonTest（JVM + iOS 模拟器） |
| `:core:domain` 根目录 | `JvmParityTest` | 第 7 步换掉的 JVM API 与新写法逐个比对：公里数格式化、角度转弧度（逐位相同）、两种测距公式、分钟取整、提醒时刻格式化 | androidHostTest（只 JVM） |
| `:core:navigation` | `NavigatorTest` | `Navigator` 纯逻辑：栈顶去重压栈、逐页返回、非首页页签根页返回回首页、首页根页返回交给系统；切页签保留各自的栈、再点当前页签回根并发 `reselect`；`openInTab`（通知 / 小组件跳详情）去重防重放；页签隐藏时清栈回首页；`isShowingRootOf`（首页轮询条件）；`stacksShown`（叠放起始页签 / 只显示当前页签）；`TopLevel.visibleTabs` 没 Key 时只去掉「路线规划」 | commonTest（JVM + iOS 模拟器） |
|  | `NavigationKeyTest` | `ScreenKey` 用 JSON 序列化与反序列化（第 16 步从 `:androidApp` 搬来） | commonTest（JVM + iOS 模拟器） |
|  | `NavigationStateRestorationTest` | `StateRestorationTester` 模拟进程重建后 4 条栈和当前页签、页内 `rememberSaveable` 都恢复；切页签不丢另一条栈的页内状态和 ViewModel、弹出页面只清它自己的 ViewModel；`TrackrSavedStateConfiguration` 登记了全部 `ScreenKey`，默认配置存不了 | Robolectric Compose UI（只 JVM） |
| `:core:network` `json` | `JsonCompatTest` | 和 org.json 同规则的只读 JSON：optString / optInt / optDouble / optBoolean / opt 的取值与默认值 | commonTest（JVM + iOS 模拟器） |
|  | `JsonCompatParityTest` | 同一段 JSON 每个字段分别用兼容层和真正的 org.json 取值，结果逐个相同 | androidHostTest（只 JVM） |
| `:core:network` `remote/common` | `BaseTransitApiClientTest` | 地铁站名识别、距离计算、车牌清洗；HTTP/JSON 执行封装、异常信息不带 query、协程取消时底层请求一起取消、20 秒总超时（换成小值验证）且超时消息不带 key | commonTest（JVM + iOS 模拟器） |
|  | `SingleFlightCacheTest` | 同 key 并发只加载一次、取消一个等待者不影响其他、全部取消后下次重新加载、失败不缓存、TTL 内复用与过期、TTL 为 0 只去重、条目数上限、手动失效 | commonTest（JVM + iOS 模拟器） |
| `:core:network` `remote/amap` | `AmapLruCacheTest` | 高德结果 LRU 缓存满时淘汰最久未用的条目 | commonTest（JVM + iOS 模拟器） |
|  | `AmapTransitApiClientTest` | 高德 Web API 换乘方案解析、6 位小数坐标与 URL 编码、`cityd` / `nightflag`、发出去的地址和拼好的串一字不差、输入提示解析 | commonTest（JVM + iOS 模拟器） |
|  | `AmapFormatJvmTest` | 换德语 Locale 后坐标仍用 `.`；定点格式化逐个对比 `String.format(Locale.ROOT, "%.6f")`、表单编码逐个对比 `URLEncoder` | androidHostTest（只 JVM） |
| `:core:network` `remote/chelaile` | `ChelaileCryptoTest` | 签名官方测试向量、信封截取（含字符串里的括号）、固定密文解密、H5 备用密钥回退、非 JSON 明文报错、带换行的 Base64 | commonTest（JVM + iOS 模拟器） |
|  | `ChelaileCryptoJvmTest` | javax.crypto 加密 → common 解密 200 组往返；签名对比 `MessageDigest` | androidHostTest（只 JVM） |
| `:core:network` `provider` | `TransitProviderTest` | 车来了 Provider 多物理站台并发补全、附近站过滤与站点状态映射；掌上公交站点详情、搜索、附近站、城市列表 | commonTest（JVM + iOS 模拟器） |
|  | `TransitProviderCacheTest` | Provider 层省请求：车来了附近站补全短缓存、其他站台只在首次加载补全、空主查询下次跳过；掌上公交 cmd103 缓存只轮询 cmd104、搜索回退结果线路与站点共用、附近站短缓存、反向地理编码空结果短缓存 | commonTest（JVM + iOS 模拟器） |
|  | `RecordedResponsesTest` | 录好的真实响应（`RecordedResponses`，车来了 5 个接口、掌上公交 4 个 CMD）走一遍客户端和解析，并核对发出去的请求头和表单 | commonTest（JVM + iOS 模拟器） |
| `:core:data` `provider` | `TransitProviderCoordinatorTest` | 数据源二选一（D1）：搜索、附近站、站点详情只查当前数据源，搜索去掉地铁并按线路名自然排序；线路详情按来源 / id 前缀路由，认不出来走当前数据源；逆地理先车来了、空了再掌上公交；城市列表两家合并 | commonTest（JVM + iOS 模拟器） |
| `:core:data` `repository` | `AmapKeyRepositoryTest` | 高德 Key 两槽（§2.5）：各槽「用户填的 > 构建时的」、互不回落（含原 `customAmapApiKey_doesNotFallBackToLegacyWebSlot`）；`hasMapKey` / `canShowMap` 四种组合；setter 后立刻更新、只写自己的槽；没有 Android 地图 SDK 的平台不看 Android 槽 | commonTest（JVM + iOS 模拟器） |
|  | `RealtimeRepositoryLineDetailCacheTest` | 线路详情同 key 并发只请求一次上游、TTL 内复用与过期重拉、目标站顺序不同算不同 key、失败不缓存、取消的调用方不影响其他等待者 | commonTest（JVM + iOS 模拟器） |
|  | `LocationRepositoryTest` | 首页取定位：够新够准直接用、强制刷新 10 秒 / 平时 60 秒、4.5 秒窗口取最准且 ≤100 m 提前结束、超过 10 分钟的旧定位不用、无权限不定位；规划页 lastKnown 取 10 分钟内最准的 | commonTest（JVM + iOS 模拟器） |
|  | `CountyLevelCityMatchTest` | 县级市/县城匹配与省份校验 | commonTest（JVM + iOS 模拟器） |
| `:core:data` `repository` | `FavoritesRepositoryTest` | 收藏合一（第 14 步）在 Room 内存库上：车和路线合并排序（新车在最前、新路线在最后）、拖动排序（跨类型、只给几条）、改分组、删分组后车和路线都变不分组、旧数据迁入后顺序和分组不变、启动对账（删孤儿行、补缺的）、默认分组、清理假数据、并发写不丢 | Robolectric（只 JVM） |
| `:core:data` | `TrackrDatabaseMigrationTest` | 库 1 -> 2：按 `schemas/.../1.json` 手工建版本 1 的库再用正式 builder 打开，`favorite_item` 按原顺序生成（车在前、路线按 sortOrder），分组按收藏页原来的规则补，旧表一行不变，仓库读出的顺序和分组和升级前一样；空库、重开 | Robolectric（只 JVM） |
| `:core:data` `legacy` | `LegacyLibraryMigrationTest` | 旧 SharedPreferences -> Room / DataStore：每个旧 key 一条（收藏、搜索历史、路线、收藏分组、收藏合一索引 `favorite_items_json`、提醒 + 计划触发时刻、6 个设置（聚合优先级不导入）、两个 Key 槽，含 0a4fdab 之前存在 `trackr_prefs` 的 Key 与「Key 文件优先」）；旧文件前后一字不差；损坏 JSON 不崩、只跳过那一项、原文复制到 `legacy_backup/`；已有 `_corrupt_backup_` 原样复制；同一代只跑一次、换代只替换旧文件里有的项；库重建后重新导入 | Robolectric（只 JVM） |
|  | `SettingsMigrationTest` | 设置和 Key 的只读 DataMigration：按类型搬、旧的聚合配置（`engine_mode = ALL` 或没存 + 优先级）落成主数据源（D1）、类型不对跳过、旧 key 不删、同一代不重跑、再导只覆盖旧文件里有的 key、空的旧 Key 不清掉新 Key、Key 不进设置文件 | Robolectric（只 JVM） |
| `:core:database` | `DaoTest` | 各 DAO 在内存库上：收藏合一的组合写入（只换给了的部分、按行改索引、改分组和排序）、整份替换保序、路线连程和候选线读回、删路线不留孤儿行、路线和分组一个事务、提醒改列表时保留计划触发时刻、搜索历史、站表缓存过期清理、迁移标记 | commonTest（Robolectric + iOS 模拟器） |
| `:core:datastore` | `TrackrDataStoresTest` | 设置文件读写重开、文件损坏换成空内容不崩、Key 和设置分两个文件、追踪会话 JSON 读写清空、会话文件损坏读成没有会话 | androidHostTest（只 JVM） |
| `:core:testing` | `FakesTest` | `FakeClock` 墙钟和单调时钟一起走、改墙钟不动单调时钟；`FakeReminderAlarms`、`FakeTransitCodeLauncher`、`FakeLocationSource` 的行为 | commonTest（JVM + iOS 模拟器） |

---

## 4. 常用测试执行命令

### 运行全量测试

```bash
./gradlew test
```

### 运行特定 ViewModel 测试

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.trackr.ui.screens.linedetail.LineDetailViewModelTest"
./gradlew :androidApp:testDebugUnitTest --tests "com.trackr.ui.screens.home.HomeViewModelTest"
```

### 运行 Compose UI 测试

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.trackr.ui.components.ExpressiveRouteRailUiTest"
```

### 生成 JaCoCo 代码覆盖率报告

```bash
./gradlew jacocoTestReport
```

报告输出路径：
- JaCoCo HTML：`androidApp/build/reports/jacoco/jacocoTestReport/html/index.html`
- 单元测试 HTML：`androidApp/build/reports/tests/testDebugUnitTest/index.html`
