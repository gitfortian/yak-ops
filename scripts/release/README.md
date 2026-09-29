# Release Tooling

本目录只负责 Yak Ops 产品 Release 的机械准备与验证，不负责决定一个版本是否允许发布。

最终 Release Decision 以 `docs/release/README.md` 和对应版本 Contract 为准。

## 版本归一

准备新版本：

```bash
bash scripts/release/prepare-release-version.sh 1.0.0
```

该命令统一更新：

- Maven Reactor 版本。
- `release.env`。
- Frontend `package.json`。
- Frontend `package-lock.json`。
- `compose.yaml`。
- `compose.without-mysql.yaml`。
- `.env.example`。

执行后会自动运行版本一致性检查。

## 版本一致性

检查当前仓库：

```bash
bash scripts/release/check-release-metadata.sh
```

Release / Tag 场景可以额外传入期望版本：

```bash
bash scripts/release/check-release-metadata.sh v1.0.0
```

检查范围包括：

- `release.env`。
- 根 Maven Product Version。
- Distribution / Frontend Maven Version。
- Frontend package / package-lock Version。
- 两套 Compose 默认镜像 Tag。
- `.env.example` 镜像 Tag。

普通 Quality Check 会执行该检查，避免版本漂移重新进入 `main`。

## Distribution 构建与验证

先构建 Frontend：

```bash
cd yak-ops-ui
npm ci --prefer-offline --no-audit --no-fund
npm run build
cd ..
```

再构建 Maven Distribution：

```bash
bash mvnw -B -ntp -DskipTests package
```

最后验证：

```bash
bash scripts/release/verify-distribution.sh
```

也可以传入 Release / Tag 期望版本：

```bash
bash scripts/release/verify-distribution.sh v1.0.0
```

验证器要求 `yak-ops-dist/target` 下只有一个 `yak-ops-*.tar.gz`，并检查：

- Tar 顶层目录必须是 `yak-ops-{version}`。
- Backend JAR。
- Frontend `index.html`。
- Runtime config。
- `run-yak-ops.sh` 及执行权限。
- MySQL 5 / 8 内置 Driver。
- LICENSE / NOTICE / README。

通过后生成：

```text
yak-ops-dist/target/SHA256SUMS
```

Docker Image 的构建、Compose Smoke Test 和正式 Release Workflow 由后续 Release Gate 负责。
