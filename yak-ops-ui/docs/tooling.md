# Frontend Tooling

Scope: `yak-ops-ui` 的工具、构建与验证。

## Toolchain

前端使用 Vite、React、React Router、Tailwind、TypeScript、Oxlint、Oxfmt 和 npm workspaces。Node 要求、依赖与脚本以 [package.json](../package.json)、workspace manifests 和 [package-lock.json](../package-lock.json) 为准。

Vite 负责开发和生产构建；npm workspaces 负责依赖安装，CI 使用 `npm ci`。架构校验入口是 [check-architecture.mjs](../scripts/check-architecture.mjs)。

## Local Verification

在 `yak-ops-ui/` 下运行：

```bash
npm ci
npm run check
npm run build
```

`npm run check` 依次验证 Oxfmt 格式、Oxlint 静态规则、TypeScript 类型及架构边界。生产构建由 Vite 独立完成。修改局部交互时另运行相应验证，不把静态检查视为浏览器验收。

## CI Quality

[Quality Check](../../.github/workflows/quality-check.yml) 按变更范围分别运行前端格式、Lint、类型、架构与构建检查；CI 检查源码但不自动修改格式。`scripts/ci/detect-changes.sh` 管理变更范围，`scripts/ci/test-detect-changes.sh` 验证范围规则。正式 Release Gate 仍执行完整质量检查。

## Runtime and Interaction Verification

前端静态与逻辑检查使用现有 Quality Check；调度浏览器验收接入 Task Execution Product Acceptance，不另建独立 gate。现有专项脚本可按实际影响选用：

```bash
node scripts/verify-data-sync-definition.mjs
node scripts/verify-login-failure-transition.mjs
node scripts/verify-login-success-transition.mjs
```

上述专项脚本验证其实现的静态或逻辑边界，不代表真实认证、DOM 动画、焦点、弹层定位或端到端链路已经通过。涉及这些行为时进行浏览器和辅助技术检查。

Task Execution Product Acceptance 使用 Python Playwright + Chromium。`scripts/acceptance/task_schedule_ui_test.py` 使用受控响应验证前端交互；`scripts/acceptance/task_schedule_browser.py` 由 `task-execution-e2e.py` 在 `E2E_BROWSER=true` 时调用，复用真实登录会话验证定时 JDBC、实例来源、日志和删除后的调度历史。两种测试的证据分开记录，截图保存在对应 Actions artifact。

开发服务器默认代理后端 `http://127.0.0.1:8080`；浏览器验收通过 `YAK_BACKEND_URL=http://127.0.0.1:18080` 指定验收实例，不改变产品请求路径或 Workspace Header 规则。

验证结果记录在对应 PR / CI，注明实际执行的命令、环境、提交与范围；未运行、跳过或失败的检查不得作为通过证据。
