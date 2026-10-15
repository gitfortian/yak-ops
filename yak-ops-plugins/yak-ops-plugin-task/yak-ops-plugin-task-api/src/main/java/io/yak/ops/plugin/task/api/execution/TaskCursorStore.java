package io.yak.ops.plugin.task.api.execution;

import java.util.List;

/** Platform-owned persistence for frozen bounded task windows, excluding connector credentials. */
public interface TaskCursorStore {
    /**
     * Returns the same frozen window for retry, creating it before any target modification.
     *
     * @param identity trusted and already admitted Instance
     * @param proposed ordered table cursor upper bounds without row payloads
     * @return durable, table-ordered windows and their actual data steps
     */
    List<TaskCursorBatch> freeze(TaskExecutionIdentity identity, List<TaskCursorBatch> proposed);
}
