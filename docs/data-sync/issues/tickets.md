# 数据集成（单表离线/同步）——Ticket 清单（验收轮 1 附带发现）

> 状态：v1.0（2026-10-29）
> 来源：第 1 轮端到端验收。为跑通「业务库 → ODS → 建模」链路，顺带走查了同步任务侧，**这些条目全部属于越界范围**：本工作项只授权修改 `data-modeling/**` 与 `data-map/**` 前端代码，`data-sync/**` **未经用户授权不得改动**。
> 因此本模块**只立票、不落码**；每条已核对代码位置。编号从 **01** 起。

## 一、Ticket 总览

| 编号 | Ticket | 优先级 | 状态 | 归属 |
|---|---|---|---|---|
| 01 | 「下一步」变灰但零说明（`aria-invalid=null`） | P1 | **blocked-by-authorization** | data-sync |
| 02 | 「同步步骤」只在选完类型后出现；目标表缺失时不提示勾选「结构迁移」 | P1 | **blocked-by-authorization** | data-sync |
| 03 | 「同步方案」摘要把 SCHEMA 过滤掉，与实际执行进度矛盾 | P1 | **blocked-by-authorization** | data-sync |
| 04 | 多轮 FULL 执行期间「全量进度」恒 `—`/`0%`，无分母 | P2 | **blocked-by-authorization** | data-sync |
| 05 | 级联加载无「加载中」占位，加载中与可选态不可区分 | P2 | **blocked-by-authorization** | data-sync |

## 二、逐票证据与验收

**01 · 禁用无说明**
[data-sync/create/dialog.tsx:101](../../../yak-ops-ui/apps/web/app/data-sync/create/dialog.tsx) `disabled={!name.trim() || !combination || loading || Boolean(error)}`。清空名称后「下一步」变灰、**无任何文案**、`aria-invalid=null`。
改法：静默禁用要么补内联说明（「请填写任务名称」），要么保持可点 + 点击后报错。二选一，全站同规则。
验收：清空名称 → 有可读原因；键盘用户能感知禁用原因。

**02 · 步骤门控与结构迁移建议**
`create/dialog.tsx:158` 起「同步步骤」组只在选完具体类型后才渲染；默认只勾「全量同步」。目标表不存在而漏勾「结构迁移」→ **运行期**才报 table not found。步骤集来自 `SolutionType.java:7` 与 catalog 组合。
改法：目标表为空/不存在时把「结构迁移」显式提示为建议项（可自动预勾但要可见）。
验收：选一张不存在的目标表 → 出现建议提示；已存在时不骚扰。

**03 · 摘要过滤掉 SCHEMA（信息矛盾）**
[data-sync/solution/presentation.ts:26](../../../yak-ops-ui/apps/web/app/data-sync/solution/presentation.ts) `.filter((step) => step !== "SCHEMA")`（`:10` 明明有 `SCHEMA: "结构迁移"`）。结果：列表/详情/基本信息三处都显示「单表离线 · 全量」，而执行进度条却渲染「结构迁移 100%」——同一任务两种事实。
改法：摘要补「+结构迁移」，与 `:10` 的字典同源。
验收：勾了 SCHEMA 的任务在列表/详情/基本信息三处都显示结构迁移；未勾时不多显示。

**04 · 全量进度无分母**
实测多轮 FULL 执行期间「全量进度」恒 `—`/`0%`，而读取条数已达 26 万，结束时才跳 100%。改法：用 `已提交/预估行数`，或至少显示「已读 N 行 · 进行中」。
验收：执行中能看到单调递增的读数；无「0% 却有 26 万行」的矛盾态。

**05 · 级联加载无占位**
`editor/table-section.tsx:86/114` 用 `refreshing={catalog.loading}`，「表」选择器从「请先选择数据源」直接跳到「请选择表」，加载中不可区分。改法：placeholder 增「加载中…」，与负责人选择器的既有做法对齐（`warehouse-planning/node-dialog.tsx:309` 就是 `ownerState === "loading" ? "加载中" : "请选择负责人"`，仓内已有正确范式）。
验收：切数据源后表选择器显示「加载中…」再落到「请选择表」；失败态有重试。

## 三、备注

- 第 1 轮实测中，**这两条链路本身是健康的**：`trade_db.trade_order_detail → ods`（1,236,712 行，8m23s，全量·按主键更新）与 `item_db.item_item → ods`（结构迁移 + 100 行）都真实成功，主键与类型映射无误。以上条目是交互与信息一致性问题，不影响数据正确性。
- 若用户授权改 `data-sync/**`，建议按 03 → 01 → 02 → 05 → 04 顺序（03 是纯信息矛盾、成本最低；04 需后端进度分母，最重）。
