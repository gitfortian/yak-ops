package io.yak.ops.business.datasync.execution;

import io.yak.ops.flow.runtime.LocalExecution;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 保存当前 Yak Ops 进程内正在执行的 Data Sync LocalExecution 引用，供离线/实时实例统一取消和生命周期收口。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Component
public class OfflineSyncExecutionRegistry {

    private final ConcurrentMap<String, LocalExecution<?>> executions = new ConcurrentHashMap<>();

    public void register(String instanceId, LocalExecution<?> execution) {
        LocalExecution<?> existing = executions.putIfAbsent(instanceId, execution);
        if (existing != null) {
            throw new IllegalStateException("data sync execution already registered: " + instanceId);
        }
    }

    public boolean cancel(String instanceId) {
        LocalExecution<?> execution = executions.get(instanceId);
        if (execution == null) return false;
        execution.cancel();
        return true;
    }

    public void remove(String instanceId, LocalExecution<?> execution) {
        executions.remove(instanceId, execution);
    }
}
