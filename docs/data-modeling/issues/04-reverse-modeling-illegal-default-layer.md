# 04: 逆向建模默认分层与「类型↔分层」规则冲突，每次必产非法模型

**对应需求:** 第 2 轮验收 轮2 B3（P1） | 阶段: P1

**What to build:** 「采集 → ODS → 逆向建模」是用户点名的业务链路，但今天它**走不通**：逆向向导 step2 的分层默认值是「第一个启用的分层」= `ODS 贴源层`，而默认类型是 `明细表`。正向编辑器把类型↔分层锁成 1:1（明细表只能 DWD，其余全 `(禁用)`），**ODS/STG 不承载任何模型类型**。于是保存必成功（200），随后编辑器立刻弹「该模型当前所在的 ODS 层与类型不匹配」。用户被迫二次进编辑器改层，而改层又把表名从 `ods_trade_orders` 改成 `dwd_trade_orders`——**与贴源表的对应关系就此断裂**。

**模块归属:** **modeling（前端默认值 + 校验）**；「ODS/STG 到底能不能建模」属分层语义，**不在本票**。

**Blocked by:** 无（设计裁决项已切到「明确不做」，见 tickets.md §四）。

**Status:** 已解决（2026-10-29 落码 + 真机复跑全链路留档）

**根因证据（本仓已复核）**
- [reverse/index.tsx:126-131](../../../yak-ops-ui/apps/web/app/data-modeling/dimension-modeling/reverse/index.tsx)：`if (layerId) return; const selectable = options.layers.find((layer) => layer.enabled); if (selectable) setLayerId(selectable.id);` —— **只看 enabled，不看类型规则**。注释声明的理由是「未启用层没有绑定数据源，服务端会 19509」，理由成立但条件不足。
- :195 校验只有 `layerId ? null : "请选择数仓分层"`；:421 下拉同样只 `filter(layer => layer.enabled)`。
- 规则已有权威实现，直接复用：[logical-models/layer-binding.ts:13-43](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/layer-binding.ts) 导出 `MODEL_LAYER_CODE`（DWD→DWD / DIM→DIM / DWS→DWS / ADS→ADS）、`layerMatchesType`、`layersForType`、`defaultLayerForType`。
- **第三处（本轮补查）**：`startDraft` 与 `changeLayer` 调 `draftTableName(name, layerId)`，而该函数第二参在 [reverse-modeling-draft.ts:93](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/reverse-modeling-draft.ts) 的契约是 **layer enCode**。传 id 时前缀永不命中，剥离逻辑是死代码；服务端 `ModelServiceImpl.suffix()` 会替它兜底剥掉所选层前缀，于是**表单显示的后缀与最终落库名不一致**（物理表 `dwd_trade_order_detail` 的后缀框显示 `dwd_trade_order_detail`）。
- 实证产物（**保留**，勿删）：`dwd_trade_orders` `109116595379298304`（17 字段/PK 1/每日全量/365，未发布）。

**修法（已落）**
1. step2 默认分层改为**按已选类型反查**：`defaultReverseLayer(options.layers, type)`（内部即 `defaultLayerForType`，只再拒绝未启用层，保住 19509 那条理由）；无合法层时不给默认值，页顶 Alert + 保存校验给同一条显式原因「明细表只能建在 DWD 层，当前工作空间没有启用的该分层，请先到「数仓规划」创建该分层并绑定数据源」。
2. 类型与分层联动：新增 `changeType`，当前层仍可选则保留，否则改到该类型的启用层并**重算表名后缀**；该类型无可用层时清空分层字段（宁可留空并说明原因，也不落一条非法模型）。
3. 下拉里对不匹配的分层给 `(禁用)` + 原因：`ODS · 贴源层（明细表限 DWD 层）`、未启用层 `（未启用）`，全部层继续列出（受控下拉删项会清空已选值），与正向编辑器同一套口径；该类型在工作空间完全没有对应层时不禁用任何项，改由页顶说明缺口。
4. `startDraft`/`changeLayer` 改传 `selectedLayer?.enCode`，让后缀框与落库名一致。
5. 向导自身的两条分层决策（默认层、可选项）抽到 [reverse/wizard-layers.ts](../../../yak-ops-ui/apps/web/app/data-modeling/dimension-modeling/reverse/wizard-layers.ts)，无运行时 `@/` 值依赖，可在 Node 下直接测；规则本体仍在 `layer-binding.ts`，两页不各定一套。
6. 与票面的一处有意偏差：分层缺失时**保存路径直接拒绝**（正向编辑器只提示不拦，因为它是改存量）；本页是新建路径，产出非法模型正是本票要消灭的东西。

**验收清单**
- [x] 选 `ods.trade_order_detail` → 默认分层 **`DWD · 明细层`**（不是 ODS），后缀默认 `trade_order_detail`、中文名 `订单明细表`，保存得 `dwd_trade_order_detail` `109142750517977088`，编辑器 **0 条横幅 / 0 个 `aria-invalid`**（此前必弹红色不匹配）
- [x] 下拉真机口径：6 个分层全列出，仅 `DWD · 明细层` 可选，其余 5 项 `aria-disabled=true` 且带 `（明细表限 DWD 层）`
- [x] 类型联动：切「维度表 DIM」→ 分层自动变 `DIM · 公共维度层`；切回「明细表 DWD」→ 复原 `DWD · 明细层`，后缀随之重算
- [x] 后缀修复：对 `dwd.dwd_trade_order_detail` 再走一次逆向 → 后缀框显示 `trade_order_detail`（修复前显示 `dwd_trade_order_detail`）
- [x] 单测锁死映射：[reverse/wizard-layers.test.mjs](../../../yak-ops-ui/apps/web/app/data-modeling/dimension-modeling/reverse/wizard-layers.test.mjs) 7 例（含「WAREHOUSE 里第一个启用层是 ods，但 DWD 的默认必须是 dwd」这条反 `find(enabled)` 断言）+ [layer-binding.test.mjs](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/layer-binding.test.mjs) 新增 2 例；已注册进 `package.json` 的 `test:unit`
- [x] 全链路留档：采集→ODS（`ods.trade_order_detail`，9 字段）→逆向→保存 `109142750517977088`→**发布成功 `dwd.dwd_trade_order_detail`**（2026-10-29 12:15:33，「已采集元数据：1 张表 / 9 个字段」）；发布弹窗同时复验票06（数据源显示 `数仓dwd（开发）`）与票07（默认库取登记库 `dwd`，首项 `ads` 不再被选中）。**未删除任何数据。**
- [~] 「未启用对应分层」分支真机未跑：需要停用现有 DWD/DIM 分层，会改到并发会话共用的工作空间配置，故只由单测覆盖（`refuses a disabled layer` / `gives no default when the workspace defines no layer for the kind`）
- [~] 逐类型走查 DWS/ADS 不在本页能力内：向导 `type` 是 `useState<"DWD" | "DIM">`，只提供明细表/维度表；DWS/ADS 的默认分层由正向编辑器承担（票01 已闭环）

**本次跑过的门:** `npm run test:unit` **100/100**；`oxlint apps packages scripts --deny-warnings` **0 warning / 0 error**；`tsc --noEmit` **exit 0**；`architecture:check` + `contract:check` 全通过；`oxfmt --check` 对本票改动的 4 个文件通过（仓内 164 个文件存量格式红与本票无关，含未改动的 `service/.../reverse-modeling.ts`；按既定纪律不跑全量 `oxfmt`）。
