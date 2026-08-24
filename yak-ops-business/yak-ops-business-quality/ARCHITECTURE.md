# Data Quality Architecture

本文件描述 Data Quality **当前长期架构**。它只记录现在有效的 package、角色、truth ownership 和调用边界，不记录 Stage / Wave 迁移过程。

需求看 `REQUIREMENTS.md`，领域规则看 `DOMAIN.md`，依赖矩阵看 `DEPENDENCIES.md`，统一工程规范看仓库根目录 [`../../CODE_STYLE.md`](../../CODE_STYLE.md)。

## 1. Principles

1. **业务子系统优先。** package 本身表达架构，而不是统一堆进 service/impl。
2. **角色名表达职责。** Manager / Reader / Policy / Factory / Dispatcher / Worker / Recorder / Gateway / Adapter 不互相冒充。
3. **Command 与 Read Side 分开。** Reader/Projector 不修改 Monitor/Execution 生命周期。
4. **执行快照冻结。** Worker 消费 enqueue-time `QualityExecutionPlan`，不回读 current Monitor 改写本次执行。
5. **外部能力停在 Gateway。** Datasource typed Catalog 通过 Quality-owned Gateway 进入。
6. **Persistence 走 Repository port。** Application role 不直接依赖 DAO/PO/MyBatis。
7. **Schedule 是投影。** MonitorSettings 是业务配置 truth，Yak Schedule 只负责触发。
8. **架构规则可执行。** dependency/corridor/code-style tests 与文档共同维护。

## 2. Package Map

```text
io.yak.ops.business.quality
├── controller
│   └── v1/mapper          # HTTP inbound / DTO-VO mapping
├── asset                  # table registration + candidate/read policy
├── monitor                # monitor/rule/settings lifecycle
├── execution              # admission / plan / dispatch / worker
├── alert                  # alert evidence recorder
├── schedule               # schedule lifecycle / framework bridge / callback
├── template               # built-in/custom template + folder
├── workspace              # report/workspace/log read side
├── gateway
│   └── datasource         # Quality-owned Datasource port + adapter
├── repository             # narrow persistence ports + adapters
├── dao                    # MyBatis persistence primitives
├── domain
│   └── execution          # immutable execution snapshot
└── config                 # properties / condition / wiring
```

production 不创建 `service / common / helper / utils / base` 业务大桶。

## 3. Application Entry

Quality 当前**没有额外的通用 `@Service` facade**。

Controller 直接进入已经声明的专业 Application role：

```text
QualityTableAssetController
    -> QualityTableAssetManager
    -> QualityTableAssetReader
    -> QualityTableCandidateReader

QualityMonitorController
    -> QualityMonitorManager
    -> QualityMonitorReader

QualityExecutionController
    -> QualityExecutionManager
    -> QualityExecutionReader

CustomTemplateController
    -> CustomTemplateManager / Reader
    -> TemplateFolderManager / Reader

QualityWorkspaceController
    -> QualityWorkspaceReader

QualityExecutionWorkspaceController
    -> QualityExecutionWorkspaceReader
    -> QualityExecutionLogProjector
```

这不是“没有 Application 层”，而是 Application role 已经通过 Manager/Reader 等明确命名表达，不需要再包装一层无业务价值的 Service。

## 4. Asset Subsystem

```text
Controller
 -> QualityTableAssetManager / Reader / CandidateReader
        |
        +-> QualityTableTargetPolicy
        +-> QualityTableAssetRepository
        `-> QualityDataCatalogGateway
```

角色：

- `QualityTableAssetManager`：注册/取消注册生命周期；
- `QualityTableAssetReader`：已注册资产 read model；
- `QualityTableCandidateReader`：Datasource 物理表候选列表；
- `QualityTableTargetPolicy`：database/schema/table identity 与字符串标准化规则；
- `QualityDataCatalogGateway`：Quality 所需的 Datasource 最小能力。

Manager 不把 Reader 当 Helper，Reader 也不拥有注册状态变化。

## 5. Monitor Subsystem

```text
QualityMonitorManager
    ├── QualityMonitorPolicy
    ├── QualityRulePolicy
    ├── QualityMonitorSettingsPolicy
    ├── QualityMonitorRepository
    ├── QualityExecutionRepository   # active-execution safety
    `── QualityScheduleLifecycle

QualityMonitorReader
    `── QualityMonitorRepository
