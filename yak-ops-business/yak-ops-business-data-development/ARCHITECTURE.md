# Data Development Architecture

本文件定义 Data Development 的长期角色边界，并记录 Stage 1 的兼容入口。需求语义看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`。

## 设计原则

1. **先有 Truth Owner，再拆 package。** 不为了 DDD 或“去 Service”制造空壳层。
2. **Service 只做稳定入口。** 内部复杂行为使用明确角色名。
3. **角色名表达职责。** Manager / Publisher / Validator / Normalizer / Coordinator / Resolver / Parser / Analyzer / Query / Gateway / Worker 不互相冒充。
4. **Draft / Revision / Execution 分离。** 任何结构都不能把三种生命周期重新揉回一个对象。
5. **外部系统停在边界。** Task Runtime、Task Catalog、Lineage、DataSource Catalog 都通过明确依赖进入。
6. **派生事实不反向拥有业务真相。** Lineage、read model、execution history 都不能成为 Task Revision owner。
7. **结构重构不偷改 REST / DB。** Stage 1 只调整内部职责和领域表达。

## Stage 1 Package Map

```text
io.yak.ops.business.development
├── controller          # HTTP inbound
├── task                # Task authoring domain roles（Stage 1 新边界）
├── domain              # Node / Draft / Revision / execution views / value semantics
├── repository          # Data Development persistence contracts + adapters
├── dao                 # MyBatis persistence primitives
├── service             # 兼容入口 + 尚未迁移的历史实现
└── config              # module configuration
```

`service` 在 Stage 1 仍然存在，但它是**迁移中的兼容区域**：允许保留稳定 Facade 和尚未迁移的旧实现，不再把新的 Parser / Validator / Manager / Coordinator 一律放入该 package。

Stage 2 目标会按业务子系统继续收敛：

```text
node / task / dataservice / release / lineage / editor / repository / dao / config
```

不是为了目录一致强行一次搬完。

## Task Stable Entry

Stage 1 保留：

```text
DevelopmentTaskController / API
        -> DevelopmentTaskService
```

`DevelopmentTaskService` 是兼容 Application Facade，内部职责拆为：

```text
DevelopmentTaskService
├── DevelopmentTaskNodeResolver
├── DevelopmentTaskDefinitionNormalizer
├── DevelopmentTaskDraftManager
├── DevelopmentTaskValidator
├── TaskDefinitionDigestCalculator
├── DevelopmentTaskPublisher
└── DevelopmentTaskRevisionReader
```

职责：

- `NodeResolver`：Node identity lookup 与 executable capability gate；
- `DefinitionNormalizer`：TaskDefinition 唯一规范化入口；
- `DraftManager`：Draft read/save/lock 与 optimistic storage；
- `Validator`：Task Plugin publish validation；
- `DigestCalculator`：发布定义 digest；
- `Publisher`：不可变 Revision append/reuse + Task Catalog projection；
- `RevisionReader`：历史 Revision read side；
- `DevelopmentTaskService`：事务边界、异常兼容和跨角色编排。

## Editor Run

Stage 1 仍保留 `DevelopmentTaskRunService` 作为现有入口，但它不再拥有第二套 Node / TaskDefinition 规则：

```text
DevelopmentTaskRunService
├── DevelopmentTaskNodeResolver
├── DevelopmentTaskDefinitionNormalizer
├── TaskExecutionGateway
└── DevelopmentTaskExecutionService
```

后续 Stage 2 可以继续收敛为 `ExecutionCoordinator / Starter / Recorder / Query`，但本 PR 不为了改名而改名。

## Role Vocabulary

```text
Manager       管一个业务对象的生命周期
Coordinator   编排多个专业角色或外部边界
Publisher     发布不可变版本 / 事件 / projection
Validator     执行业务能力校验
Normalizer    把输入收敛为唯一逻辑表示
Compiler      从逻辑表示生成另一种运行表示
Parser        解析语法或协议
Analyzer      基于解析结果产生静态事实
Resolver      解析引用、身份或上下文
Reader/Query  只读
Gateway       外部系统边界
Worker        异步消费
Reconciler    本地意图与外部事实收敛
Repository    领域持久化 contract
```

如果一个类同时承担三种以上角色，应优先拆职责，而不是继续扩大 `*Service`。

## Domain / Persistence Boundary

```text
domain
   ↑
task roles
   ↓
repository contracts
   ↓
repository adapters / dao
```

Stage 1 允许 `domain` 继续保留现有 JSON serialization annotation 以保证接口兼容，但不新增 Spring Service、MyBatis Mapper、Task Runtime Client 或 Lineage Client 依赖。

`task` package 不直接依赖 Controller / DAO；持久化通过 Repository contract。

## External Corridors

允许的主要外部方向：

```text
Task Publisher     -> Task Catalog
Editor Run         -> shared Task Runtime
SQL publish        -> durable lineage outbox -> Lineage subsystem
Data Service       -> DataSource / Data Service Runtime boundaries
```

这些 corridor 是明确依赖，不等于把对方领域模型复制到 Data Development。

## Behavior Compatibility

Stage 1 必须保持：

- Controller 路径和 DTO 不变；
- DB schema 不变；
- Draft optimistic revision 不变；
- Published Revision append-only / reuse 语义不变；
- Task Catalog projection 不变；
- Editor Run snapshot 语义不变；
- SQL lineage outbox 不变。

## Stage 2

Stage 2 再处理：

- `service` 大桶中的 Data Service / Release / Lineage / Execution 角色化；
- `DEPENDENCIES.md` / `REVIEW.md` / README 收口；
- `@Service` allowlist；
- top-level dependency matrix；
- architecture / dependency executable guards；
- 更彻底的 package move。

Stage 2 的目标是让 package 本身表达业务架构，而不是让 Stage 1 为追求目录漂亮一次性改变所有代码。