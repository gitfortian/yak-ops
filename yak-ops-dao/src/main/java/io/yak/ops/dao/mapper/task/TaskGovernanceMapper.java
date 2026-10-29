package io.yak.ops.dao.mapper.task;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Read-only Task projections the governance rules need.
 *
 * <p>Both statements read the current published definition version and {@code SCHEDULE} instances
 * only: manual, workflow and debug runs never accuse a definition, and a {@code CANCELED} instance
 * breaks a failure streak instead of counting as one.
 */
@Mapper
public interface TaskGovernanceMapper {

    @Select("""
            WITH ranked AS (
                SELECT i.definition_id, i.status,
                       ROW_NUMBER() OVER (
                           PARTITION BY i.definition_id ORDER BY i.scheduled_time DESC, i.id DESC
                       ) AS rn
                FROM yak_ops_task_instance i
                JOIN yak_ops_task_definition d ON d.id = i.definition_id
                WHERE i.workspace_id = #{workspaceId}
                  AND i.trigger_type = 'SCHEDULE'
                  AND i.definition_version = d.current_version
                  AND i.status IN ('FINISHED', 'FAILED', 'CANCELED')
                  AND i.scheduled_time >= #{since}
            ), streak AS (
                SELECT definition_id,
                       COALESCE(MIN(CASE WHEN status <> 'FAILED' THEN rn END) - 1, MAX(rn)) AS failures,
                       MAX(rn) AS samples
                FROM ranked
                GROUP BY definition_id
            )
            SELECT s.definition_id AS definition_id, d.name AS display_name,
                   s.failures AS consecutive_failures, s.samples AS sample_count
            FROM streak s
            JOIN yak_ops_task_definition d ON d.id = s.definition_id
            WHERE d.workspace_id = #{workspaceId}
              AND d.status = 1
              AND d.deleted_at IS NULL
              AND s.failures >= #{minimum}
            ORDER BY s.failures DESC, d.name
            """)
    List<TaskFailureCandidateRow> listConsecutiveFailureCandidates(
            @Param("workspaceId") String workspaceId,
            @Param("since") LocalDateTime since,
            @Param("minimum") int minimum);

    @Select("""
            SELECT d.id AS definition_id, d.name AS display_name,
                   ROUND(AVG(TIMESTAMPDIFF(SECOND, i.start_time, i.end_time))) AS average_seconds,
                   COUNT(*) AS sample_count
            FROM yak_ops_task_instance i
            JOIN yak_ops_task_definition d ON d.id = i.definition_id
            WHERE i.workspace_id = #{workspaceId}
              AND i.trigger_type = 'SCHEDULE'
              AND i.definition_version = d.current_version
              AND i.status = 'FINISHED'
              AND i.scheduled_time >= #{since}
              AND i.start_time IS NOT NULL
              AND i.end_time IS NOT NULL
              AND d.status = 1
              AND d.deleted_at IS NULL
            GROUP BY d.id, d.name
            HAVING COUNT(*) >= #{minimumRuns}
               AND AVG(TIMESTAMPDIFF(SECOND, i.start_time, i.end_time)) > #{thresholdSeconds}
            ORDER BY average_seconds DESC
            """)
    List<TaskSlowCandidateRow> listSlowRunCandidates(
            @Param("workspaceId") String workspaceId,
            @Param("since") LocalDateTime since,
            @Param("minimumRuns") int minimumRuns,
            @Param("thresholdSeconds") long thresholdSeconds);
}
