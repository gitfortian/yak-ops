# 09: 字段标准改名不传播 + 绑定静默改写 4 项 + 中文名不即时重绘

**对应需求:** 第 1 轮验收 轮1 B4（P1） | 阶段: P1

**What to build:** 标准是「一次定义、处处一致」的锚点。今天改名会造成**不可修复的漂移**：
1. 先把 `商品ID` 标准绑到 `dim_item_info.item_id`，再把标准改名为 `商品ID（改名测试）`——编辑弹窗**不显示任何引用数**，保存**无二次确认**；模型字段中文名仍是旧「商品ID」；
2. 随后发布的 DDL 把 `COMMENT '商品ID'` 写进物理表，而「表已存在时只做结构校验、不会修改」→ **该漂移无法通过重发布修复**；
3. 反向问题：绑定标准这个动作会**静默改写**模型字段的 4 项（中文名/英文编码/类型/精度），弹窗文案只声明「编码、类型与长度精度」少了中文名，且表格里的中文名单元格**不即时重绘**（保存刷新后才显示）。

**对照事实（第 2 轮补测，正向）**：改名**不破坏引用**（参照完整性成立）；**删除**被引用标准会被硬拦并点名引用方（`模型字段 item_id · dim_item_info.item_id`）且删除按钮 disable。即：**引用查询能力已存在，只是改名路径没用它**。

**模块归属:** **modeling（编辑弹窗 + 绑定文案/重绘）为主**；「改名是否要回填已引用字段」属口径裁决，本票只做「**告知 + 确认 + 已发布表漂移预警**」，不做静默批量 UPDATE。

**Blocked by:** 无。

**Status:** 已解决（2026-10-30，真机复验：改名出引用数与二次确认、取消不发 PUT、绑定即时重绘；活证据 `item_id` 标准 `108862259906666496` 保留）

**根因/落点证据（修前）**
- 引用预检查能力已在仓内：`data-standards/delete-dialog.tsx:12-16` 调 `checkReferences`（`@/service/data-modeling/data-standards`）、`:69-71` 据 `result.referenced` 切 `referenced` 态、`:128-130` 文案「该记录已被引用，无法删除」+ `ReferenceItem`/`REFERENCE_KIND_LABEL` 渲染清单；
- 改名入口缺这一步：编辑用 `data-standards/standard-dialog.tsx`，修前未调用 `checkReferences`；
- 静默改写的实现（修前）：[logical-models/fields.ts](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/fields.ts) `fromStandard(standard)` 一次性覆盖 `cnName/enCode/dataType/lengthPrecision`，并把 `description` 置 `null`（另见 P2 票 22）；
- 前端与后端口径不一致：[field-editor.tsx](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/field-editor.tsx) 的 `bindStandard` 修前保留 `row.cnName.trim() || replacement.cnName`，而后端 `ModelServiceImpl.validateFields()` 保存时**无条件**用标准重写 `enCode/cnName/dataType/lengthPrecision`——前端承诺的名字下一次保存就被服务器改掉，这正是「中文名不重绘 + 改名漂移」的同一根因。

**修法**
1. 抽出绑定契约 [logical-models/standard-binding.ts](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/standard-binding.ts)：`inheritStandard()` 单点声明标准拥有的 4 项（中文名/英文编码/类型/长度精度）+ `standardId/standardEnCode`；`applyStandard()` 用该契约覆盖既有行并清掉属于旧类型的 `importNote`；`fromStandard()` 复用同一契约建新行。`field-editor.tsx:86-91` 改用 `applyStandard()`，表格行状态立即更新（React 受控行，无缓存问题）；
2. 行 key 生成移到 [logical-models/field-key.ts](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/field-key.ts)，让契约模块零运行时依赖、可被 Node 直接单测；`fields.ts` 与 `import-dialog.tsx` 改从新模块取 `newFieldKey`；
3. [logical-models/standard-picker.tsx:130-133](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/standard-picker.tsx) 逐项写明绑定后果：单条绑定「中文名、英文编码、数据类型与长度/精度会按标准覆盖；非空以标准为默认值，可再修改」，批量导入「导入的字段沿用标准的中文名、英文编码、数据类型与长度/精度」；
4. [data-standards/standard-dialog.tsx](../../../yak-ops-ui/apps/web/app/data-modeling/data-standards/standard-dialog.tsx) 编辑既有标准时调 `checkReferences("field", id)`（:153）：加载中/失败可重试/未启用/引用数四态展示在英文编码下方（:430-445），读数为「被 N 个模型字段引用（，共 M 处引用）」或「被 N 处引用」；保存拆为 `persist()` 与 `submit()`（:262/:315），名称或编码有变且（引用数未读到或 >0）时先出确认面板（:715-724），「返回修改」不发 PUT，「确认保存」才写；确认期间底部「保存」禁用（:756）；
5. 确认文案点名两条后果：「改名不会自动传播：N 个模型字段仍保留旧名称」「已发布物理表的列注释不会自动更新，需重新发布或手工变更」，并提示「已保存的模型字段需重新保存对应模型，才会同步新名称」；
6. 单测 [standard-binding.test.mjs](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/standard-binding.test.mjs) 锁死契约（已加入 `package.json` 的 `test:unit` 列表）。

