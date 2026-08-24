# Data Development Domain

> `REQUIREMENTS.md` 定义“需要什么”；本文件定义“哪些领域事实不能被实现细节破坏”；`ARCHITECTURE.md` 定义这些事实由哪些代码角色负责。

## 核心模型

```text
DevelopmentNode
├── executable node
│     │
│     ▼
│  DevelopmentTaskDraft (mutable)
│     │ publish
│     ▼
│  DevelopmentTaskRevision (immutable)
│     │ project
│     └──────────────> Task Catalog
│
│  current editor definition
│     │ run
│     ▼
│  DevelopmentTaskExecution
│
└── output node
      ├── DATASET
      └── DATA_SERVICE
             │
             ├── DevelopmentDataServiceDraft
             └── DevelopmentDataServiceRevision
```

核心关系：

```text
Node != Draft != Revision != Execution
```

## Truth Ownership

```text
DevelopmentNode             = 工作区长期身份与位置
DevelopmentTaskDraft        = 当前可编辑定义
DevelopmentTaskRevision     = 不可变发布版本
DevelopmentTaskExecution    = 编辑器运行历史
Task Catalog                = 发布状态 / 跨模块任务资产投影
Data Service Revision       = Data Service 不可变发布事实
Lineage subsystem           = Lineage Asset / Relation 最终事实
Task Runtime                = 外部执行事实
```

Task Catalog 不能反向成为 Data Development Revision 的存储真相；Lineage 也不能成为 Task 发布状态的真相。

## 12 条硬规则

1. **Node 是身份，不是版本。** 编辑定义和发布内容不能继续堆进 `DevelopmentNode`。
2. **Node Type 能力必须显式声明。** 是否支持 Task Lifecycle 只由 `DevelopmentNodeType.supportsTaskLifecycle()` 决定。
3. **Draft 可变但有版本。** 每次保存通过 `draftRevision` 做 optimistic concurrency check。
4. **Published Revision 不可变。** 发布后只能新增 Revision 或切换 Catalog 指针，不能修改历史 Revision。
5. **Draft、Revision、Execution 必须分离。** 运行或发布不能偷读错误的生命周期对象。
6. **Editor Run 不等于 Publish。** 编辑器运行使用临时 `TaskVersionSnapshot(version=0)`，不能隐式发布。
7. **TaskDefinition 只有一套规范化规则。** Node Type 对齐、schemaVersion、content 和 configJson normalization 不能在多个 Service 各复制一份。
8. **Publish 校验由 Task Plugin 提供能力事实。** Data Development 负责协调，不复制插件内部 SQL/SHELL/HTTP 规则。
9. **相同 Draft + 相同 Digest 重复发布必须幂等。** 可以重复修复 Task Catalog projection，但不能重复插入等价 Revision。
10. **DATASET / DATA_SERVICE 不是 executable Task。** 输出节点不得为了复用 Service 而进入 Task Draft / Revision 生命周期。
11. **Lineage 是派生事实。** SQL Revision 是证据来源；Asset / Relation 的最终 owner 是 Lineage 模块。
12. **结构重构不能偷改行为。** package move、角色拆分、领域语义变更、REST/DB 变更必须分开处理。

## Task Definition Contract

`TaskDefinition` 是 Data Development 与 Task Plugin / Task Runtime 共享的逻辑定义表示。

进入 Draft、Publish、Editor Run 前必须统一规范化：

```text
taskType       -> trim + upper-case + equals Node.type
schemaVersion  -> > 0
content        -> null becomes empty string
configJson     -> JSON Object + canonical serialization
```

同一规则不得在 `DevelopmentTaskService`、`DevelopmentTaskRunService`、Controller 等位置重复实现。

## Publish Contract

```text
require executable Node
    -> lock Draft
    -> verify expected draftRevision
    -> normalize TaskDefinition
    -> Task Plugin validation
    -> calculate Definition Digest
    -> reuse or append immutable Revision
    -> reconcile Task Catalog projection
    -> enqueue SQL lineage evidence work
```

`DevelopmentTaskRevision.represents(draftRevision, checksum)` 表达“现有不可变版本是否已经代表当前 Draft”的领域判断。

## Data Service Contract

Data Service Definition 可以自己持有发布不变量，例如 `validatePublishable()`。

它与普通 Task 的关系是相邻能力，不是继承关系：

```text
Task Revision        -> shared Task Runtime
Data Service Revision -> Data Service Runtime
```

后续结构调整应继续强化这个边界，而不是把两者重新塞回一个通用 Service。

## Lineage Boundary

```text
SQL DevelopmentTaskRevision
    -> parser / analyzer
    -> lineage evidence
    -> yak-ops-business-lineage
```

Data Development 可以持有 Parser、Analyzer、Outbox、Worker 等角色，但不得复制 Lineage Asset / Relation 的领域所有权。

## 修改代码前后

涉及领域行为的修改，先写：

```text
Domain Impact Analysis
- Aggregate / truth owner:
- Draft / Revision / Execution impact:
- Invariant impact:
- Runtime / Catalog / Lineage boundary impact:
- Domain Gap: yes/no
```

结构调整完成后确认：

```text
Domain Compliance Report
- Rule preserved/implemented:
- Behavior compatibility:
- Tests:
- Known gaps:
```

Stage 2 会继续用依赖测试把这些 contract 固化；Stage 1 不通过删除现有行为测试来换取重构通过。