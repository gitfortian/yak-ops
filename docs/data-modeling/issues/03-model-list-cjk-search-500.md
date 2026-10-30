# 03: 模型列表按中文关键词搜索直接 HTTP 500

**对应需求:** 第 2 轮验收 轮2 B2（P1） | 阶段: P1

**What to build:** 数仓建模列表要能**按中文找表**——业务同事的检索入口 90% 是表中文名。今天 `GET /api/v1/modeling/dimension-modeling/models?keyword=商品` → **HTTP 500 `{"code":999,"message":"失败"}`**；`keyword=item`/`Top` 则 200 且能命中中文名（英文编码列匹配成功、中文名列抛异常整条 SQL 失败）。

**模块归属:** **dao（ModelMapper）+ modeling**。前端表现是正确的（出「检索失败 + 重试」而不是伪装空态），**本票是纯后端修复**。

**Blocked by:** 无。

**Status:** 已解决（2026-10-29 落码；2026-10-30 后端重启后 REST + 真实 UI 双复验通过）。唯一未执行项仍是 `ModelDatabaseIT`——本机无 Docker/Testcontainers，需在 IntelliJ 或装有 Docker 的环境跑一次（命令见验收清单）。

**根因证据（本仓已复核）**
- [ModelMapper.java:43-44](../../../yak-ops-dao/src/main/java/io/yak/ops/dao/mapper/modeling/ModelMapper.java)：`FILTER` 里 `m.name LIKE #{keyword} ESCAPE '!'` / `m.cn_name LIKE …` 未做字符集转换。
- 列定义：[V2__v1_4_0.sql:278](../../../yak-ops-dao/src/main/resources/db/migration/yak-ops/V2__v1_4_0.sql) `name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL`。
- 日志实证：`logs/yak-ops.log:144` `SQLException: Illegal mix of collations (ascii_bin,IMPLICIT) … for operation 'like'`。
- 同仓已有正确写法（照抄，不要另创）：`mapper/modeling/metric/MetricCompositeMapper.java:20`、`metric/MetricDerivedMapper.java:30-31`、`standard/StandardMapper.java:114-115`（:104 有说明注释）、`standard/StandardLookupMapper.java:20-22`——
  `CONVERT(<col> USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{…} ESCAPE '!'`。
- 波及面已审：全站 `ESCAPE '!'` 站点中**只有这一处漏包**，所以坏的只有模型列表搜索（数仓/标准/指标/单位/词典中文搜索均 200）。

**修法（已实施）**
`ModelMapper.FILTER` 的 `keyword` 分支：`m.name` 侧包 `CONVERT(m.name USING utf8mb4) COLLATE utf8mb4_unicode_ci`，`m.cn_name` 保持原生 LIKE（该列本就是表默认 utf8mb4，同仓 peer 写法同样只转 ascii 列）；照抄 `StandardMapper.java:104` 的行上注释口径。投影、分页、Workspace 条件一字未动。IT 侧新增 `ModelDatabaseIT.searchMatchesCjkDisplayNameAndKeepsWildcardsLiteral()`，`createDetailModel` 加一个带 `cnName` 的重载（原三参重载委派给它，默认文案不变）。

**验收清单**
- [x] 真实浏览器/REST（2026-10-30，后端重启后）：`keyword=商品` → **200**、`total=5`，命中 `dim_item_info`（商品信息维度表）、`dws_item_pay`（商品支付日汇总）、`ads_item_sales_top`（商品销售Top榜），另有 `ads_pay_item_top_recheck`、`dwd_item_sales_detail`；`keyword=复核` → **200**、1 行 `ads_pay_item_top_recheck`；列表搜索只覆盖 `m.name`/`m.cn_name`，当前无模型名含「销售额」→ **200 + 0 行**（票面原「销售额命中」前提不成立，非回归）。含空关键词、`订单_`、`订单%`、不存在词在内的 7 条查询全部 200，无 500。
- [x] 通配符注入仍按字面匹配：IT 断言 `订单_`→1、`订单%`→1、`order_`→3（未转义则分别膨胀到 2/3/4）；转义实现在 `ModelServiceImpl.java:540`（`!`→`!!`、`%`→`!%`、`_`→`!_`）+ `ESCAPE '!'`。REST 实测 `订单_`/`订单%` 均 200 且 0 行（当前数据无字面匹配），未出现未转义膨胀。
- [ ] 后端 IT：`ModelDatabaseIT`（已加第 15 条用例）—— **仍未执行**：本机 `docker`/`podman` 均不存在，不制造假绿；请在 IntelliJ 运行该类，或 `bash mvnw -pl yak-ops-business/yak-ops-business-modeling test -Dtest=ModelDatabaseIT`
- [x] IT 注册：新用例写在**已注册**的 `ModelDatabaseIT` 内，未新建 `*IT` 类，`scripts/tests/backend_tests.py:39` 的 `{"ModelDatabaseIT":12}` 是最少用例数，无需改 upstream `scripts/`
- [x] 编译与格式：`bash mvnw -o -pl yak-ops-dao,yak-ops-business/yak-ops-business-modeling -am -DskipTests test-compile` exit 0；spotless `:apply` + `:check`（仅这两个模块，作用域限定避免动到在途文件）exit 0；`git status` 仅这两个文件变更
- [x] 中文关键词出结果后，清空恢复、翻页与树钉选叠加不破（真实 UI，2026-10-30）：`商品` → 「检索结果 5 个」；`复核` → 1 个；点「清空」→ 恢复「全部逻辑模型 8 个」，关键词清空、分页尺寸复位为 20；搜索态把每页 20 改 50 → 仍 5 行；树钉「商品支付 4 表」→ 4 行，关键词 `商品` 就地收窄到 2 行（`dws_item_pay`、`dwd_item_sales_detail`），清空 → 回到节点 4 行，再点「全部逻辑模型」→ 8 行。**翻页本身不可达**：当前 8 个模型、分页选项 20/50/100，不存在第 2 页，未伪造读数。
