# 08: 库候选暴露整台实例（系统库 + 平台元库可读可写）

**对应需求:** 第 1 轮 轮1 B3（暴露面半边）+ 第 2 轮 轮2 B6（暴露面半边） | 阶段: P1，**裁决先行**

**What to build:** 建模侧的「数据库」候选必须限定在**该数据源被授权访问的库**，而不是整台 MySQL 实例。实测两处（发布弹窗、逆向/导入目录）都列出全 **27 个库**，含 `mysql`、`sys`、`information_schema`、`performance_schema`，以及**平台自身的元数据库** `yak_ops`、`yak_ops_local`、`yak_bak`。也就是说：把 `CREATE TABLE yak_ops.xxx` 的脚本点一下就能发出去，或从 `mysql` 库导字段。当前唯一的兜底是一句「环境只作提示，不做拦截」的文案。

**为什么单列一票、且不随手改**
这是**数据源可见范围契约**，不是 modeling 的局部显示问题：
- 收窄若只在前端做（本地过滤掉系统库），就是**第二套口径**——后端 `listDataSourceDatabases` 仍然全开，任何直调 API 的路径照旧越界，且换一台实例（PostgreSQL/Oracle）系统库名单完全不同，前端清单必然腐烂；
- 正确落点在后端目录服务：按数据源登记的 `database/schema` 与授权范围返回候选，或对系统库做服务端排除；
- 涉及 REST 返回语义变更，按仓库规矩**先出 FYOIP**（英文六节 + `docs/yoips/README.md` 索引 + `[DISCUSS]` 只提文档的 PR + maintainer `[VOTE]`），不接受散装设计稿。

**模块归属:** **datasource（契约与目录服务端）+ datamap + modeling（消费方）**。跨模块。

**Blocked by:** 数据源侧「库可见范围」口径裁决。

**Status:** needs-decision（**越界，需用户授权后另开 FYOIP**；本票只做记录与方案，不落码）

**证据**
- 消费方：`publish-dialog.tsx:130` `listDataSourceDatabases(datasourceId)`、`use-catalog-picker.ts:171/207`；
- 服务端候选为实例全量（同一台 MySQL 的 7 个数据源各自都返回 27 个库）；
- 读路径有 `非生产环境` 确认（`use-catalog-picker.ts:100-101`），写路径无环境判定（见 [06](06-publish-env-misread-and-no-write-gate.md)）；
- 登记表里已有 `database`/`schema` 字段可用（`DataSourceVO.java:22/25`，`DataSourceServiceImpl.java:398-401` 由 `connectionParams` 解析）——收窄有明确依据，不靠猜。

**方案候选（供 FYOIP 讨论，不预设结论）**
- A：目录 API 增加 `scope` 语义，默认只返回数据源登记库；显式 `?all=true` 才给实例全量（并要求该数据源登记环境为 DEVELOP/TEST）；
- B：服务端按方言固定排除系统库/信息模式（MySQL `information_schema|mysql|performance_schema|sys`、PG `pg_*|information_schema`、Oracle 字典用户），仍保留业务库全量；
- C：平台元库（`yak_ops*`）在任何数据源上都永久不可见——与 A/B 叠加，属安全底线而非范围策略。
> 倾向 A+C：A 给出「按登记库工作」的默认，C 是无论开关都成立的红线；B 只是 A 的补充，不能单独成立（挡不住同名业务库）。

**验收清单（裁决后）**
- [ ] FYOIP 通过并合入文档票
- [ ] 直调 `GET .../databases` 也不再返回系统库与 `yak_ops*`（**服务端成立**，前端过滤不作数）
- [ ] 三个建模入口（发布 / 逆向 / 从表导入）候选一致，无第二套清单
- [ ] 非 MySQL 方言各走查一遍（至少 PostgreSQL），排除名单不靠前端硬编码
- [ ] 增加后端 IT 覆盖「系统库不出现在候选」，并登记进已注册 IT 类（不新建 `*IT`，避免动 upstream 的 `scripts/tests/backend_tests.py`）
