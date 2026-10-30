# 10: 标准覆盖英文编码导致 ODS→DWD 列名映射断裂，UI 零提示

**对应需求:** 第 1 轮验收 轮1 B5（P1） | 阶段: P1

**What to build:** 绑标准是为了统一口径，但**不能静默改掉与来源表对齐的物理列名**。实测：`dwd_item_sales_detail` 的 `amount`（ODS 实际列名）绑定 `商品销售金额` 标准后，英文编码直接被改成 `item_sales_amount`，发布建出的物理列就是 `item_sales_amount`；UI 全程未提示「该列名与来源表 `ods.trade_order_detail` 不再一致」。下游若用单表离线回填这张 DWD 表，**字段映射在这里断掉**（列名对不上），而且是运行期才炸。

**模块归属:** **modeling（纯前端，会话内告警）**。

**Blocked by:** [09](09-standard-rename-drift-and-silent-rewrite.md)（同一处 `fromStandard` 改写逻辑，一起改避免两次触碰）——09 已闭环，本票已解除阻塞。

**Status:** 已解决（2026-10-30，真机复验：导入链路与逆向链路都出「新旧列名 + 来源表」确认，取消不改写、确认后行内留痕；手工字段不打扰）

**根因证据（修前）**
- 改写实现：[logical-models/fields.ts](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/fields.ts) `fromStandard()` 把 `enCode` 无条件替换为 `standard.enCode`；
- 会话内**没有结构化的来源列信息**：`importNote` 只承载类型映射说明（如「JSON 以文本保存」），`FieldDraft` 里既无来源表也无来源列，绑定前无法判断「这次改写是否切断了与来源表的对齐」；
- **没有持久化的来源列映射**：本模块不存在 `sourceColumn`/`source_column` 字段，`toModelFieldInput` 也不提交来源列——所以「跨会话的映射断裂检测」今天做不到，属**发布资产血缘**范畴（对应 `docs/yoips/FYOIP-16`、`FYOIP-17`），**不在本票偷偷加一列**。

**修法（本票范围＝会话内可判定）**
1. `FieldDraft` 新增**仅会话内**的 `source: { table, column } | null`（[fields.ts:11-34](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/fields.ts)）：`columnToField()` 由「从表/视图导入」带入，`describeFieldDrafts()` 由「逆向建模」带入；`toModelFieldInput()` 不提交、`fromModelField()` 置空，后端与持久化零改动；
2. 纯规则 [standard-binding.ts](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/standard-binding.ts) 新增 `standardBindingWarning()`：有来源列、绑定会改写编码、且新编码不等于物理列名归一化结果时才告警；手工字段、编码不变、或绑定把编码改回物理列名时一律静默；
3. [standard-picker.tsx](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/standard-picker.tsx) 单条绑定先出确认面板（复用 `Alert`，不新增提示样式）：「绑定后英文编码将由「amount」改为「item_sales_amount」，与来源表 ods.trade_order_detail 的物理列「amount」不再一致；下游字段映射需同步调整」＋「返回选择 / 确认绑定」；「返回选择」保留选择器与行状态不变；
4. [field-editor.tsx](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/field-editor.tsx) 确认后复用 `importNote` 通道在行内留痕；「解绑」清空该提示，避免与实际编码错位；
5. [field-mapping.ts](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/field-mapping.ts) 新增 `qualifiedTableName()`，来源表按 schema/库限定（MySQL 取库名、PG/Oracle 取 schema）；
6. 单测挂靠三处：`standard-binding.test.mjs`（告警判定 + 行内留痕）、`field-mapping.test.mjs`（来源带入 + 表名限定）、`reverse-modeling-draft.test.mjs`（逆向来源带入）。

**验收清单**
- [x] 从 `ods.trade_order_detail` 导入 `amount` → 绑 `商品销售金额` 标准 → 出现含**新旧列名 + 来源表名**的确认：`绑定后英文编码将由「amount」改为「item_sales_amount」，与来源表 ods.trade_order_detail 的物理列「amount」不再一致；下游字段映射需同步调整`
- [x] 取消则不改写：点「返回选择」→ 确认面板消失、选择器保持打开、该行仍为 `amount` 且无提示
- [x] 确认后：该行立即变为 `商品销售金额 / item_sales_amount / DECIMAL / 18,2`，行内提示为 `已绑定标准「商品销售金额」：英文编码由 amount 改为 item_sales_amount，与来源表 ods.trade_order_detail 的列 amount 不一致，下游字段映射需同步调整`
- [x] 提示跟随行身份：把该行「上移」后提示仍在 `item_sales_amount` 行（无错位）；「解绑」后提示消失、恢复「绑定」
- [x] 无来源列的手工新增字段绑标准 → 不弹确认、直接绑定（手工金额/manual_amount → 商品销售金额，行为与今天一致）
- [x] 逆向建模链路同样命中：`ods.trade_order_detail` 生成草稿后对 `amount` 绑定同一标准，确认面板与行内提示文案一致（草稿未保存）
- [x] 单测覆盖：告警判定 5 例、来源带入 2 例、表名限定 1 例、逆向来源 2 例；`test:unit` 全绿
- [x] 票尾留下「需 FYOIP 的持久化来源列」结论（见残留），不留隐性 TODO

**门禁**
- `npx tsc --noEmit` → 0
- `npx oxlint` 12 个改动文件 `--deny-warnings` → 0 warnings / 0 errors
- `npx oxfmt --check` 12 个改动文件 → 通过（3 个文件格式化后复检通过）
- `npm run test:unit` → 117 pass / 0 fail
- `npm run architecture:check` → 16 pass
- 后端零改动：`toModelFieldInput()` 未增加字段，模型保存/发布请求体不变

**残留**
- **来源列只在会话内存在**：`source` 不提交、不持久化；且保存成功后编辑器按服务端字段重建行（`fromModelField`），来源与行内提示随本次草稿一起清空。因此不会出现陈旧错误提示，但也无法跨会话检测；跨会话的映射断裂检测需要持久化「模型字段 → 来源物理列」，属 FYOIP-16/17 的资产血缘范围，本票不偷偷加列；
- 告警只覆盖「绑定标准导致编码变化」这一条路径；手工直接改英文编码、或同步任务侧已有的列映射，本票不改写、不校验；
- 来源表名按 schema 优先、库名兜底限定；同一实例内不同库的同名表在提示中可能只显示库名，属展示精度问题，不影响判定；
- 内嵌浏览器 `visibilityState=hidden` 本次仍无法截图，证据为 DOM 原文读数与行状态读数；
- 两条链路均只操作草稿（`ads_pay_item_top_recheck` 未保存、逆向草稿未保存），未新增或删除任何持久化数据。
