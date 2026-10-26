# Yak Ops

Build, move, and operate data with confidence.

Yak Ops 是面向数据集成与同步场景的数据平台。当前 `main` 分支仍处于开发阶段：Task Definition 与 YakFlow Core / Runtime / Connectors 独立存在，产品侧同步执行尚未重新接通。已发布版本的能力请查看对应 Release。

## Build and Run

运行 Distribution 需要 Java 21，并配置元数据库连接和 Datasource Master Key。

```sh
# Docker Compose，内置元数据库
cp .env.example .env
# 按下方说明编辑 .env，然后启动
docker compose up -d
```

首次使用空数据库时，在 `.env` 设置 `YAK_SECURITY_BOOTSTRAP_ENABLED=true` 和独立的强密码 `YAK_SECURITY_BOOTSTRAP_PASSWORD`，启动成功并创建管理员后将开关改回 `false` 并重新部署。已有用户数据的环境保持关闭。没有配置管理员密码时，不允许启用初始化。

在非本地环境必须修改示例数据库密码并设置新的 `YAK_OPS_DATASOURCE_MASTER_KEY`。使用外部数据库可选择 `compose.without-mysql.yaml`，两种部署方式都使用 `YAK_DATABASE_*` 连接元数据库。

正式 Distribution 为 `yak-ops-{version}.tar.gz`，解压后通过 `./bin/run-yak-ops.sh` 启动。

## 数仓规划

登录后选择工作空间，进入「数据建模 → 数仓规划」，维护「业务分类 → 数据域 → 业务过程」三级业务归属。负责人从当前工作空间有效成员中选择；英文编码在所有工作空间的三类节点之间唯一，有子节点的规划节点禁止删除。逻辑模型关联当前尚未开放。

## 数据标准

进入「数据建模 → 数据标准」，按多级目录维护字段编码、逻辑类型、长度/精度、非空约束、安全等级、负责人和业务口径。搜索覆盖当前工作空间的所有分类；标准编码在所有工作空间的标准字段中唯一。目录含子分类或标准时禁止删除，逻辑模型引用能力尚未开放。

## Development

- [开发与验证](DEVELOPMENT.md)
- [贡献与 YOIP](.github/CONTRIBUTING.md)
- [YOIP 模板](.github/YOIP_TEMPLATE.md)
- [前端开发规范](yak-ops-ui/FRONTEND_RULES.md)

## Releases

- [发布历史与 Release Notes](docs/releases/README.md)
- [历史 GitHub Releases](https://github.com/weifuwan/yak-ops/releases)
- [发布操作](scripts/release/README.md)

发布检查包括 CI、JDBC Source/Sink、CDC、独立 `yak-ops-e2e` 模块的产品/恢复集成测试、Playwright UI、Distribution 与 Docker/Compose 验证。`yak-ops-boot` 模块内旧的 E2E 未作为当前测试入口；各测试是否通过以对应 Release Gate 的实际报告为准。发布证据来自 GitHub Actions、Git Tags 和 Release Assets。
