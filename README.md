# Yak Ops

Build, move, and operate data with confidence.

Yak Ops 是面向数据集成与同步场景的数据平台。当前 `main` 分支仍处于开发阶段：Task Definition 与 YakFlow Core / Runtime / Connectors 独立存在，产品侧同步执行尚未重新接通。已发布版本的能力请查看对应 Release。

## Build and Run

运行 Distribution 需要 Java 21，并配置元数据库连接和 Datasource Master Key。

```sh
# Docker Compose，内置元数据库
cp .env.example .env
docker compose up -d
```

在非本地环境必须修改示例密码并设置新的 `YAK_OPS_DATASOURCE_MASTER_KEY`。使用外部数据库可选择 `compose.without-mysql.yaml`。

正式 Distribution 为 `yak-ops-{version}.tar.gz`，解压后通过 `./bin/run-yak-ops.sh` 启动。

## Development

- [开发与验证](DEVELOPMENT.md)
- [贡献与 YOIP](.github/CONTRIBUTING.md)
- [YOIP 模板](.github/YOIP_TEMPLATE.md)
- [前端开发规范](yak-ops-ui/FRONTEND_RULES.md)

## Releases

- [历史 GitHub Releases](https://github.com/weifuwan/yak-ops/releases)
- [发布操作](scripts/release/README.md)

发布以真实 CI、Full Backend Acceptance、产品 E2E、Distribution 与 Docker/Compose 验收及版本一致性为准。发布证据来自 GitHub Actions、Git Tags 和 Release Assets。
