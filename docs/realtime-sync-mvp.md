# 实时同步一期

> 领域设计入口：[Realtime Sync Domain Design](./realtime-sync/domain/README.md)。本文件描述一期 Flink CDC 运行实现，不作为实时同步核心领域模型定义。

## 设计边界

一期不引入 `yak-cdc-runtime`。Yak Ops 负责控制面，复用现有 Flink Standalone Session
Cluster：

- 启动：在 Yak Ops 所在机器执行 Flink CDC CLI；
- 停止和状态：按持久化的 Flink `jobId` 调用 REST API；
- 日志：展示本次 CLI 提交日志和 Flink `/exceptions`；
- 指标：代理 Flink Job、Metrics 和 Checkpoints REST API；
- Connector：由部署人员放入 Flink CDC 的 `lib`，Yak Ops 不依赖 Connector 工程。

浏览器只访问 Yak Ops API，不直接访问 Flink REST。每个任务独立保存 `jobId`，同一个
Session Cluster 可以运行多个实时任务。

## 环境准备

推荐版本为 Flink 1.20.5、Flink CDC 3.6.0 和 Java 21。先准备目录：

```text
/opt/flink
/opt/flink-cdc
```

将需要的 Connector JAR（例如 `yak-flink-cdc-connectors` 的构建产物）复制到
`/opt/flink-cdc/lib`，然后启动 Flink Session Cluster：

```bash
/opt/flink/bin/start-cluster.sh
curl http://127.0.0.1:8081/overview
```

Yak Ops 需要和上述目录位于同一台机器或同一个挂载命名空间。核心配置：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `YAK_FLINK_REST_URL` | `http://127.0.0.1:8081` | Flink REST 地址 |
| `YAK_FLINK_HOME` | `/opt/flink` | Flink 安装目录 |
| `YAK_FLINK_CDC_HOME` | `/opt/flink-cdc` | Flink CDC 安装目录 |
| `YAK_FLINK_JAVA_HOME` | 空 | 可选，提交进程使用的 `JAVA_HOME` |
| `YAK_REALTIME_WORK_DIRECTORY` | `./data/realtime-sync` | 提交日志和短期临时文件目录 |
| `YAK_REALTIME_SUBMIT_TIMEOUT` | `60s` | CLI 提交超时 |

`YAK_REALTIME_WORK_DIRECTORY` 应位于本机受保护目录，不应放在共享 Web 目录。

## 启停流程

启动时 Yak Ops：

1. 校验已发布的任务定义和 Source/Sink 能力；
2. 从数据源管理读取最新凭据，仅在内存中替换密码占位符；
3. 以 `0600` 权限写入短期 Pipeline YAML；
4. 执行：

   ```bash
   /opt/flink-cdc/bin/flink-cdc.sh pipeline.yaml \
     --flink-home /opt/flink \
     --target remote \
     -Drest.address=127.0.0.1 \
     -Drest.port=8081
   ```

5. 从 CLI 输出解析 32 位 Flink `jobId`，保存到部署记录；
6. CLI 退出后立即删除临时 YAML，提交日志按 `jobId` 保存且经过接口脱敏。

停止任务调用 `PATCH /jobs/{jobId}`。Yak Ops 不管理 CLI PID，因为 CLI 完成提交后会退出；
Flink 侧实际执行实体是 Flink Job，领域中的运行实例仍统一称为 `SyncExecution`。

## 状态、日志和指标

| Yak Ops API | Flink 数据源 |
|---|---|
| `GET /api/v1/realtime-sync/{id}` | 本地定义、部署和最近状态 |
| `POST /api/v1/realtime-sync/{id}/reconcile` | `GET /jobs/{jobId}` |
| `GET /api/v1/realtime-sync/{id}/logs` | 本地提交日志 + `/jobs/{jobId}/exceptions` |
| `GET /api/v1/realtime-sync/{id}/checkpoints` | `/jobs/{jobId}/checkpoints` |
| `GET /api/v1/realtime-sync/{id}/metrics` | `/jobs/{jobId}` + `/jobs/{jobId}/metrics` |

后台 Reconciler 按每个部署的 `jobId` 对账。短暂 REST 故障达到配置阈值后才把该任务标为
`UNKNOWN`；任务丢失或终止时不会自动重新提交，避免 At-least-once 场景产生意外重复回放。

状态事件和操作审计保存在业务库；高频 Metrics、Checkpoint 详情和完整 Flink 日志不入库。
完整运行日志仍通过 Flink Web UI 或集群日志系统查看。

## 操作流程

1. 启动 Flink Session Cluster，并确认 `/overview` 正常；
2. 启动 Yak Ops，打开“数据集成 → 实时同步”；
3. 创建任务，选择 MySQL Source、MySQL/PostgreSQL Sink 和表路由；
4. 保存并发布；
5. 点击“启动”，在详情中确认 Flink Job ID 和 `RUNNING`；
6. 在“任务日志”查看提交失败或 Job 异常；
7. 在“Checkpoint / Metrics”刷新实时观测数据；
8. 点击“停止”，Yak Ops 按该任务的 `jobId` 取消 Flink Job。

一期的 Checkpoint 间隔和 Restart Strategy 仍以 Flink 集群配置为准，不向 Pipeline YAML
写入非 Flink CDC Pipeline Options。按任务 Savepoint、恢复启动、Vertex 级指标聚合和集中式
日志检索留到后续版本。
