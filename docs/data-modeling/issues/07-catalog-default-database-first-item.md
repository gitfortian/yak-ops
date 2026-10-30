# 07: 目录选择器默认库取 `values[0]`，与数据源绑定库无关

**对应需求:** 第 1 轮 轮1 B3（默认值半边）+ 第 2 轮 轮2 B7（P1） | 阶段: P1

**What to build:** 选完数据源后，「数据库」必须默认到**该数据源 JDBC URL 里登记的那个库**；不能默认到实例列表第一个。实测：选 `数仓ods`（JDBC 明确绑 `/ods`）后，数据库下拉默认给出 **`ads`**（两轮都复现；轮1 的商品库登记 `item_db`、默认同样落到 `ads`）。用户不手动改就在 ads 库里读/写，**却以为自己在操作 ods**——这是会写错地方的默认值，不是显示瑕疵。

**模块归属:** **modeling（纯前端）**。

**Blocked by:** 无。发布弹窗侧若要同样默认，依赖数据源登记库可用（见「修法」第 3 点）。

**Status:** 已解决（2026-10-29 落码 + 真机逐源走完 7 个 + 两条链路；「登记库不在列表」分支只有单测，本机造不出）。发布弹窗**不做默认**——复核后判定那侧本来就是强制显式选择，见清单第 5 条。

**根因证据（本仓已复核）**
- [use-catalog-picker.ts:171](../../../yak-ops-ui/apps/web/app/data-modeling/use-catalog-picker.ts)（database）与 `:207`（schema）：`setDatabase(previous => previous && values.includes(previous) ? previous : (values[0] ?? ""))` —— 候选来自实例全量列表，**第一项即默认**。
- 同文件 `:99-101` 已经拿到了 `datasource`（含 `environment`），但从未读取其 `database`。
- **数据源登记库前端已可得，无需后端改动**：`service/datasource/types.ts:12-28` 的 `DataSourceRecord` 已含 `database?` / `schema?` / `environment?` / `connStatus?`；后端 `yak-ops-common/.../bean/vo/datasource/DataSourceVO.java:22/25/34/40` 在 `/page` 与 `/{id}` 都填充（`DataSourceServiceImpl.java:222`、`:398-401` 从 `connectionParams` 解析）；`app/datasource/form/model.ts:126-144` 还已有 `parseJdbcUrl`。
  - **本次实测坐实**：`POST /api/v1/data-source/page` 7 台全部带 `database`（`trade_db`/`item_db`/`ods`/`dwd`/`dim`/`dws`/`ads`，与各自 `jdbcUrl` 尾段一致），所以纯前端就能修，不需要后端补字段。
- 使用面（改动波及两个入口，都在本模块内）：`dimension-modeling/reverse/index.tsx:33`、`logical-models/import-dialog.tsx:22`。
- 参照正确做法：采集任务页同类选择器**不给错误默认**（轮1 实测为「未选」）。
- 立票后复核修正：**修法第 2 点（跨源残留）已是既有行为**——`selectDatasource()` 在 `use-catalog-picker.ts:303` 起就 `setDatabase("")` + 清 schema/表/列，`previous` 保留分支只对**同一源的 retry** 生效，不存在换源残留。本票不重做，仅补测。

