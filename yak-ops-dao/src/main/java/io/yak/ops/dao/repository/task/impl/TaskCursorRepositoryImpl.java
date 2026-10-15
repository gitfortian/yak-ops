package io.yak.ops.dao.repository.task.impl;

import io.yak.ops.dao.repository.task.TaskCursorBatchRow;
import io.yak.ops.dao.repository.task.TaskCursorRepository;
import io.yak.ops.dao.repository.task.TaskCursorRow;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Narrow, transaction-bound SQL operations for incremental progress and frozen windows. */
@Repository
public class TaskCursorRepositoryImpl implements TaskCursorRepository {
    @Resource
    private JdbcTemplate jdbc;

    @Override
    public Optional<TaskCursorRow> lock(String workspace, String definition, String key) {
        List<TaskCursorRow> results = jdbc.query(
                """
                SELECT table_key,binding,cursor_value,pending_value,initialized,revision
                FROM yak_ops_task_cursor WHERE workspace_id=? AND definition_id=? AND table_key=? FOR UPDATE
                """,
                (rs, ignored) -> new TaskCursorRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getBoolean(5),
                        rs.getLong(6)),
                workspace,
                definition,
                key);
        return results.stream().findFirst();
    }

    @Override
    public void insert(String workspace, String definition, TaskCursorRow row) {
        jdbc.update(
                """
                INSERT INTO yak_ops_task_cursor
                  (workspace_id,definition_id,table_key,binding,cursor_value,pending_value,initialized,revision)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                workspace,
                definition,
                row.tableKey(),
                row.binding(),
                row.cursorValue(),
                row.pendingValue(),
                row.initialized(),
                row.revision());
    }

    @Override
    public void stage(String workspace, String definition, TaskCursorRow row) {
        if (jdbc.update("""
                UPDATE yak_ops_task_cursor SET pending_value=?
                WHERE workspace_id=? AND definition_id=? AND table_key=? AND binding=?
                    AND revision=? AND pending_value IS NULL
                """, row.pendingValue(), workspace, definition, row.tableKey(), row.binding(), row.revision())
                != 1) throw new IllegalStateException("Cursor reservation lost");
    }

    @Override
    public void freeze(String workspace, String instance, TaskCursorBatchRow row) {
        jdbc.update(
                """
                INSERT INTO yak_ops_task_cursor_batch
                  (workspace_id,instance_id,table_key,binding,mode,column_name,initial_value,lower_value,
                   upper_value,allow_initial_load,initial_load,expected_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                workspace,
                instance,
                row.tableKey(),
                row.binding(),
                row.mode(),
                row.column(),
                row.initialValue(),
                row.lower(),
                row.upper(),
                row.allowInitialLoad(),
                row.initialLoad(),
                row.expectedVersion());
    }

    @Override
    public List<TaskCursorBatchRow> frozen(String workspace, String instance) {
        return jdbc.query(
                """
                SELECT table_key,binding,mode,column_name,initial_value,lower_value,upper_value,
                       allow_initial_load,initial_load,expected_version
                FROM yak_ops_task_cursor_batch WHERE workspace_id=? AND instance_id=? ORDER BY table_key
                """,
                (rs, ignored) -> new TaskCursorBatchRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7),
                        rs.getBoolean(8),
                        rs.getBoolean(9),
                        rs.getLong(10)),
                workspace,
                instance);
    }

    @Override
    public void commit(String workspace, String definition, TaskCursorBatchRow row) {
        if (jdbc.update(
                        """
                UPDATE yak_ops_task_cursor
                SET cursor_value=?,pending_value=NULL,initialized=1,revision=revision+1
                WHERE workspace_id=? AND definition_id=? AND table_key=? AND binding=?
                   AND revision=? AND pending_value=?
                """,
                        row.upper(),
                        workspace,
                        definition,
                        row.tableKey(),
                        row.binding(),
                        row.expectedVersion(),
                        row.upper())
                != 1) throw new IllegalStateException("Stale or missing incremental cursor candidate");
    }
}
