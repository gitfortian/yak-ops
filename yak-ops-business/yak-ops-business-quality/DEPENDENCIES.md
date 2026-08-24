# Data Quality Dependencies

本文件定义 Data Quality package 的**允许依赖方向、跨子系统 corridor 和跨模块边界**。原则：**显式、窄、无环**。

架构角色看 `ARCHITECTURE.md`；统一工程规则看仓库根目录 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。如果代码与本文件冲突，先判断代码是否越界，不要直接扩大白名单。

## 1. Top-level Dependency Matrix

Quality production 内部允许的 top-level 依赖：

| Source | Allowed Quality packages |
| --- | --- |
| `controller` | `asset`, `config`, `domain`, `execution`, `monitor`, `template`, `workspace` |
| `workspace` | `config`, `domain`, `monitor`, `repository` |
| `monitor` | `config`, `domain`, `repository`, `schedule` |
| `schedule` | `config`, `domain`, `execution`, `repository` |
| `execution` | `alert`, `config`, `domain`, `gateway`, `repository` |
| `alert` | `config`, `domain`, `repository` |
| `asset` | `config`, `domain`, `gateway`, `repository` |
| `template` | `config`, `domain`, `repository` |
| `gateway` | `config` |
| `repository` | `config`, `dao`, `domain` |
| `dao` | none |
| `domain` | none |
| `config` | none |

同一 top-level package 内部可以互相协作，但不会因此自动成为其他 package 的公共 API。

声明图和实际源码图都必须无环。

## 2. Controller Corridors

Controller 只能进入显式 Application role，不允许直接调用 Repository / DAO / Gateway / Schedule engine。

当前可用角色族：

```text
asset      -> Manager / Reader / CandidateReader
monitor    -> Manager / Reader
execution  -> Manager / Reader
workspace  -> Reader / Projector
template   -> Manager / Reader
```

Controller transport mapper 只负责 DTO/VO 与 Quality command/domain/read values 的边界转换。

## 3. Monitor -> Schedule

Monitor 跨入 Schedule 只允许：

```text
QualityMonitorManager
    -> QualityScheduleLifecycle

QualityMonitorSettingsPolicy
    -> QualityScheduleCalculator
```

Monitor 不直接依赖 ScheduleHandler、EngineBridge 或 Reconciler。

## 4. Schedule -> Execution

Schedule callback 进入 Execution 只允许：

```text
QualityScheduleHandler
    -> QualityExecutionManager
```

Schedule 不能直接：

- insert/update Execution；
- 调用 Worker；
- 调用 Dispatcher；
- 自己复制 execution admission 规则。

## 5. Workspace -> Monitor

Workspace 读取 Monitor 当前定义只允许：

```text
QualityWorkspaceReader
    -> QualityMonitorReader
```

其他 workspace projection 优先直接依赖自己的 read Repository；Workspace 不进入 Monitor Manager。

## 6. Execution -> Alert

Execution 触发告警只允许：

```text
QualityExecutionWorker
    -> QualityAlertRecorder
```

Execution 不直接写 Alert DAO/PO；Alert 也不反向调用 Execution Manager/Worker。

## 7. Quality-owned Datasource Gateway

Asset / Execution 使用 Datasource 能力时只依赖：

```text
QualityDataCatalogGateway
```

当前允许：

```text
asset     -> QualityDataCatalogGateway
execution -> QualityDataCatalogGateway
```

禁止 business role 直接 import：

```text
io.yak.ops.business.datasource.controller.*
io.yak.ops.business.datasource.repository.*
io.yak.ops.business.datasource.dao.*
io.yak.ops.business.datasource.plugin.*
```

### External Datasource corridor

Quality 对 Datasource 模块只有两个明确入口：

```text
config/QualityConfiguration
    -> datasource.config.BusinessDatabaseConfiguration
       # infrastructure wiring only

gateway/datasource/DataSourceQualityCatalogAdapter
    -> datasource.catalog.DataSourceCatalogReader
    -> datasource.domain.catalog.CatalogReadRequest
       # typed Catalog capability only
```

不得扩大为 Quality business package 直接依赖 Datasource implementation。

## 8. Persistence Boundary

```text
Manager / Reader / Worker / Policy owner
    -> narrow Quality*Repository port
    -> RepositoryAdapter
    -> DAO / PO / Mapper XML
```

底层规则：

```text
Domain     -> no Quality upper-layer dependency
DAO        -> no Quality business dependency
Repository -> config + dao + domain only
```

Repository contract 不暴露：

- Quality HTTP DTO/VO；
- Quality PO；
- MyBatis `IPage` / Mapper；
- Datasource DTO/VO/PO。

分页 Repository 继续使用 shared `io.yak.framework.common.PageData<T>`。

## 9. Config Boundary

`config` 只负责：

- feature condition；
- properties；
- Flyway；
- executor；
- MapperScan；
- infrastructure configuration import。

业务角色不由 `QualityConfiguration` 手工 new / `@Bean` 反向装配。

因此禁止重新建立：

```text
config -> execution/monitor/asset/template/workspace
         -> config
```

内部专业角色应使用正常 constructor injection + `@Component`。

## 10. No Cycles

不允许通过以下方式掩盖 package cycle：

- `@Lazy`；
- ApplicationContext lookup；
- 静态 Service Locator；
- 把接口随意移动到第三个 `common` package；
- 扩大 dependency-test 白名单。

发现环时先明确能力 owner，再建立窄 Gateway / Reader / Lifecycle corridor。

## 11. Adding a New Dependency

新增一个 import 不在允许矩阵时按顺序判断：

1. **类是否放错 package？**
2. **已有 Manager / Reader / Gateway / Repository 是否能表达？**
3. **是否缺一个由能力 owner 定义的窄 contract？**
4. **架构是否真的改变？**

只有第 4 种情况才在同一个 PR 更新 `ARCHITECTURE.md`、本文件和 executable dependency test。