package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;

/**
 * JDBC 单表 Enumerator 的分片分配状态。
 *
 * @param assigned 当前唯一表 split 是否已经分配
 * @author weifuwan
 * @since 2026-09-27
 */
record JdbcEnumeratorState(boolean assigned) implements CheckpointState {}
