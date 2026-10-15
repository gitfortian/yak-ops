package io.yak.ops.dao.repository.task;

import java.util.List;
import java.util.Optional;

/** Transaction-bound cursor reads, frozen-instance windows and compare-and-set progress updates. */
public interface TaskCursorRepository {
    /** @param workspace owning Workspace @param definition definition identity @param key stable table-key hash
     * @return locked state, if present */
    Optional<TaskCursorRow> lock(String workspace, String definition, String key);
    /** @param workspace Workspace @param definition Definition @param row new pending cursor */
    void insert(String workspace, String definition, TaskCursorRow row);
    /** @param workspace Workspace @param definition Definition @param row existing pending cursor */
    void stage(String workspace, String definition, TaskCursorRow row);
    /** @param workspace Workspace @param instance frozen Instance @param row immutable table window */
    void freeze(String workspace, String instance, TaskCursorBatchRow row);
    /** @param workspace Workspace @param instance Instance @return frozen table rows */
    List<TaskCursorBatchRow> frozen(String workspace, String instance);
    /** @param workspace Workspace @param definition Definition @param row successful candidate */
    void commit(String workspace, String definition, TaskCursorBatchRow row);
}
