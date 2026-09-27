package io.yak.ops.flow.runtime;

/**
 * 从控制线程提交给 Source 工作线程的一次检查点请求。
 *
 * @param checkpointId 检查点标识
 * @author weifuwan
 * @since 2026-09-27
 */
record CheckpointRequest(long checkpointId) {

    CheckpointRequest {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("checkpointId must be greater than 0");
        }
    }
}
