# Realtime Sync

> **开发 / AI 修改本模块前必读：[`DOMAIN.md`](./DOMAIN.md)**  
> Realtime Sync 的领域设计不是从现有 Flink/SSH 实现反推出来的。任何新需求必须先做 Domain Impact Analysis；无法映射到既有领域模型时标记为 `Domain Gap`，先讨论模型，不直接新增 `syncType / sceneType / *Spec / *Task`。
>
> 完整设计：`docs/realtime-sync/domain/`。

实时同步控制面以 **Compute Environment** 作为唯一运行时配置来源。任务在创建时必须绑定一个已启用的运行环境；每次部署都会保存不可变的运行环境快照，后续修改默认环境或环境配置不会把历史部署重定向到其他 Flink 集群。

## 基线约束

实时同步当前只维护一个 Flyway 基线：

```text
db/migration/yak-realtime-sync/V1__create_realtime_sync.sql
```

这个基线描述当前最终表结构，不承担旧开发版本的原地升级。已经执行过旧 V1-V8 的开发数据库需要删除实时同步相关表以及 `yak_realtime_schema_history` 后重新初始化；不要对已有旧 history 直接执行新的 V1。

新基线不再包含：

- 历史 `ALTER TABLE` / 数据回填；
- `LEGACY` runtime identity 状态；
- 未绑定运行环境的实时任务；
- 长期持久化的 Pipeline YAML。

Pipeline YAML 只在发布/启动时根据任务 Spec 编译，并在提交边界短暂存在。数据库保存结构化 Spec、摘要、部署环境快照和运行标识，不保存带凭据的提交 YAML。

## 运行环境

在 **设置 → 计算引擎** 中维护 Flink CDC 运行环境。当前支持两种提交方式：

- `LOCAL`：Yak Ops 与 Flink CDC CLI 位于同一执行环境，通过本地 `ProcessBuilder` 调用 `flink-cdc.sh`。
- `SSH`：Yak Ops 可以运行在 Windows 或其他机器，通过本机 OpenSSH 客户端连接 Linux 执行节点，在远端调用 `flink-cdc.sh`。

运行环境保存：

- Flink REST URL；
- Flink Home / Flink CDC Home / Java Home；
- Flink / Flink CDC 版本；
- `LOCAL` 或 `SSH` 提交方式；
- SSH 提交节点配置（SSH 模式）。

应用级 `yak.sync.realtime` 配置只负责控制面自身行为，例如工作目录、HTTP/提交超时、日志上限和 reconcile 参数。任务生命周期不再通过 application.yml 猜测运行环境。

## SSH 模式

推荐使用 OpenSSH key 或 ssh-agent，Yak Ops 不保存 SSH 登录密码。SSH 用户不需要 root 权限，但至少需要：

- 能执行 `${flink-cdc-home}/bin/flink-cdc.sh`；
- 能读取 Flink CDC connector 和 Flink 安装目录；
- 能在远端 `${TMPDIR:-/tmp}` 创建临时文件；
- 能从 Linux 执行节点访问运行环境中配置的 Flink REST 地址（或 `remoteRestAddress/remoteRestPort`）。

Yak Ops 使用 `BatchMode=yes`，因此任何需要交互输入密码、验证码或首次确认 host key 的 SSH 登录都会失败。

典型链路：

```text
Windows / Yak Ops
  ├─ HTTP ───────────────> Flink REST
  └─ OpenSSH ────────────> Linux execution node
                              └─ flink-cdc.sh
                                     └─ Flink cluster
```

## Pipeline YAML 安全边界

SSH 模式不会在 Yak Ops 本地创建包含数据源密码的 Pipeline YAML 文件：

```text
Yak Ops 内存中的 Pipeline YAML
  -> OpenSSH stdin
  -> Linux mktemp 临时文件（umask 077）
  -> flink-cdc.sh
  -> trap 自动删除远端临时文件
```

LOCAL 模式只在应用工作目录创建提交期临时 YAML，提交结束后立即删除。两种模式都只长期保留脱敏后的提交 stdout/stderr。

## Runtime identity 与状态恢复

每次部署在进入 Flink CDC CLI 之前都会持久化确定性的 runtime job name：

```text
REQUIRED -> BOUND -> CLI submit
```

如果提交结果不确定或 Yak Ops 在 JobId 落库前退出，reconcile 会使用该 runtime identity 精确查找 Flink Job。新基线不存在旧部署的 `LEGACY` 分支，也不会按用户可见任务名猜测 JobId。

多实例 reconcile 通过 `yak_realtime_runtime_lease` 抢占租约，确保同一时刻只有一个实例执行全局对账。

## 可观测性

当前页面统一使用：

- `GET /api/v1/realtime-sync/{id}/observability`：运行概览、Checkpoint、Metrics；
- `GET /api/v1/realtime-sync/{id}/logs/submission`：Flink CDC CLI 提交日志；
- `GET /api/v1/realtime-sync/{id}/logs/runtime`：Flink Exception History。

旧的 `/logs`、`/checkpoints`、`/metrics` 兼容接口不再作为当前 API 使用。

## 不包含的能力

当前 SSH 模式只解决远程执行 Flink CDC CLI，不包含：

- SSH 隧道代理 Flink REST；
- Runtime Agent / 常驻远端进程；
- SSH 密码托管；
- JobManager/TaskManager 日志文件通过 SSH 下载。

如果 Yak Ops 无法直接访问 Flink REST，应通过内网网络、反向代理或安全网关解决 REST 可达性。
