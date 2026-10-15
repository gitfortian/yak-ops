package io.yak.ops.business.task.instance;

import io.yak.ops.dao.repository.task.TaskCursorBatchRow;
import io.yak.ops.dao.repository.task.TaskCursorRepository;
import io.yak.ops.dao.repository.task.TaskCursorRow;
import io.yak.ops.plugin.task.api.execution.TaskCursorBatch;
import io.yak.ops.plugin.task.api.execution.TaskCursorStore;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Freezes incremental cursors with optimistic ownership and preserves failed batch upper bounds. */
@Service
public class TaskCursorStoreService implements TaskCursorStore {
    @Resource
    private TaskCursorRepository repository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TaskCursorBatch> freeze(TaskExecutionIdentity identity, List<TaskCursorBatch> proposed) {
        if (proposed.isEmpty() || proposed.size() > 100)
            throw new IllegalArgumentException("Invalid incremental table count");
        List<TaskCursorBatchRow> existing = repository.frozen(identity.workspaceId(), identity.instanceId());
        if (!existing.isEmpty()) {
            if (existing.size() != proposed.size())
                throw new IllegalStateException("Previously frozen table count changed");
            return existing.stream().map(TaskCursorStoreService::fromRow).toList();
        }
        var keys = new HashSet<String>();
        var result = new ArrayList<TaskCursorBatch>();
        for (var proposal : proposed) {
            if (!keys.add(proposal.tableKey())) throw new IllegalArgumentException("Duplicate incremental table");
            TaskCursorRow state = repository
                    .lock(identity.workspaceId(), identity.definitionId(), proposal.tableKey())
                    .orElse(null);
            if (state != null && !state.binding().equals(proposal.binding()))
                throw new IllegalStateException("Incremental definition changed; reinitialization required");
            boolean initialized = state != null && state.initialized();
            String lower = state == null ? proposal.initialValue() : state.cursorValue();
            if (!initialized && !proposal.allowInitialLoad() && (lower == null || lower.isBlank()))
                throw new IllegalArgumentException("An incremental-only task requires an initial cursor");
            String upper = state != null && state.pendingValue() != null ? state.pendingValue() : proposal.upper();
            if (state == null)
                repository.insert(
                        identity.workspaceId(),
                        identity.definitionId(),
                        new TaskCursorRow(proposal.tableKey(), proposal.binding(), null, upper, false, 0));
            else if (state.pendingValue() == null)
                repository.stage(
                        identity.workspaceId(),
                        identity.definitionId(),
                        new TaskCursorRow(
                                state.tableKey(),
                                state.binding(),
                                state.cursorValue(),
                                upper,
                                initialized,
                                state.revision()));
            TaskCursorBatch frozen = new TaskCursorBatch(
                    proposal.tableKey(),
                    proposal.binding(),
                    proposal.mode(),
                    proposal.column(),
                    proposal.initialValue(),
                    lower,
                    upper,
                    proposal.allowInitialLoad(),
                    proposal.allowInitialLoad() && !initialized,
                    state == null ? 0 : state.revision());
            repository.freeze(identity.workspaceId(), identity.instanceId(), toRow(frozen));
            result.add(frozen);
        }
        if (result.stream().map(TaskCursorBatch::initialLoad).distinct().count() > 1)
            throw new IllegalStateException("Mixed initialized and uninitialized routes require separate tasks");
        return List.copyOf(result);
    }

    private static TaskCursorBatch fromRow(TaskCursorBatchRow r) {
        return new TaskCursorBatch(
                r.tableKey(),
                r.binding(),
                r.mode(),
                r.column(),
                r.initialValue(),
                r.lower(),
                r.upper(),
                r.allowInitialLoad(),
                r.initialLoad(),
                r.expectedVersion());
    }

    private static TaskCursorBatchRow toRow(TaskCursorBatch r) {
        return new TaskCursorBatchRow(
                r.tableKey(),
                r.binding(),
                r.mode(),
                r.column(),
                r.initialValue(),
                r.lower(),
                r.upper(),
                r.allowInitialLoad(),
                r.initialLoad(),
                r.expectedVersion());
    }
}
