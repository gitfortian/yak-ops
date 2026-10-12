# Yak Ops

Build, move, and operate data with confidence.

> 当前开发分支保留同步管理功能及 JDBC / CDC Connector 源码，但旧同步引擎已删除，暂不支持实际运行同步任务。历史已发布版本的能力与此不同。

Yak Ops 是一个面向数据集成与数据同步场景的数据平台项目。当前 V1 聚焦在可部署、可验收的核心链路，而不是一次覆盖完整数据平台能力。

## V1 核心能力

```text
Data Integration
├── Datasource
├── Offline Sync
└── Realtime Sync

Operations Center
├── Offline Task Operations
└── Realtime Task Operations

Management Center
├── User Management
└── Workspace Management
```

当前数据同步主路径：

- Datasource：MySQL 5 / 8、PostgreSQL、Oracle。
- Offline：MySQL → MySQL / PostgreSQL / Oracle。
- Realtime：MySQL CDC → MySQL / PostgreSQL / Oracle。
- Offline Write Mode：APPEND / OVERWRITE / UPSERT。
- Realtime Recovery：at-least-once，不声称 exactly-once。

## 文档

- [开发环境与验证](DEVELOPMENT.md)
- [贡献规范与 FLIP](.github/CONTRIBUTING.md)
- [FLIP 提案模板](.github/FLIP_TEMPLATE.md)

- [文档入口](docs/README.md)
- [架构](ARCHITECTURE.md)
- [发布操作](scripts/release/README.md)
- [历史版本与发布说明](https://github.com/weifuwan/yak-ops/releases)

## Distribution

正式 Distribution：

```text
yak-ops-{version}.tar.gz
```

运行 Tar 包需要 Java 21，并需要配置 Yak Ops 元数据库连接与 Datasource Master Key。

启动入口：

```bash
./bin/run-yak-ops.sh
```

Docker 部署可以使用：

- `compose.yaml`：包含 MySQL。
- `compose.without-mysql.yaml`：使用外部 MySQL。

复制 `.env.example` 为本地 `.env` 后，请在任何非本地环境替换示例密码并设置新的 `YAK_OPS_DATASOURCE_MASTER_KEY`。

## Release

Release 不以“代码能编译”作为上线标准。

正式版本需要通过：

```text
Quality Check
→ Full Backend Acceptance
→ Manual Product E2E
→ Distribution Verification
→ Docker / Compose Smoke Test
→ Version Consistency
→ Release Gate PASS
```

发布操作见 [Release Tooling](scripts/release/README.md)。发布说明和证据以 GitHub Releases、Git Tags 及对应 Actions 为准。
