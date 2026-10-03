# Frontend Tooling

Status: Active

Scope: `yak-ops-ui` 的工具、命令和验证入口。

## Toolchain

Vite、React、React Router、Tailwind、TypeScript、Oxlint、Oxfmt 与 npm 构成当前工具链；具体版本及 Node engines 读取 [workspace package.json](../package.json)、各 workspace manifest 和 [package-lock.json](../package-lock.json)，不另写版本副本。

Tailwind 通过 Vite Plugin 接入，不恢复旧 PostCSS pipeline、Umi、Biome 或第二套 Utility CSS / lint 工具。Vite 是唯一前端开发和生产构建入口。

## Required Checks

在 `yak-ops-ui/` 执行：

```bash
npm ci
npm run check
npm run build
```

本地 check 聚合格式、Lint、类型和架构检查；生产构建独立执行。脚本定义在 package.json，实际 CI 编排见 [Quality Check](../../.github/workflows/quality-check.yml)。

## Physical Quality Gate

- Oxfmt 负责格式；Oxlint 负责静态规则，warning 按失败处理，必要 disable 仅最小范围并说明原因。
- TypeScript 负责 apps / packages 类型校验，不能用 lint 代替。
- Node architecture check 负责可确定判断的目录与依赖边界；Vite 验证真实生产构建。
- CI 独立执行各 gate 且 check-only，不执行 format / lint:fix 改源码，不包装成单一 check 黑盒。
- 检查失败在源头修复，不维护与 formatter 冲突的手工排版规则。

## Architecture Check

[check-architecture.mjs](../scripts/check-architecture.mjs) 是规则执行入口。架构变化同步文档与 enforcement；脚本只能证明其实际覆盖的规则，不能替代业务 review。

## Package Manager

使用 npm workspaces 与 package-lock.json。运行时依赖属于真正的 apps / packages owner；workspace root 只承载工具依赖。依赖变更同步 lockfile，CI 使用 npm ci，不恢复 Yarn 或第二套安装流程。

## Git Hooks

当前不维护 Husky / lint-staged / commitlint gate；不保留依赖已删除的假 Hook。开发、预览和修复命令直接查 package.json，文档不复制完整 scripts 清单。

## Tests

当前前端没有独立自动化测试 gate。引入测试体系需要明确 Contract 与 Tooling，不在普通文档整理中增加框架或放宽现有门禁。编译与静态检查不能证明弹层定位、焦点、动画或真实业务链路。

针对登录失败衔接的纯几何回归可在 `yak-ops-ui/` 执行 `node scripts/verify-login-failure-transition.mjs`。它使用已有 TypeScript 与 Node assert，检查路径拓扑、地面、端点、动态目标与中断重接；不是新增测试框架或默认 CI gate，也不能证明 DOM 采样、浏览器动画、焦点或真实认证。

## Verification Record

组件文档中的验收步骤描述可重复执行的方法，不表示已经通过。一次结果放在 PR / CI；发布结果放对应版本证据，并按 [Engineering Context Model](../../docs/engineering-context-model.md#evidence-chain) 记录提交、环境、执行范围和证据。局部样式夹具、真实 React 运行时、产品 E2E 分开报告；未执行、失败和 skipped 不写成完整验收通过。
