# Frontend Rules

Status: Active

Scope: `yak-ops-ui/apps/**`、`yak-ops-ui/packages/**`、`yak-ops-ui/scripts/**`。

## Ownership

目录和依赖归 [Architecture](ARCHITECTURE.md)。按任务读取 [App](apps/web/APP_RULES.md)、[Form](apps/web/FORM_RULES.md)、[Service](SERVICE_RULES.md)、[Yak UI](packages/yak-ui/UI_RULES.md) 与就近领域规则，不把链接当作全量必读清单。

## Local Cohesion

优先局部内聚：状态放在拥有行为的最小边界，页面私有组件与逻辑留在领域内。只有独立行为、真实复用或复杂度足够时才拆文件；不因名词、文件长度或形式对称创建新层级。

## Must

- 先确定 owner，再放代码；业务私有实现不进入 root `utils / hooks / types / constants`。
- 跨页面运行态归 `context`，跨组件访问 Hook 归 `hooks`，不为局部状态引入 Zustand / Redux。
- App 通过领域 Service 调用后端，组件、页面、Hook 不直接调用原生 `fetch`。
- 产品层使用 `@yak-ops/yak-ui` 公共导出，不直接依赖 Base UI。
- 持续风险提示、瞬时结果与字段错误的选用遵循 [Alert](packages/yak-ui/docs/alert.md)，不要手写另一套提示条。
- 修改行为时维护对应权威契约及验证入口，不把实施进展追加到通用规则。

## Must Not

- 恢复 `apps/web/src`、`apps/web/pages`、`apps/web/shared` 或根 `src/public/types/mock`。
- 恢复 `packages/datasource`、`@yak-ops/datasource` 或第二套 UI / HTTP framework。
- Service 反向依赖 App，或 App 绕过领域 Service 直接依赖 `service/http`。
- 为单一概念建立没有独立行为的目录、另一套表单运行时或全局组件大桶。
- 使用 broad lint disable、删除 enforcement、跳过 formatter 或降低约束让检查变绿。

## Architecture Gate

架构变化同步 Architecture、就近 Rules 和现有 enforcement。检查入口见 [Tooling](docs/tooling.md#architecture-check)。

## Physical Quality Gates

可确定判断的格式、静态规则、类型、架构与生产构建由工具检查。CI 独立暴露各 gate，只检查、不修改源码；不能用聚合命令隐藏具体失败阶段。

## Validation

命令、工具和执行范围只在 [Frontend Tooling](docs/tooling.md#required-checks) 维护。报告实际执行结果，不把编译通过当成浏览器交互验收。
