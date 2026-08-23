# Realtime Sync Domain

> 只描述**当前模型**，不记录阶段演进。历史设计过程看 Git / PR。

## 一张图看懂

```text
RealtimeSyncTask
├── current Draft
└── publishedDefinitionRef
          │
          ▼
   DefinitionVersion (immutable)
          │
          ▼
     SyncExecution
```

核心含义：

```text
RealtimeSyncTask  = 长期任务身份 + 当前草稿
SyncDefinition    = 同步什么、怎么同步
DefinitionVersion = 一次不可变发布事实
SyncExecution     = 某个 Published Version 的一次真实运行
```

**Task、Version、Execution 生命周期分离。**

## SyncDefinition

```text
SyncDefinition
├── SourceEndpoint(DataSourceRef)
├── SinkEndpoint(DataSourceRef)
├── SyncRoute[]
│   ├── SourceSelector
│   ├── SinkTarget
│   └── ReplayKey
├── SyncPolicy
│   ├── StartupPolicy
│   └── SchemaEvolutionPolicy
└── ExecutionPolicy
```

Wizard 和 Yak YAML 都只是同一个 `SyncDefinition` 的编辑方式；Flink Pipeline YAML 是临时编译产物，不是领域事实。

## 典型流程

### 编辑与发布

```text
Save Draft
   ↓
Publish
   ↓
DefinitionVersion V4
```

运行中的旧版本不受影响：

```text
Execution E100(V3) RUNNING
+
Draft r4
+
Published V4
```

### 启动

```text
Task.publishedDefinitionRef
        ↓
DefinitionVersion
        ↓
new SyncExecution
```

Start 不读取当前 Draft。

### 重启与升级

```text
RestartExecution
E100(V3) -> E101(V3)
```

```text
ApplyPublishedVersion
E100(V3), Published=V4 -> E101(V4)
```

两个动作不能混在一起。

## Execution 生命周期

```text
STARTING
RUNNING
STOPPING
UNKNOWN
CONFLICT
```

以上都算 Active / Uncertain，同一 Task 不允许创建第二个 Execution。

```text
STOPPED
FAILED
```

是单个 Execution 的终态；再次运行必须创建新 Execution。

`UNKNOWN` 表示外部事实未确认，不等于失败；`CONFLICT` 表示运行身份有歧义，不能猜。

## 边界

Realtime Sync Core Domain 只保存引用和同步语义，不拥有：

```text
DataSource connection / password
Flink Home / REST / SSH config
Flink JobId 之外的引擎内部模型
Pipeline YAML
JDBC / Connector 私有调优
```

DataSource、Compute Environment 是邻接上下文；Flink/SSH/YAML Compiler 是 Infrastructure。

## 当前兼容名

```text
definition_version  -> DraftRevision
published_version   -> legacy published revision marker
latestDeployment    -> latest SyncExecution projection
config_digest       -> 需按上下文区分 sourceConfigDigest / artifactDigest
```

这些名字为了 v1 兼容暂时存在，不能反推领域模型。

## 开发规则

改 realtime-sync 前先读模块根目录 `DOMAIN.md`。

只有两步：

```text
实现前：Domain Impact Analysis
实现后：Domain Compliance Report
```

机器护栏：

```bash
python3 tools/realtime_domain_guardrails.py
```

关键设计取舍见 [DECISIONS.md](./DECISIONS.md)。
