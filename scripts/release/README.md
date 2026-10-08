# Release Tooling

本目录只负责 Yak Ops 产品 Release 的机械准备与验证，不负责决定一个版本是否允许发布。

最终 Release Decision 以 `docs/release/README.md` 和对应版本 Contract 为准。

## 版本归一

准备新版本：

```bash
bash scripts/release/prepare-release-version.sh 1.2.0-SNAPSHOT
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
bash scripts/release/check-release-metadata.sh v1.1.0
```

检查范围包括：

- `release.env`。
- 根 Maven Product Version。
- Distribution / Frontend Maven Version。
- Frontend package / package-lock Version。
- 两套 Compose 默认镜像 Tag。
- `.env.example` 镜像 Tag。

普通 Quality Check 会执行该检查，避免版本漂移重新进入 `main`。

## Release Migration 检查

开发阶段允许当前未发布 Product Version 存在多个 Draft Migration；进入 Release Freeze 后必须先按 [Flyway Rules](../../yak-ops-dao/FLYWAY_RULES.md) 收口，再执行正式 Release Gate。

检查 Release Migration：

```bash
bash scripts/release/check-release-migration.sh 1.2.0
```

脚本会机械验证：

- `V1__baseline.sql` 仍是首个冻结基线。
- Flyway Version 连续且不重复。
- 除 Baseline 外，正式历史只使用 `V{flywayVersion}__v{major}_{minor}_{patch}.sql`。
- 当前 Product Version 最多一个 Release Migration。
- 当前版本存在 Release Migration 时，它必须是最高 Flyway Version。
- 不允许 Draft / Feature 命名的 Migration 遗留到 Release Gate。
- 不允许高于目标 Product Version 的未来 Release Migration 混入当前候选版本。
- Product Version 没有 Schema 变化时允许不新增 Migration。

正式 Release Gate 会在版本元数据校验后自动执行该检查。Draft checksum 在开发阶段发生变化时应重建可重建数据库，不使用 Flyway repair 掩盖历史差异。

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
bash scripts/release/verify-distribution.sh v1.1.0
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

## Release Gate

正式自动发布门禁由 `.github/workflows/v1-release-gate.yml` 中的通用 `Release Gate` 负责。文件名保留历史兼容，但 Workflow 不再绑定某个具体版本。

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
bash scripts/release/smoke-compose.sh v1.1.0
```

手动触发正式 Release Decision 时必须逐项显式确认：

- 对应版本 Readiness 中列出的 Required Manual E2E 已全部通过。
- 当前版本新增 Migration 已完成最终审查。
- 没有未关闭的 P0 / P1 Release Blocker。
- Release Notes / Readiness / Known Limitations 已准备完成。

Workflow 还会机械检查：

- Release Decision 必须从 `main` 运行。
- 正式 Release Decision 只接受稳定 `X.Y.Z` 版本；`SNAPSHOT` / `rc.N` 只能用于候选验证，不能直接进入正式 Publish。
- `docs/release/v{version}-readiness.md` 存在。
- Readiness 文档第一个 `Status:` 行必须精确为 `Status: Ready`。
- `docs/release/v{version}-release-notes.md` 存在。

Manual E2E 的执行人、日期、环境和 Evidence 统一记录在对应版本的 Readiness 文档。

Release Gate 只判断候选版本是否满足上线门槛，不创建 Git Tag，也不向 Registry 推送镜像。

## Release Publish

正式 `Release Gate` PASS 后，发布动作由 `.github/workflows/release-publish.yml` 负责。

仓库需要预先配置 GitHub Actions Repository Secrets：

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
```

`DOCKERHUB_TOKEN` 使用 Docker Hub Personal Access Token，只授予发布所需的 Read / Write 权限，不把 Token 写入仓库或聊天记录。

Workflow 手动输入：

```text
release_version
release_commit
gate_run_id
publish_latest
```

发布流程：

```text
Verify exact formal Release Gate + Release Decision
→ Checkout exact release commit
→ Download Gate release-candidate Artifact
→ Verify SHA256SUMS
→ Build images from the verified Distribution
→ Local Compose Smoke
→ Push immutable Docker version tags
→ Pull version tags back from Docker Hub
→ Registry Compose Smoke
→ Record registry digests
→ Create immutable Git Tag
→ Create GitHub Release + upload assets
→ Update latest
```

安全规则：

- Tag 只能指向输入的 `release_commit`，该 Commit 必须与正式 Gate 的 `head_sha` 完全一致。
- Publish Workflow 不重新执行 Maven / npm 构建；Docker Image 只消费正式 Gate Artifact 中的 `yak-ops-{version}.tar.gz`。
- 如果不可变 Docker version tag 已存在，必须先验证其 OCI version / revision；验证不一致时立即失败，禁止覆盖。
- Git Tag 已存在时必须解析到同一个 Release Commit，禁止移动 Tag。
- `latest` 只能在 Registry version image 二次拉取和 Compose Smoke、GitHub Release 全部成功后更新。
- 最终 Release Evidence 记录 Distribution SHA-256 与 Docker Hub Registry Digest。
