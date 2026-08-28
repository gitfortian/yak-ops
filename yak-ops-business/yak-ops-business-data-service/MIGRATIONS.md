# Data Service Migration Contract

Data Service 和 Datasource 使用完全独立的 Flyway namespace。当前仍处于产品第一期，数据库结构尚未形成正式发布兼容承诺，因此开发期增量 migration 已收敛为各模块自己的一个 V1 baseline。

## 1. Namespace ownership

```text
Datasource
  location: classpath:db/migration/yak-datasource
  history:  yak_datasource_schema_history

Data Service
  location: classpath:db/migration/yak-data-service
  history:  yak_data_service_schema_history
```

固定规则：

- Datasource migration 只能存在于 `yak-ops-business-datasource/src/main/resources/db/migration/yak-datasource`；
- Data Service migration 只能存在于 `yak-ops-business-data-service/src/main/resources/db/migration/yak-data-service`；
- Data Service 模块禁止再创建 `db/migration/yak-datasource`；
- 两个模块拥有独立 version sequence，不共享版本号和 history table。

## 2. First-release baselines

当前 baseline：

```text
yak-ops-business-datasource
└── db/migration/yak-datasource
    └── V1__baseline_datasource.sql

yak-ops-business-data-service
└── db/migration/yak-data-service
    └── V1__baseline_data_service.sql
```

`V1__baseline_data_service.sql` 直接创建 Data Service 当前最终结构，包括：

- Data Service API definition / source revision / Project ownership；
- API Key / auth mode / rate-limit policy；
- pagination / local cache / circuit policy / runtime generation；
- API documentation；
- invocation audit / Project-scoped indexes；
- cluster rate-limit minute window；
- hourly invocation rollup。

不再保留开发期 `V3/V4/V5/V6/V7/V9/V10/V11/V12/V13` 链路。

## 3. Flyway ordering

`DataServiceFlywayConfiguration` 必须：

```text
@DependsOn("opsDataSourceFlyway")
```

启动顺序：

```text
Datasource V1
    ↓
Data Service V1
```

这是初始化顺序，不代表两个模块共享 schema history。

## 4. One-time development database reset

本次 baseline squash 明确以“第一期开发数据可丢弃”为前提。不要尝试保留旧 checksum/history。

合并本次调整后，本地旧开发库建议一次性执行：

```sql
DROP TABLE IF EXISTS yak_ops_data_service_call_log_hourly;
DROP TABLE IF EXISTS yak_ops_data_service_rate_window;
DROP TABLE IF EXISTS yak_ops_data_service_documentation;
DROP TABLE IF EXISTS yak_ops_data_service_api_key;
DROP TABLE IF EXISTS yak_ops_data_service_call_log;
DROP TABLE IF EXISTS yak_ops_data_service_api;

DROP TABLE IF EXISTS yak_ops_sql_statement_execution;
DROP TABLE IF EXISTS yak_ops_sql_execution;
DROP TABLE IF EXISTS yak_ops_data_source;

DROP TABLE IF EXISTS yak_ds_schema_history;
DROP TABLE IF EXISTS yak_datasource_schema_history;
DROP TABLE IF EXISTS yak_data_service_schema_history;
```

然后重新启动 Yak Ops，让两个 V1 baseline 从零创建当前结构。

> 该 reset 只适用于当前未正式发布的一期开发数据库。后续一旦进入正式版本，不再允许通过 squash/reset 处理已发布 migration。

## 5. Rules after first release

正式发布 baseline 后：

1. Datasource 后续使用 `yak-datasource/V2`, `V3`, ...；
2. Data Service 后续使用 `yak-data-service/V2`, `V3`, ...；
3. 已发布 migration 不改名、不改内容、不改 checksum；
4. 不允许再次跨模块共享 migration location/history table；
5. schema ownership 变化必须先更新本文件和对应 Flyway contract test。
