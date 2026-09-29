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

## V1 Release Gate

V1 自动发布门禁由 `.github/workflows/v1-release-gate.yml` 负责。

它会复用现有 Quality Check，并强制 Backend Acceptance 执行 Full Sweep，然后继续执行：

```text
Distribution Build
→ Distribution Verification
→ Backend / Frontend Docker Build
→ OCI Version / Revision Check
→ Compose Startup
→ Login through Nginx
→ Current User Check
→ Release Candidate Evidence
```

Compose Smoke 也可以在本地对已经构建好的正式版本镜像执行：

```bash
bash scripts/release/smoke-compose.sh v1.0.0
```

手动触发正式 V1 Release Decision 时还必须逐项显式确认：

- OFFLINE-001 / APPEND 已通过。
- OFFLINE-002 / OVERWRITE 已通过。
- OFFLINE-003 / UPSERT 已通过。
- REALTIME-001 / Snapshot + INSERT/UPDATE/DELETE 已通过。
- REALTIME-002 / persisted-offset continuation 已通过。
- V1 Flyway baseline 已完成最终审查。
- 没有未关闭的 P0 / P1 Release Blocker。
- Release Notes 与 Known Limitations 已准备完成。

Manual E2E 的执行人、日期、环境和 Evidence 统一记录在 `docs/release/v1.0.0-readiness.md`。

Release Gate 只判断候选版本是否满足上线门槛，不创建 Git Tag，也不向 Registry 推送镜像。
