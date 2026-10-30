# 11: 发布回报文案「已采集元数据」与实际登记语义不符

**对应需求:** 第 1 轮验收 轮1 B2 的**文案半边**（P1）；登记链路半边见 [docs/data-map/issues/01](../../data-map/issues/01-publish-does-not-register-asset.md) | 阶段: P1

**What to build:** 物理发布成功后，回报**不能说「已采集元数据」**。第 1 轮两次发布（`dim.dim_item_info`、`dwd.dwd_item_sales_detail`）页面都打印「已采集元数据：1 张表 / 9 个字段」，但资产目录持续是 `共 0 个资产 / 还没有采集任何资产`，必须**手工**发起采集任务并「立即运行」才出现资产。产品把「物理发布」和「元数据登记」做成了两套语义，**前者的文案冒充后者**——这是会让人以为闭环已通的误导，比缺功能更危险。

**模块归属:** **modeling（发布弹窗文案，本票）** + **data-map（发布即登记，另票，受 FYOIP-18 约束）**。

**Blocked by:** 无。本票**只改文案**，不动采集行为——「发布即登记」是 FYOIP-18 的裁决内容，不在这里顺手实现。

**Status:** 已解决（2026-10-30，真机复验：成功发布读数无「已采集」，指引可跳采集任务入口）

**证据**
- 文案出处（修前）：[logical-models/publish-dialog.tsx:420](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/publish-dialog.tsx) `已采集元数据：{snapshot.tableCount} 张表 / {snapshot.fieldCount} 个字段。`；历史行另有「重新采集」按钮与 `元数据已重新采集` toast，同属把发布快照说成采集；
- 反证：资产目录当时为 0 条，需手工建采集任务 `e2e_dwd_item_sales_detail_crawl` 才登记出资产 `dwd_item_sales_detail`（`108869748396056576`）；
- 语义核实（后端只读）：`MetadataCollector.collect()` 回读**本张物理表的列结构**写入 `metadata_collection/table/field`，属「发布结构快照」，不写资产目录；资产只在采集任务运行时登记；
- 治理口径：`docs/yoips/FYOIP-18-model-published-asset-registration.md`（发布即登记 + 范围治理）——文案必须先与现状一致，语义再随 FYOIP-18 落地升级。

**修法**
1. 发布成功结果块改为陈述实际发生的事：「已回读物理表结构：N 张表 / M 个字段。」（[publish-dialog.tsx:444](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/publish-dialog.tsx)）；
2. 紧跟一行现状指引：「发布不会登记资产目录；需在「元数据采集」运行采集任务后，资产才会出现在资产目录。」＋「去元数据采集」跳转控件（`useNavigate` → `/data-governance/data-map/tasks`，即导航栏「元数据采集」入口）；只在该条发布 `SUCCEEDED` 时显示，失败/未知不多说；
3. 发布历史同语义纠正：「重新采集」→「重读表结构」，toast `元数据已重新采集` → `已重读物理表结构`（[publication-history.tsx:92,170](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/publication-history.tsx)）；接口与采集行为零改动；
4. 不改采集逻辑、不加自动采集——不制造新的假闭环。FYOIP-18 落地时，此文案随之升级为「已登记为资产」，并在票尾回写。

**验收清单**
- [x] 发布成功（`dwd_item_sales_detail` → `dwd.dwd_item_sales_detail`，2026-10-30 09:29:40）→ 结果块原文：`发布成功 · dwd.dwd_item_sales_detail · 表已创建或已与模型一致 · 已回读物理表结构：1 张表 / 9 个字段。 · 发布不会登记资产目录；需在「元数据采集」运行采集任务后，资产才会出现在资产目录。 · 去元数据采集`；不再出现「已采集」
- [x] 指引可跳转：点击「去元数据采集」→ `location.pathname = /data-governance/data-map/tasks`，页头 `H1 = 元数据采集`
- [x] 失败路径不误报：`dim_item_info` → `dim` 当日结构校验失败（字段注释漂移），结果块只有错误原文，无「已采集」也无指引（指引仅 `SUCCEEDED` 显示）
- [x] 全文 `grep 已采集元数据` 在 `apps/web/app/data-modeling` 为 **0 命中**；`grep 采集` 仅剩新指引与历史「重读表结构」
- [x] 资产侧行为未变：`data-map` 无任何改动，采集任务入口/调度/登记链路原样
- [x] 与 [data-map/01](../../data-map/issues/01-publish-does-not-register-asset.md) 交叉引用：本票只保证「不撒谎」，登记闭环仍由 data-map/01 + FYOIP-18 负责

**门禁**
- `npx tsc --noEmit` → 0
- `npx oxlint` 两文件 `--deny-warnings` → 0 warnings / 0 errors
- `npx oxfmt --check` 两文件 → 通过
- `npm run test:unit` → 104 pass / 0 fail

**残留**
- 后端接口名 `refreshPublicationMetadata` / `/metadata/refresh` 保持原样（内部契约，非用户可见），仅前端文案改为「重读表结构」；
- 验收目标从 `ads_item_sales_top` 改为 `dwd_item_sales_detail`：前者当前列表态为「发布失败」（在途会话改过其字段），故取同为成功发布的 DWD 模型复验成功文案，失败路径另用 `dim_item_info` 复验；
- 内嵌浏览器 `visibilityState=hidden` 本次无法截图，证据为 DOM 原文读数与导航 URL 读数；
- 复验产生的三条 2026-10-30 发布记录（`dwd_item_sales_detail` 成功 09:29:40 与 09:30:08 两条；`dim_item_info` 失败 09:29:15 一条）按「不清理」约定保留，可在发布历史中复核。
