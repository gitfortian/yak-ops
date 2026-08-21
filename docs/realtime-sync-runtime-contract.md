# Realtime sync Runtime contract v1

Yak Ops submits a password-free Flink CDC Pipeline plus submission-scoped credentials to a fixed
Runtime. The Runtime must never log or persist the `credentials` object.

## Capabilities

`GET /capabilities` must include:

```json
{
  "protocolVersion": "1",
  "runtimeVersion": "1.0.0",
  "dynamicCredentialBinding": true,
  "deliverySemantics": "at-least-once",
  "connectors": {
    "sources": ["mysql"],
    "sinks": ["yak-jdbc:mysql", "yak-jdbc:postgres"],
    "schemaEvolution": ["evolve", "ignore", "exception"]
  }
}
```

Yak Ops enables deployment only when `protocolVersion` is `1` and
`dynamicCredentialBinding` is `true`.

## Deployment

`POST /deploy` uses `Content-Type: application/json` and an `Idempotency-Key` header:

```json
{
  "pipelineYaml": "source:\n  password: ${SECRET:source.password}\n...",
  "credentials": {
    "source": { "username": "cdc_reader", "password": "..." },
    "sink": { "username": "cdc_writer", "password": "..." }
  }
}
```

Before submitting to Flink CDC, the Runtime replaces only the exact placeholders
`${SECRET:source.password}` and `${SECRET:sink.password}`. Credentials are request-scoped, must be
cleared after submission, and must not appear in status, logs, error messages, or persisted job
metadata.

Successful deployment returns HTTP `202`:

```json
{
  "jobId": "runtime-job-id",
  "deliverySemantics": "at-least-once"
}
```

The Runtime should treat `Idempotency-Key` as a server-side idempotency key. HTTP `409` means a
different active job already occupies the fixed Runtime; HTTP `422` means the Pipeline was rejected.

The existing `/validate`, `/status`, `/stop`, and `/logs` endpoints retain their current contracts.


## Reliable control-plane behavior

Yak Ops publishes committed task changes over `GET /api/v1/realtime-sync/stream` using SSE. Clients
fall back to bounded polling while the stream reconnects. A database lease ensures that only one Yak
Ops instance reconciles the fixed Runtime at a time, and transient Runtime status failures must reach
the configured threshold before running tasks become `UNKNOWN`.

Operators can request a non-deploying state check with `POST /api/v1/realtime-sync/{id}/reconcile`.
This operation only compares the latest local deployment with `/status`; it never resubmits a lost
CDC job automatically.