```

Manager 拥有 Monitor/Rule/Settings 的事务性生命周期；Policy 只做 deterministic validate/normalize；Reader 只查询。

## 6. Execution Subsystem

高风险主路径保持显式：

```text
QualityExecutionManager
    -> lock/validate admission
    -> insert WAITING Execution
    -> QualityExecutionPlanFactory
    -> immutable QualityExecutionPlan
    -> QualityExecutionDispatcher
         -> afterCommit
         -> qualityExecutionTaskExecutor
         -> QualityExecutionWorker
              -> QualitySqlCompiler
              -> QualityMetricEvaluator
              -> QualityDataCatalogGateway
              -> QualityExecutionRepository
              -> QualityMonitorRepository
              -> QualityAlertRecorder
```

角色：

- `Manager`：受理 manual/scheduled execution；
- `PlanFactory`：冻结本次执行需要的 Monitor/Rule/Settings 快照；
- `Dispatcher`：事务提交后分发，不拥有最终结果；
- `Worker`：执行规则并提交 execution evidence；
- `Compiler` / `Evaluator`：规则 SQL 和 metric 判断；
- `Reader`：查询 Execution；
- `Receipt`：受理结果值对象。

## 7. Schedule Subsystem

```text
Monitor Manager
    -> QualityScheduleLifecycle
          -> QualityScheduleEngineBridge
          -> QualityMonitorRepository

Yak Schedule callback
    -> QualityScheduleHandler
          -> validate current Monitor/Settings
          -> QualityExecutionManager.runScheduled
          -> refresh runtime projection
```

`QualityMonitorSettingsPolicy` 可以使用 `QualityScheduleCalculator` 计算/验证 cron 表达，但 schedule package 不反向调用 Monitor Manager。

`QualityScheduleReconciler` 负责从业务配置恢复 framework projection；它不拥有 Monitor 配置 truth。

## 8. Alert Subsystem

```text
QualityExecutionWorker
    -> QualityAlertRecorder
         -> QualityAlertRepository
```

Recorder 是 best-effort notification evidence boundary。Alert 写入失败只记录日志，不改写已经确认的 Execution result。

## 9. Template Subsystem

```text
QualityTemplateReader
CustomTemplateManager / Reader / Policy
TemplateFolderManager / Reader
        -> narrow template repositories
```

Template Policy 负责自定义 SQL 和参数 schema 的 validate/normalize；Template Manager/Folder Manager 负责生命周期；Reader 不写状态。

## 10. Workspace Read Side

```text
QualityWorkspaceReader
    -> QualityMonitorReader
    -> QualityWorkspaceRepository

QualityExecutionWorkspaceReader
    -> QualityExecutionWorkspaceRepository

QualityExecutionLogProjector
    -> persisted Execution / RuleExecution values only
```

Workspace 只做查询与 projection，不能出现 Monitor save/delete、Execution run 或 Schedule sync。

## 11. Datasource Boundary

Quality 与 Datasource 的唯一实现级连接点：

```text
QualityDataCatalogGateway               # Quality-owned port
        ^
        |
DataSourceQualityCatalogAdapter         # only external adapter
        |
        v
Datasource DataSourceCatalogReader
```

只有该 Adapter 可以 import `io.yak.ops.business.datasource.*`。

Asset / Execution 不允许为了方便直接调用 Datasource Reader、Repository、DAO、Plugin 或 Controller。

## 12. Persistence Boundary

```text
Application role
    -> Quality*Repository interface
    -> Repository Adapter
    -> Quality*Dao
    -> PO / Mapper XML / MyBatis
```

Repository port 不暴露 DTO/VO/PO/MyBatis。

目前较大的 `QualityRepositoryAdapter` 仍是 persistence adapter，不是 Application Service；它实现多个窄 port 是当前持久化实现选择。若未来需要拆分，应按 persistence responsibility 独立重构，不改变 port contract。

## 13. Truth Ownership

```text
TableAsset                 = registered physical quality target
Monitor + Rules + Settings = current monitor definition truth
QualityExecutionPlan       = immutable enqueue-time execution truth
Execution                  = execution lifecycle/result evidence
RuleExecution              = per-rule evidence
Monitor last-*             = execution projection
AlertEvent                 = notification evidence
Yak Schedule               = trigger projection
Datasource Catalog         = physical metadata evidence
```

出现两个角色同时“决定”同一个 truth 时，先修 ownership，不要通过事件、静态工具或新的 Context 掩盖冲突。

## 14. Change Rule

新增或移动代码前依次回答：

1. 属于哪个 Quality subsystem？
2. 角色是什么？Manager/Reader/Policy/Worker/Gateway 是否准确？
3. 它拥有哪个 truth，还是只读取/投影？
4. 新 import 是否符合 `DEPENDENCIES.md`？
5. 是否跨 Datasource？如果是，为什么 QualityDataCatalogGateway 不够？
6. 是否让 Worker 回读 current Monitor 改写 frozen plan？
7. 哪个 behavior test 与 architecture test 会保护这次改动？

答不清楚时不要创建新的 Helper/Common/ServiceImpl。