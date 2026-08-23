# Datasource Change

> 开发前读 `REQUIREMENTS.md + DOMAIN.md`；涉及插件时同时读 `yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN.md`；Review 按 `REVIEW.md`。

## Summary

<!-- 这次改什么？是否属于 REQUIREMENTS.md 已有能力？ -->

## Domain Impact Analysis

```text
Aggregate(s): DataSourceDefinition / SqlExecutionAggregate / adjacent / none
Subdomain(s): Catalog / Plugin / SQL Execution / none
Invariant/lifecycle impact:
Layer: Domain / Application / Gateway / Adapter / Infrastructure / Interface
Domain Gap: no
```

## Plugin Contract Impact

```text
Plugin API changed: no
Descriptor / Capability changed: no
Catalog SPI changed: no
apiVersion / migration required: no
```

## Compatibility

<!-- 是否影响 REST / yak_ops_data_source / Flyway / Plugin SPI / 内置或第三方插件 / yak-ops-core SQL contract？ -->

## Tests / Safety

<!-- 跑了什么；涉及连接状态、Secret、Catalog、Plugin Contract、SQL lifecycle 时说明对应 guardrail。 -->

## Domain Compliance Report

```text
Rule changed/implemented:
Safety/tests:
Known gaps:
```