**验收清单**
- [x] 编辑被引用的标准 → 出引用数：`GET /data-standards/fields/108862259906666496/references [200]`，弹窗读数「被 1 个模型字段引用」
- [x] 改名 → 出二次确认；取消 → 不发 PUT：点「保存」出确认面板，点「返回修改」后网络列表只有 references GET，**0 条 PUT**
- [x] 确认文案逐字读数：`标准「商品ID（改名测试）（item_id）」将改名为「商品ID（item_id）」` + `改名不会自动传播：1 个模型字段仍保留旧名称` + `已保存的模型字段需重新保存对应模型，才会同步新名称` + `已发布物理表的列注释不会自动更新，需重新发布或手工变更`
- [x] 确认保存 → PUT 成功、toast 保存成功；列表行更新为「商品ID」、更新时间 `2026-10-30 09:37:18`
- [x] 漂移可自救：`dim_item_info` 第 1 行改前仍显示旧名「商品ID（改名测试）」，进入编辑器点「保存」后同步为「商品ID」（保存即同步，符合确认文案预期）
- [x] 绑定即时重绘：`ads_pay_item_top_recheck`（0 字段、未发布）添加字段 → 绑定 → 选「商品ID」，行内中文名/英文编码/类型/长度**立即**读数为 `商品ID / item_id / STRING / 32`，未保存（草稿未落库）
- [x] `applyStandard` 覆盖行为有单测：4 项覆盖 + `importNote` 清空 + 保留主键/分区/聚合/引用/描述，`fromStandard` 不继承标准描述；`test:unit` 全绿
- [x] 真机：把 `商品ID（改名测试）` 改回 `商品ID` 并复验 `dim_item_info.item_id` 的显示链路（见上两条）

**门禁**
- `npx tsc --noEmit` → 0
- `npx oxlint` 8 个改动文件 `--deny-warnings` → 0 warnings / 0 errors
- `npx oxfmt --check` 8 个源码文件 → 通过；`package.json` 为仓库既有红（HEAD 版本同样失败，非本次引入）
- `npm run test:unit` → 108 pass / 0 fail
- `npm run architecture:check` → 16 pass
- 后端零改动（`ModelServiceImpl.validateFields()` 的既有改写语义不变，本票只让前端如实呈现）

**残留**
- **改名仍不自动传播**：这是本票的明确裁决——只告知 + 确认，不做静默批量 UPDATE；已保存模型需重新保存才会同步，已发布物理表注释需重新发布或手工变更；
- `dim_item_info` 2026-10-30 09:29:15 的发布失败记录（字段注释漂移）保留；票 09 复验把它重新保存为「商品ID」后漂移已解除，该失败记录不删除；
- 引用数读取失败或后端未启用引用检查时，改名仍会弹确认（文案改为「引用数未能读取，改名仍可能影响已引用方」），属于 fail-closed；
- 内嵌浏览器 `visibilityState=hidden` 本次仍无法截图，证据为 DOM 原文读数、网络请求列表与列表页读数；
- 按约定未删除任何数据：标准 `item_id`、模型 `dim_item_info` / `ads_pay_item_top_recheck` 及两轮全部验收数据保留，供人工复核。
