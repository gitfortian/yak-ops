# Frontend Service Rules

Status: Active

Scope: `yak-ops-ui/apps/web/service/**`。

依赖边界见 [Architecture](ARCHITECTURE.md)，通用实现约束见 [Frontend Rules](FRONTEND_RULES.md)。

## Ownership

`service/http` 是唯一 HTTP transport，拥有 HttpUtils、统一 Result、JSON、网络和认证失败处理。领域 Service 拥有 endpoint、参数适配及后端请求/响应类型；页面拥有 UI 状态。

数据源调用包含 CRUD、批量操作、连接测试与只读 Catalog；数据同步调用包含定义、发布、执行、调度及运维读取。具体函数以真实 App 调用链为入口，不在文档复制 endpoint 清单。

## Service Locality

默认使用 `service/<domain>/index.ts + types.ts`。只有独立 transport、协议、生命周期或明确的阅读复杂度才继续拆分，不按 CRUD / Catalog 等名词机械创建小文件，不添加 interface / impl / adapter 层。

## Dependency Invariant

- 领域请求统一经过 HttpUtils；原生 `fetch` 仅属于 transport。
- 请求/响应类型与对应 Service 放在一起；App 可以导入或重新导出，Service 不导入 App model 或 UI 组件。
- UI 只消费业务 data，不解析 `Result<T>`；HttpUtils 不持有数据源或同步业务规则。
- 后端错误保留失败语义，不制造假成功数据。
- User Preference 请求显式省略 Workspace Header；其他请求按接口的真实 scope 使用 transport，不在页面手工拼接上下文。
- 不创建 axios、umi-request 或第二套 transport，不在 Service 保存页面状态。

## Service Export Lifecycle

新增函数必须有真实 App caller，或与调用方一同交付；后端存在某接口不构成新增前端导出的理由。删除函数时同时清理只为它存在的类型、endpoint 常量和适配代码，不保留未来页面占位。

## Enforcement

确定性的依赖检查见 [architecture check](scripts/check-architecture.mjs)。导出是否有业务价值、错误语义是否正确仍需结合调用方审查，不能仅凭静态门禁通过宣称全部满足。
