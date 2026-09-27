package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.checkpoint.CheckpointState;

/**
 * Runtime 测试使用的可断言检查点状态。
 *
 * @param owner 状态归属
 * @param position 当前读取位置
 * @author weifuwan
 * @since 2026-09-27
 */
record TestCheckpointState(String owner, long position) implements CheckpointState {}
