package io.yak.ops.dao.repository.task;

/** Persistence-only snapshot; never carries source credentials or row payloads. */
public record TaskCursorRow(
        String tableKey, String binding, String cursorValue, String pendingValue, boolean initialized, long revision) {}
