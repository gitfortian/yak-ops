# 01: 集市入口的 type/scope 未解码——应用表建不出来、汇总表变明细表

**对应需求:** 第 2 轮验收 轮2 B1（P0）+ 轮2 B4（P1）合并 | 阶段: P0

**What to build:** 维度建模左树的「新建应用表」「新建汇总表」两个入口，点进去必须得到**一张同类型的模型**。今天做不到：
- `新建应用表` 传 `type:"ADS"` 但**不传 `scope`**（[dimension-modeling/index.tsx:434](../../../yak-ops-ui/apps/web/app/data-modeling/dimension-modeling/index.tsx)），而编辑器预置只解码 `DIM`/`DWD`（[logical-models/editor.tsx:220-225](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/editor.tsx)）。`scope` 解不到就保留 `INITIAL_FORM.scope = "PUBLIC"`（editor.tsx:118、226-231、303-316），于是 `validate()` 把 ADS 塞进 PUBLIC 分支去要一个界面上根本不存在的「概念维度」（editor.tsx:568-570）→ **保存零请求、零可见提示、死锁**。实测只有手拼地址栏 `?scope=APPLICATION&categoryId=…&datamartId=…` 才建得出 `ads_item_sales_top`。
- `新建汇总表` 传 `type:"DWS"`（index.tsx:358），同一段解码把 DWS 丢掉，表单渲染成 `模型类型=明细表 / 数仓分层=DWD`。而表单明写「类型创建后不可变更」→ 用户按按钮语义填完保存，得到一张**改不回类型的错表**。

**模块归属:** **modeling（纯前端）**——`logical-models/editor.tsx` 的 URL 预置解码 + `dimension-modeling/index.tsx` 的入口参数。

**Blocked by:** 无。

**Status:** 已解决（2026-10-29，`editor.tsx` + `layer-binding.ts`，浏览器实测 + node 契约测试）

**根因证据（本仓已复核）**
- `editor.tsx:218-239` `prefill` 只把 `type` 认成 `DIM`/`DWD`，其余 → `null`；`scope` 只认 `APPLICATION`/`PUBLIC`，其余 → `null`。
- `editor.tsx:116-131` `INITIAL_FORM = { type:"DWD", scope:"PUBLIC" }`；`editor.tsx:303-316` 预置 effect 用 `prefill.x ?? previous.x`，**未解码即静默沿用 DWD/PUBLIC**。
- `editor.tsx:565-571` 归属校验三分支：`DWD|DWS → domain+process`；`scope==="PUBLIC" → domain + 概念维度`；`else → datamart`。
- `editor.tsx:599-620` 提交的 `ModelInput` **不含 `scope`**——`scope` 是纯前端概念，且 `type` 已能决定归属；DWD/DWS 走 domain/process，ADS 走 datamart。
- **只有 DIM 才真正需要 scope 二义**：归属类型单选（PUBLIC/APPLICATION）只在 `form.type === "DIM"` 时渲染（`editor.tsx:834-843`）；DWD/DWS/ADS 三类的归属完全由 `type` 决定。
- **死锁的最后一环**：`validate()` 在 `scope === "PUBLIC"` 分支要求 `dimensionId`（:568-570），而「概念维度」控件也只在 `form.type === "DIM"` 时渲染（:904-915）→ ADS + 泄漏的 `scope="PUBLIC"` 时，**被要求的字段在 DOM 里根本不存在**，用户无任何可操作出口；同一分支还额外渲染出必填星号的「数据域」（:846、:862-871），进一步误导（见 P2 票 25）。
- `dimension-modeling/index.tsx:344 / 358 / 378 / 420 / 434` 五个 `openEditor` 调用点：只有 378/420（两条 DIM 路径）带 `scope`；`default` 分支的裸「新建模型」（:473-483）不带任何参数。

**修法（已实施）**
1. `layer-binding.ts` 新增 `modelTypeFromCode(value)`：以 `MODEL_LAYER_CODE` 的键（对 `ModelType` 穷尽，新增类型不改这张表就编译不过）解码 URL 的 `type`，认不出的一律 `null`；`editor.tsx` 的 `prefill.type` 改用它，DWD/DIM/DWS/ADS 四类入口全部生效，分层默认值继续走同一份 `defaultLayerForType`。
2. **没有**给 `ModelInput` 加 `scope`，也没有让 `index.tsx` 补 `scope` 参数：`scope` 只在 `type === "DIM"` 时才有语义，因此编辑器内所有归属判断统一读一个派生标记 `publicDimension = form.type === "DIM" && form.scope === "PUBLIC"`（校验分支、提交 `domainId`、归属区渲染、概念维度必填与空提示、集市为空时的引导 Alert）。非 DIM 类型不再受 `INITIAL_FORM.scope = "PUBLIC"` 泄漏影响，按钮切类型也不再能走进「要一个 DOM 里不存在的字段」的死支。
3. 非法 `type`（`ODS`/拼错/已删类型）不再静默：`模型类型` 字段提示「链接指定的类型「XXX」不被支持，已按默认类型打开，请确认模型类型」，`ownershipLocked` 的原提示保持在其后（仅当类型可解码时显示）。

**验收清单**
- [x] 点「新建应用表」→ 表单 `模型类型=应用表`、`数仓分层=ADS · 应用层`（前缀 `ads_`）、归属为必填「数据集市」且已带出「电商交易集市」；填表名+中文名后保存 **POST /api/v1/data-modeling/dimension-modeling/models → 200**，toast「创建成功」，重开详情页类型/分层仍为 应用表/ADS（留档 id `109129379416563712`，见 tickets.md §六）
- [x] 点「新建汇总表」→ 表单 `模型类型=汇总表`、`DWS · 汇总层`，业务分类/数据域/业务过程按链接 `?type=DWS&categoryId=&domainId=&processId=` 带出（电商业务/交易域/商品支付），无「概念维度」「数据集市」
- [x] 既有路径零回归：DIMENSION 节点 `?type=DIM&…&locked=1` 仍为 维度表 + 公有层 + 概念维度必填并提示「从概念维度进入，类型与归属已锁定」；DATAMART 节点 `?type=DIM&scope=APPLICATION` 仍为 数据集市必填 + 概念维度选填 + 归档分类；控制台无报错
- [x] 非法 `type=ODS` → 表单按默认打开并在「模型类型」显示拒绝提示（实测文案见上）
- [x] `layer-binding.test.mjs`（已在 `test:unit` 注册）补 3 条：四类全覆盖、`ODS/STG/dwd/""/null` 一律 `null`、解码后的类型能落到自己那层
- [x] `npx tsc --noEmit` exit 0；`npx oxlint … --deny-warnings` 0 warnings；`npx oxfmt --check` 通过；`npm run test:unit` 84/84；`npm run architecture:check` 16/16
