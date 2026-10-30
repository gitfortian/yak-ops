package io.yak.ops.dao.mapper.datamap;

import io.yak.ops.dao.entity.datamap.DataMapCrawlEntity;
import io.yak.ops.dao.entity.datamap.DataMapCrawlFailureEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for crawl runs and their per-table failures.
 *
 * <p>Every terminal transition releases {@code active_scope} in the same statement that writes the
 * status, so no path can leave the scope lock behind. {@link #sweepInterrupted} is the restart
 * recovery and follows the same rule.
 */
@Mapper
public interface DataMapCrawlMapper {

    String SELECT_COLUMNS = "c.id,c.workspace_id,c.task_id,c.datasource_id,c.datasource_revision,"
            + "c.database_name,c.schema_name,c.status,c.active_scope,c.table_count,c.column_count,"
            + "c.added_count,c.updated_count,c.removed_count,c.failed_count,c.filtered_count,"
            + "c.trigger_type,c.exclude_pattern,c.exclude_tables,c.retry_of,c.error,"
            + "c.created_time,c.started_time,c.finished_time,c.create_by,"
            + "(SELECT t.name FROM yak_ops_data_map_crawl_task t WHERE t.id=c.task_id) AS task_name";

    String INSERT_COLUMNS = "id,workspace_id,task_id,datasource_id,datasource_revision,database_name,schema_name,"
            + "status,active_scope,table_count,column_count,added_count,updated_count,removed_count,failed_count,"
            + "filtered_count,trigger_type,exclude_pattern,exclude_tables,retry_of,error,"
            + "created_time,started_time,finished_time,create_by";

    @Insert("INSERT INTO yak_ops_data_map_crawl (" + INSERT_COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{taskId},#{datasourceId},#{datasourceRevision},#{databaseName},#{schemaName},"
            + "#{status},#{activeScope},#{tableCount},#{columnCount},#{addedCount},#{updatedCount},#{removedCount},"
            + "#{failedCount},#{filteredCount},#{triggerType},#{excludePattern},#{excludeTables},#{retryOf},"
            + "#{error},#{createdTime},#{startedTime},#{finishedTime},#{createBy})")
    int insert(DataMapCrawlEntity row);

    @Select("SELECT " + SELECT_COLUMNS + " FROM yak_ops_data_map_crawl c WHERE c.id=#{id}")
    DataMapCrawlEntity findById(@Param("id") String id);

    @Select("SELECT " + SELECT_COLUMNS + " FROM yak_ops_data_map_crawl c "
            + "WHERE c.workspace_id=#{workspaceId} AND c.id=#{id}")
    DataMapCrawlEntity findByIdForWorkspace(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Update("UPDATE yak_ops_data_map_crawl SET status='RUNNING',started_time=#{startedTime} "
            + "WHERE id=#{id} AND status='PENDING'")
    int claim(@Param("id") String id, @Param("startedTime") LocalDateTime startedTime);

    @Update("UPDATE yak_ops_data_map_crawl SET status='SUCCEEDED',active_scope=NULL,table_count=#{tableCount},"
            + "column_count=#{columnCount},added_count=#{addedCount},updated_count=#{updatedCount},"
            + "removed_count=#{removedCount},failed_count=#{failedCount},filtered_count=#{filteredCount},"
            + "finished_time=#{finishedTime} "
            + "WHERE id=#{id} AND status='RUNNING'")
    int finishSucceeded(
            @Param("id") String id,
            @Param("tableCount") int tableCount,
            @Param("columnCount") int columnCount,
            @Param("addedCount") int addedCount,
            @Param("updatedCount") int updatedCount,
            @Param("removedCount") int removedCount,
            @Param("failedCount") int failedCount,
            @Param("filteredCount") int filteredCount,
            @Param("finishedTime") LocalDateTime finishedTime);

    @Update("UPDATE yak_ops_data_map_crawl SET status='FAILED',active_scope=NULL,error=#{error},"
            + "table_count=#{tableCount},column_count=#{columnCount},added_count=#{addedCount},"
            + "updated_count=#{updatedCount},removed_count=0,failed_count=#{failedCount},"
            + "finished_time=#{finishedTime} WHERE id=#{id} AND status='RUNNING'")
    int finishFailed(
            @Param("id") String id,
            @Param("error") String error,
            @Param("tableCount") int tableCount,
            @Param("columnCount") int columnCount,
            @Param("addedCount") int addedCount,
            @Param("updatedCount") int updatedCount,
            @Param("failedCount") int failedCount,
            @Param("finishedTime") LocalDateTime finishedTime);

    /** Rejects a submission the executor could not accept, releasing the scope lock immediately. */
    @Update("UPDATE yak_ops_data_map_crawl SET status='FAILED',active_scope=NULL,error=#{error},"
            + "finished_time=#{finishedTime} WHERE id=#{id} AND status='PENDING'")
    int reject(@Param("id") String id, @Param("error") String error, @Param("finishedTime") LocalDateTime finishedTime);

    /**
     * Marks every active run as INTERRUPTED after a restart and releases its scope lock in the same
     * statement, so the operator sees a distinct state from a genuine failure and can submit again.
     */
    @Update("UPDATE yak_ops_data_map_crawl SET status='INTERRUPTED',active_scope=NULL,error=#{error},"
            + "finished_time=#{finishedTime} WHERE active_scope IS NOT NULL")
    int sweepInterrupted(@Param("error") String error, @Param("finishedTime") LocalDateTime finishedTime);

    @Insert("INSERT INTO yak_ops_data_map_crawl_failure (id,workspace_id,crawl_id,table_name,error,created_time) "
            + "VALUES (#{id},#{workspaceId},#{crawlId},#{tableName},#{error},#{createdTime})")
    int insertFailure(DataMapCrawlFailureEntity row);

    @Select("SELECT id,workspace_id,crawl_id,table_name,error,created_time FROM yak_ops_data_map_crawl_failure "
            + "WHERE crawl_id=#{crawlId} ORDER BY created_time, id LIMIT #{limit}")
    List<DataMapCrawlFailureEntity> listFailures(@Param("crawlId") String crawlId, @Param("limit") int limit);

    /** Filters of the run feed, shared by the page and the count. */
    String RUN_FILTER = "<if test=\"datasourceId != null\"> AND c.datasource_id=#{datasourceId}</if>"
            + "<if test=\"taskId != null\"> AND c.task_id=#{taskId}</if>"
            + "<if test=\"status != null\"> AND c.status=#{status}</if>"
            + "<if test=\"triggerType != null\"> AND c.trigger_type=#{triggerType}</if>"
            + "<if test=\"failed != null and failed\"> AND c.failed_count &gt; 0</if>"
            + "<if test=\"failed != null and !failed\"> AND c.failed_count = 0</if>"
            + "<if test=\"since != null\"> AND c.created_time &gt;= #{since}</if>";

    @Select("<script>SELECT " + SELECT_COLUMNS + " FROM yak_ops_data_map_crawl c "
            + "WHERE c.workspace_id=#{workspaceId}" + RUN_FILTER
            + " ORDER BY c.created_time DESC, c.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<DataMapCrawlEntity> listPage(
            @Param("workspaceId") String workspaceId,
            @Param("datasourceId") String datasourceId,
            @Param("taskId") String taskId,
            @Param("status") String status,
            @Param("triggerType") String triggerType,
            @Param("failed") Boolean failed,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM yak_ops_data_map_crawl c WHERE c.workspace_id=#{workspaceId}" + RUN_FILTER
            + "</script>")
    long count(
            @Param("workspaceId") String workspaceId,
            @Param("datasourceId") String datasourceId,
            @Param("taskId") String taskId,
            @Param("status") String status,
            @Param("triggerType") String triggerType,
            @Param("failed") Boolean failed,
            @Param("since") LocalDateTime since);

    /**
     * Counts runs of one workspace by outcome inside a time window.
     *
     * <p>{@code COUNT(CASE ...)} rather than {@code SUM(...)} keeps the result integral on every
     * supported dialect and yields zero instead of null for an empty window, so the caller never
     * has to explain a null count.
     *
     * @param workspaceId owning Workspace
     * @param since earliest submission time included
     * @return outcome distribution of the window
     */
    @Select("SELECT COUNT(*) AS total,"
            + "COUNT(CASE WHEN c.status='SUCCEEDED' AND c.failed_count=0 THEN 1 END) AS succeeded,"
            + "COUNT(CASE WHEN c.status='SUCCEEDED' AND c.failed_count>0 THEN 1 END) AS partial,"
            + "COUNT(CASE WHEN c.status='FAILED' THEN 1 END) AS failed,"
            + "COUNT(CASE WHEN c.status='INTERRUPTED' THEN 1 END) AS interrupted,"
            + "COUNT(CASE WHEN c.status='RUNNING' THEN 1 END) AS running,"
            + "COUNT(CASE WHEN c.status='PENDING' THEN 1 END) AS pending "
            + "FROM yak_ops_data_map_crawl c WHERE c.workspace_id=#{workspaceId} AND c.created_time>=#{since}")
    DataMapRunSummaryRow summarizeRuns(@Param("workspaceId") String workspaceId, @Param("since") LocalDateTime since);

    /**
     * Quality figures of the run window: today's ratio, a seven-day success rate and how long a
     * successful run takes.
     *
     * <p>Only terminal outcomes (SUCCEEDED or FAILED) enter every figure, so a run that is still
     * queued cannot move the success rate in either direction. A succeeded run with failed tables
     * counts as a success, because it did catalogue the namespace. Duration is measured over the
     * successful runs only: a failed run usually aborts early and would drag the average down
     * without describing a healthy run.
     *
     * @param workspaceId owning Workspace
     * @param todayStart local start of the current day
     * @param weekStart local start seven days ago
     * @return quality figures of the window
     */
    @Select("SELECT COUNT(CASE WHEN c.created_time>=#{todayStart} THEN 1 END) AS today_total,"
            + "COUNT(CASE WHEN c.created_time>=#{todayStart} AND c.status='SUCCEEDED' THEN 1 END)"
            + " AS today_succeeded,"
            + "COUNT(CASE WHEN c.created_time>=#{weekStart} THEN 1 END) AS week_total,"
            + "COUNT(CASE WHEN c.created_time>=#{weekStart} AND c.status='SUCCEEDED' THEN 1 END)"
            + " AS week_succeeded,"
            + "AVG(CASE WHEN c.status='SUCCEEDED' AND c.started_time IS NOT NULL"
            + " AND c.finished_time IS NOT NULL"
            + " AND c.created_time>=#{weekStart}"
            + " THEN TIMESTAMPDIFF(SECOND, c.started_time, c.finished_time) END) AS avg_seconds"
            + " FROM yak_ops_data_map_crawl c"
            + " WHERE c.workspace_id=#{workspaceId}"
            + " AND c.status IN ('SUCCEEDED','FAILED')"
            + " AND c.created_time>=#{weekStart}")
    DataMapRunQualityRow summarizeRunQuality(
            @Param("workspaceId") String workspaceId,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("weekStart") LocalDateTime weekStart);

    /**
     * P95 duration of successful runs in the window, resolved with a window function because no
     * supported dialect ships a percentile aggregate. Rows are rare (a few runs per datasource
     * per day), so scanning them twice is cheaper than the complexity of an approximation.
     *
     * @param workspaceId owning Workspace
     * @param weekStart local start seven days ago
     * @return p95 of successful run durations in seconds, or null when nothing succeeded
     */
    @Select("WITH ranked AS (SELECT TIMESTAMPDIFF(SECOND, c.started_time, c.finished_time) AS seconds,"
            + " ROW_NUMBER() OVER (ORDER BY c.finished_time-c.started_time) AS rn,"
            + " COUNT(*) OVER () AS total"
            + " FROM yak_ops_data_map_crawl c"
            + " WHERE c.workspace_id=#{workspaceId} AND c.status='SUCCEEDED'"
            + " AND c.started_time IS NOT NULL AND c.finished_time IS NOT NULL"
            + " AND c.created_time>=#{weekStart})"
            + " SELECT MIN(CASE WHEN rn>=CEIL(total*0.95) THEN seconds END) AS p95_seconds FROM ranked")
    Long percentile95Duration(@Param("workspaceId") String workspaceId, @Param("weekStart") LocalDateTime weekStart);

    /**
     * Sums the failure backlog over the newest run of each task.
     *
     * <p>A failure a later run already fixed is not pending anymore, so the newest run per task is
     * the unit of measurement. Runs submitted outside a task carry no schedule to act on and are
     * excluded, which keeps the figure consistent with the task list that filters the same way.
     *
     * @param workspaceId owning Workspace
     * @return failed tables and abnormal task count of the current state
     */
    @Select("SELECT COALESCE(SUM(x.failed_count),0) AS failed_tables,"
            + "COUNT(CASE WHEN x.status='FAILED' OR x.failed_count>0 THEN 1 END) AS abnormal_tasks "
            + "FROM yak_ops_data_map_crawl x WHERE x.workspace_id=#{workspaceId} AND x.task_id IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM yak_ops_data_map_crawl n WHERE n.task_id=x.task_id "
            + "AND (n.created_time > x.created_time OR (n.created_time = x.created_time AND n.id > x.id)))")
    DataMapTaskFailureRow summarizeLatestTaskFailures(@Param("workspaceId") String workspaceId);

    /**
     * Reads the newest run of each requested task, so a task list shows one status per task without
     * loading run history.
     *
     * @param workspaceId owning Workspace
     * @param taskIds task identities to resolve
     * @return the newest run of each task that has one
     */
    @Select("<script>SELECT " + SELECT_COLUMNS + " FROM yak_ops_data_map_crawl c "
            + "WHERE c.workspace_id=#{workspaceId} AND c.task_id IN "
            + "<foreach collection=\"taskIds\" item=\"taskId\" open=\"(\" separator=\",\" close=\")\">#{taskId}</foreach> "
            + "AND NOT EXISTS (SELECT 1 FROM yak_ops_data_map_crawl n WHERE n.task_id=c.task_id "
            + "AND (n.created_time > c.created_time OR (n.created_time = c.created_time AND n.id > c.id)))</script>")
    List<DataMapCrawlEntity> listLatestByTaskIds(
            @Param("workspaceId") String workspaceId, @Param("taskIds") List<String> taskIds);

    /** Counts runs of a task that still hold the scope lock, so a task cannot be deleted mid-run. */
    @Select("SELECT COUNT(*) FROM yak_ops_data_map_crawl WHERE workspace_id=#{workspaceId} "
            + "AND task_id=#{taskId} AND active_scope IS NOT NULL")
    long countActiveByTask(@Param("workspaceId") String workspaceId, @Param("taskId") String taskId);
}
