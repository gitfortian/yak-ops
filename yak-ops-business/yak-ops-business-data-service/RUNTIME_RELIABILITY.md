# Data Service Stage 1: Runtime Reliability Plane

Stage 1 不引入 Project/RBAC，也不把当前 JVM-local Runtime 强行改造成分布式系统。目标是先保证：**业务调用结果、审计证据、缓存代际和详情查询互不拖垮。**

## 1. Invocation result 与 Audit evidence 解耦

```text
DataServiceInvoker
   |
   +-- authorize / compile / query / runtime protection
   |        |
   |        v
   |   business result / original failure
   |
   +-- best-effort invocation audit
```

固定规则：

- SQL 查询已经成功时，调用日志 INSERT 失败不能把 HTTP 调用改成失败；
- SQL / 401 / 429 / 503 等原始异常已经产生时，审计失败不能替换原始异常；
- Recorder 仍保持同步 evidence 写入，以保留当前日志时序和落库语义，但 Invoker 对 Recorder 故障做最终隔离；
- 审计失败仅写内部 warning，不记录 raw API key，也不把请求参数重新输出到应用日志。

这一步解决的是 **failure isolation**，不是异步日志吞吐。后续如果需要高吞吐审计队列，应单独设计 bounded queue / durable outbox，不在本阶段偷换调用日志语义。

## 2. Version-safe local cache identity

当前 Cache 仍是 Caffeine/JVM-local，但 Cache Key 不能只包含 SQL 和 bindings：

```text
cache key
  = persisted runtime namespace
  + compiled SQL
  + bindings
  + pagination identity
```

persisted runtime namespace 至少覆盖：

- stable Data Service ID；
- sourceType/sourceRef；
- sourceRevisionId/sourceRevisionNo；
- persisted definition update generation。

因此：

```text
Revision R1 on Pod B
   !=
Revision R2 on Pod A/Pod B
```

即使 Pod B 没收到 Pod A 的本地 `invalidate()`，当它下一次从 DB 读取到新 generation 后也不会命中旧 generation 的缓存结果。

这不是分布式缓存；它只是让当前 local cache 在多实例部署时具备更安全的版本隔离。

## 3. Service-scoped invocation log read

详情页禁止继续使用：

```text
GET all recent logs
  -> browser filter(apiId)
```

Stage 1 增加：

```text
GET /api/v1/data-service/{id}/logs?limit=50
```

Repository 使用：

```text
WHERE api_id = ?
ORDER BY create_time DESC, id DESC
LIMIT ?
```

并增加索引：

```text
(api_id, create_time, id)
```

详情页同时改用 `GET /api/v1/data-service/{id}`，不再先加载所有 Data Service 再 `find(id)`。

## 4. Stage 1 tests

关键行为测试必须覆盖：

- audit repository/recorder failure 不影响成功 invocation；
- audit failure 不覆盖原始 datasource failure；
- audit failure 不覆盖原始 authorization failure；
- persisted runtime generation 变化会产生不同 cache namespace/key；
- 原有 local cache reuse / circuit breaker 行为保持不变。

## 5. Explicit non-goals

本阶段不做：

- Data Service Project/RBAC；
- Redis/global cache；
- distributed rate limit；
- cluster metrics aggregation；
- cross-node invalidation bus；
- invocation log retention / rollup；
- sensitive parameter masking；
- async/durable audit pipeline。

这些分别进入后续 Governance / Cluster Runtime 阶段。