**修法（已实施）**
1. 新增零导入纯模块 [catalog-default.ts](../../../yak-ops-ui/apps/web/app/data-modeling/catalog-default.ts) `resolveDefaultDatabase(registered, candidates)` → 命中登记库就用它；登记库为空 → `{"", 未登记提示}`；登记库不在候选 → `{"", 「数据源登记库「X」不在该实例的数据库列表中…」}`，**一律不回落到 `values[0]`**。放成独立模块的原因与 `publication-target.ts` 同：`use-catalog-picker.ts` 有 `@/service` 值导入、node 直不起来，纯函数才能当契约测。
2. hook 侧（`use-catalog-picker.ts:166-175`）用它替换 `values[0]`；`hint` 复用 `failures.databases` 通道——两个消费方（`import-dialog.tsx:321-327`、`reverse/index.tsx:338-344`）已经在渲染 `failureMessages`，**不改任何 UI** 就能看见提示。
3. 依赖处理：默认值要读 `datasource?.database`，把它加进该 effect 的 deps（字符串基元，稳定）；而「已手选且仍在候选里就别覆盖」需要读实时 `database`，把它加进 deps 会变成「每选一次库就重拉一次库列表」，故用 `databaseRef`（`use-catalog-picker.ts:102-105`）读，避免这条隐形重复请求。
4. **schema 的 `values[0]` 默认（`:207`）本票不动**：本工作区 7 台 MySQL 数据源 `schema` 全为 `null`，且 MySQL 侧「库即 Schema」；改了没有可验证路径，也不制造一条永真分支。
5. **发布弹窗不加默认库（与修法 3 的偏离，记录在此）**：复核 `publish-dialog.tsx:130-150` 只 `setDatabases(values)`，从不 `setDatabase(...)` —— 真机开弹窗即为「请选择数据库」且「生成建表脚本」disabled（本次票 06 复验时同屏再证一次）。写路径强制显式选择比给默认**更安全**，给默认反而把 07 的 hazard 引进写路径；`LayerDataSourceVO` 也就无需补 `database` 字段（不触发契约变更）。

**验收清单**
- [x] 选 `数仓ods` → 默认库 `ods`；选 `商品库` → 默认库 `item_db`；逐源走查 7 个
  - 真机 `从表/视图导入` 逐源（读「选择数据库」组合框实际值）：`数仓ods→ods`、`商品库→item_db`、`订单库→trade_db`、`数仓dwd→dwd`、`数仓dim→dim`、`数仓dws→dws`、`数仓ads→ads` —— **7/7 命中登记库，无一落到 `ads`**。
  - 关键对照：修复前每个源都落 `ads`（实例列表第一项），而 `ads` 恰是其中一台的登记库，所以旧截图看着「只对 ads 正确」。
- [x] 先选 A 源、再切 B 源：库与表都复位，不残留 A 的选择 —— 真机连切 6 次（ods→item_db→trade_db→dwd→dim→dws→ads）每次「选择数据库」都跟着变成新源登记库，未出现上一源残留；机制在 `selectDatasource()` 既有复位（本票复核确认，非新增）。
- [~] 登记库不在实例列表中 → 空值 + 显式提示，**不回落到 `values[0]`** —— **只有单测**：`resolveDefaultDatabase("staging", INSTANCE)` → `{database:"", hint 含 staging}` 并显式断言 `!== INSTANCE[0]`；`resolveDefaultDatabase("ods", [])` 同样不回落到空列表。真机造不出该状态（7 台登记库都真实存在），要验只能去实例上删库或改登记，属破坏共享数据，不做。
- [x] 两条链路「查询」默认即指向正确库；表列表与预期一致
  - `从表/视图导入`：停在 `数仓ads`/`ads` 勾环境后点「查询」→ 表列表 = **`ads_item_sales_top` 一张**。与物理事实吻合：`ads_pay_item_top_recheck`（0 字段从未发布）与 `ads_ticket05_no_length`（票 05 故意留非法、发布被拦）都**没有物理表**，只有 `ads_item_sales_top` 有过一次成功发布 → 说明查询确实打在 `ads` 而不是别的库。
  - `逆向建模`：选 `数仓ods` → 「数据库」= **`ods`**（第二个消费方同hook同修生效）。
- [x] 发布弹窗默认库 —— **本票判定为「不应有默认」**，理由与证据见修法 5；不改后端 record、不加第二条取数路径。
- [x] 前端契约测试锁死「数据源 → 默认库」映射（含缺失/不在列表两种分支）
  - 新增 `catalog-default.test.mjs` **5 例**（命中、含空格容错、不在列表不回落到 `values[0]`、未登记/`null`/`undefined`/空白、空实例列表）；已在 `yak-ops-ui/package.json` 的 `test:unit` 注册（否则根本不会跑）。
  - 实跑：`npm run test:unit` → **tests 91 / pass 91 / fail 0**（此前 86，新增 5 全绿）。
  - `npx tsc --noEmit` exit 0；`npx oxlint apps/web/app/data-modeling --deny-warnings` → 0/0（79 files）；`npx oxfmt --check` 三个改动文件全合规。
- [x] 环境提示门禁零回归：勾环境前仍出「即将从「开发」环境读取字段， 请确认连接的是目标库；环境只作提示，不做拦截。」
