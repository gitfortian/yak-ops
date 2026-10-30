# 05: STRING 长度——保存放行、发布才拦、且不告诉你是哪一列

**对应需求:** 第 2 轮验收 轮2 B5（P1） | 阶段: P1

**What to build:** 「STRING 必填长度」这条规则要**在设计期（保存）就拦住并指到具体行**，而不是等发布生成脚本时才报错、还只给一句没有主语的话。实测：给 ADS 加一列 STRING 且长度留空 → 保存 **200 +「保存成功」**；进发布弹窗才见「STRING 字段必须填写长度」——**不带字段名、不带行号**（同模型另一列 STRING 有长度 64，用户得自己逐列对比）。此时发布按钮正确 disable，但模型已以「不可发布」状态落库。

**模块归属:** **modeling（前端保存校验）+ business-modeling（后端报错信息带上下文）**。

**Blocked by:** 无。

**Status:** 已解决（2026-10-29 落码，前后端改动 + 单测绿；2026-10-30 后端重启后**发布侧新文案已复验通过**）。

**根因证据（本仓已复核）**
- 前端保存校验缺规则：[logical-models/fields.ts:157-192](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/fields.ts) `validateFieldRows` 校验了中文名必填/≤128、`FIELD_CODE`（仅 `!row.standardId` 时）、类型必填、英文编码重复、分区必须 STRING 且单列——**没有 STRING→长度必填**。调用面：`editor.tsx:397/581`、`reverse/index.tsx:149`。
- 批量录入侧**已有同类规则**可对齐：`fields.ts:323-324`「第 N 行：长度/精度只适用于 STRING 或 DECIMAL」。
- 后端抛错点：[PhysicalTypes.java:81](../../../yak-ops-business/yak-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/publication/PhysicalTypes.java) `parsePositive(lengthPrecision, "STRING 字段必须填写长度")`；
- 上下文丢失：`PhysicalDdlGenerator.java:40-44` 逐列循环但**不附 `column.name()`**；`ModelPublicationServiceImpl.java:260-262` 直接 `new BusinessException(failure.getMessage())` 透传 → 消息里既无字段名也无行号。
- 活证据（**保留**）：`ads_item_sales_top` `109111423840215040` 里的 `extra_check_col`，发布历史 1 成 1 败。

**修法（已实施，两处 + 一处必要的第三处）**
1. 前端：`validateFieldRows` 增 `dataType === "STRING" && !lengthPrecision.trim()` → `{index, field:"lengthPrecision", message:"STRING 字段必须填写长度"}`（文案**照抄后端原句**，不写成「长度/精度」，避免出现第二套口径）；`FieldIssueField`（`fields.ts:39`）扩一员；`field-editor.tsx` 长度/精度单元格加 `aria-invalid` + 行内 `<p>`。
2. 后端：`PhysicalDdlGenerator` 抽出 `typeOf(dialect, column)` 私有方法，`catch (IllegalArgumentException failure)` 后重抛 `"字段 " + column.name() + "：" + failure.getMessage()` 并**保留 cause**（原异常类型不变，`AGENTS.md`：不解析异常文案做决策、不丢 cause）。
3. 追加的必要一处（原票只列两处，实测不够）：编辑器原本 `issues={attempted ? issues : []}`，**存量非法模型打开时一行红都不显示**，要点一次「保存」才出——即「不只在发布弹窗」这条不成立。改为**载入服务端字段后立即** `setAttempted(validateFieldRows(loaded).length > 0)`（`editor.tsx:296-301`）：来自库的列有违规就直接标红，用户自己新敲的行仍等首次保存才报，不产生「还没写就红一片」。

**验收清单**
- [x] 新增 STRING 列留空长度 → 保存被**前端拦住**、行内出现文案、无网络请求
  - `ads_item_sales_top` 加第 3 行（中文名/编码合法、长度留空）点保存：汇总条「第 3 行：STRING 字段必须填写长度」+ 该行长度单元格内联同句 + `aria-invalid="true"`；URL 未变、无 toast；`list_network_requests` 该会话 14 条**全为 GET，无 PUT** → 真·零往返。
- [x] 存量非法模型打开编辑器即逐行显示该错误（不只在发布弹窗）
  - 因原票缺第 3 处修法，先用直调 API 建立**新的**活证据 `ads_ticket05_no_length` `109134965185110016`（单列 `ticket05_col` STRING、`lengthPrecision: null`，`POST` 返回 **200「成功」**——再次确认「保存放行」是后端事实，不是前端漏检）。**直接进该 URL 不做任何操作**即见「第 1 行：STRING 字段必须填写长度」+ 行内文案 + `aria-invalid="true"`。
  - 注：原票点的 `ads_item_sales_top.extra_check_col` 已被在途会话补成 `32`，本机已不再是非法样本，故另立上面的新证据行（**均保留未清理**）。
- [x] 绕过前端直调 API 保存同样非法字段时，后端错误信息**含字段名**（2026-10-30 后端重启后复验）
  - 代码与单测到位：`PhysicalPublishingTest.namesTheColumnWhoseTypeCannotBePublished` 断言消息含 `item_name` 与原句、且 `getCause() instanceof IllegalArgumentException`。
  - 复验读数：对 `ads_ticket05_no_length`（`109134965185110016`）打开发布弹窗，目标库选 `ads`、点「生成建表脚本」→ 弹窗内红色 Alert 精确为「**字段 ticket05_col：STRING 字段必须填写长度**」；确认「发布」按钮保持 disabled，未产生发布记录。旧的「无列名」文案不再出现。
  - **未新增 IT**：`MySqlDatabase`（Testcontainers `mysql:8.0.39`）需要 Docker，本机 `docker`/`podman` 均不存在，加了也不会执行；发布消息已由纯单测覆盖，不再补 IT 制造假绿。
- [x] 发布弹窗错误与编辑器行内错误文案一致，不出现第二套口径 — 两侧同句「STRING 字段必须填写长度」，后端只在前面加「字段 \<列\>：」定位。
- [x] 已发布成功路径零回归（重开重存不破）
  - `dim_item_info` `108866872718319616` → `PUT … [200]`；`dws_item_pay` `109105431115128832` → `PUT … [200]`；两者打开时 0 条红。
  - 逐模型扫全部 7 个模型的持久化字段跑同一套规则：只有故意建非法的那个报问题，`dim_item_info`/`dwd_item_sales_detail`/`dws_item_pay`/`ads_item_sales_top`/`dwd_trade_orders`/`ads_pay_item_top_recheck` **全部 0 问题** → 新的「打开即标红」不会误伤。
- [x] 后端单测 + 前端检查（**实际执行过的**）
  - `bash mvnw -o -pl yak-ops-business/yak-ops-business-modeling -am -Dtest='PhysicalPublishingTest' -Dsurefire.failIfNoSpecifiedTests=false test` → `BUILD SUCCESS`；surefire `TEST-…PhysicalPublishingTest.xml` = `tests="13" errors="0" skipped="0" failures="0"`，含新用例名。
  - `npx tsc --noEmit` → exit 0；`npx oxlint … --deny-warnings` → 0 warnings/0 errors（25 files）；`npx oxfmt --check` → 全部合规（该工具要求联合类型写回单行，已由其就地改正）。
  - **未执行**：本票未碰 `ModelDatabaseIT`；`-Dtest='Publication*'` 整批未跑（只跑了受影响的 `PhysicalPublishingTest`）。
