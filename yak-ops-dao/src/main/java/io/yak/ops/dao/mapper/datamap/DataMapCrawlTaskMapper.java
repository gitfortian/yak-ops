package io.yak.ops.dao.mapper.datamap;

import io.yak.ops.dao.entity.datamap.DataMapCrawlTaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for metadata collection tasks.
 *
 * <p>Status transitions are conditional on the status the caller observed, so a concurrent
 * online/offline or delete cannot be silently overwritten. Due selection and the next-fire advance
 * are separate statements: the scheduler advances one row conditionally, which makes a duplicate
 * trigger occurrence impossible even when two pollers overlap.
 */
@Mapper
public interface DataMapCrawlTaskMapper {

    String COLUMNS = "id,workspace_id,name,datasource_id,database_name,schema_name,owner_id,status,"
            + "schedule_type,cron_expression,next_fire_time,exclude_pattern,exclude_tables,"
            + "create_time,update_time,create_by,update_by";

    @Insert("INSERT INTO yak_ops_data_map_crawl_task (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{name},#{datasourceId},#{databaseName},#{schemaName},#{ownerId},#{status},"
            + "#{scheduleType},#{cronExpression},#{nextFireTime},#{excludePattern},#{excludeTables},"
            + "#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(DataMapCrawlTaskEntity row);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_crawl_task "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    DataMapCrawlTaskEntity findByIdForWorkspace(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /**
     * Builds the task filter shared by the page and the count.
     *
     * <p>The keyword is already LIKE-escaped by the service, so every fragment below is either a
     * fixed clause or a bound placeholder.
     *
     * <p>{@code abnormal} reads the newest run of the task rather than any run: a task that failed
     * last week and succeeded today is healthy, so only the current attempt may make it abnormal.
     * The same predicate decides the health band's abnormal count, which keeps the number and the
     * filter it links to in agreement.
     */
    String FILTER = "<if test=\"status != null\"> AND t.status=#{status}</if>"
            + "<if test=\"datasourceId != null\"> AND t.datasource_id=#{datasourceId}</if>"
            + "<if test=\"keywordPattern != null\"> AND (t.name LIKE #{keywordPattern} ESCAPE '!'"
            + " OR t.database_name LIKE #{keywordPattern} ESCAPE '!'"
            + " OR t.schema_name LIKE #{keywordPattern} ESCAPE '!'"
            + " OR EXISTS (SELECT 1 FROM yak_ops_data_source ds WHERE ds.id=t.datasource_id"
            + " AND ds.name LIKE #{keywordPattern} ESCAPE '!'))</if>"
            + "<if test=\"abnormal != null and abnormal\"> AND EXISTS (SELECT 1 FROM yak_ops_data_map_crawl c"
            + " WHERE c.task_id=t.id AND (c.status='FAILED' OR (c.status='SUCCEEDED' AND c.failed_count &gt; 0))"
            + " AND NOT EXISTS (SELECT 1 FROM yak_ops_data_map_crawl n WHERE n.task_id=c.task_id"
            + " AND (n.created_time &gt; c.created_time"
            + " OR (n.created_time = c.created_time AND n.id &gt; c.id))))</if>";

    @Select("<script>SELECT t.id,t.workspace_id,t.name,t.datasource_id,t.database_name,t.schema_name,"
            + "t.owner_id,t.status,t.schedule_type,t.cron_expression,t.next_fire_time,t.exclude_pattern,"
            + "t.exclude_tables,t.create_time,t.update_time,t.create_by,t.update_by "
            + "FROM yak_ops_data_map_crawl_task t WHERE t.workspace_id=#{workspaceId}" + FILTER
            + " ORDER BY t.create_time DESC, t.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<DataMapCrawlTaskEntity> listPage(
            @Param("workspaceId") String workspaceId,
            @Param("keywordPattern") String keywordPattern,
            @Param("status") String status,
            @Param("datasourceId") String datasourceId,
            @Param("abnormal") Boolean abnormal,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM yak_ops_data_map_crawl_task t " + "WHERE t.workspace_id=#{workspaceId}"
            + FILTER + "</script>")
    long count(
            @Param("workspaceId") String workspaceId,
            @Param("keywordPattern") String keywordPattern,
            @Param("status") String status,
            @Param("datasourceId") String datasourceId,
            @Param("abnormal") Boolean abnormal);

    @Update("UPDATE yak_ops_data_map_crawl_task SET name=#{name},datasource_id=#{datasourceId},"
            + "database_name=#{databaseName},schema_name=#{schemaName},owner_id=#{ownerId},status=#{status},"
            + "schedule_type=#{scheduleType},cron_expression=#{cronExpression},next_fire_time=#{nextFireTime},"
            + "exclude_pattern=#{excludePattern},exclude_tables=#{excludeTables},"
            + "update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int update(DataMapCrawlTaskEntity row);

    /**
     * Applies a lifecycle transition only when the row is still in the expected status.
     *
     * @param workspaceId owning Workspace
     * @param id task identity
     * @param expectedStatus status the caller observed
     * @param status status to apply
     * @param nextFireTime next scheduled trigger, or null when not scheduled
     * @param updateTime transition timestamp
     * @param updateBy acting user
     * @return number of rows changed; zero means the task changed concurrently
     */
    @Update("UPDATE yak_ops_data_map_crawl_task SET status=#{status},next_fire_time=#{nextFireTime},"
            + "update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status=#{expectedStatus}")
    int transitionStatus(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("expectedStatus") String expectedStatus,
            @Param("status") String status,
            @Param("nextFireTime") LocalDateTime nextFireTime,
            @Param("updateTime") LocalDateTime updateTime,
            @Param("updateBy") String updateBy);

    /**
     * Deletes a task that is not running. Runs cascade through {@code fk_data_map_crawl_task}.
     *
     * @param workspaceId owning Workspace
     * @param id task identity
     * @return number of rows deleted
     */
    @Delete("DELETE FROM yak_ops_data_map_crawl_task "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status IN ('DRAFT', 'OFFLINE')")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_crawl_task "
            + "WHERE status='RUNNING' AND schedule_type='CRON' AND next_fire_time IS NOT NULL "
            + "AND next_fire_time <= #{now} ORDER BY next_fire_time, id LIMIT #{limit}")
    List<DataMapCrawlTaskEntity> listDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * Advances the next trigger only when it still equals the occurrence this poller observed.
     *
     * @param id task identity
     * @param expectedNextFireTime occurrence observed by the poller
     * @param nextFireTime occurrence to apply
     * @return number of rows changed; zero means another poller already advanced it
     */
    @Update("UPDATE yak_ops_data_map_crawl_task SET next_fire_time=#{nextFireTime} "
            + "WHERE id=#{id} AND next_fire_time=#{expectedNextFireTime}")
    int advanceNextFire(
            @Param("id") String id,
            @Param("expectedNextFireTime") LocalDateTime expectedNextFireTime,
            @Param("nextFireTime") LocalDateTime nextFireTime);
}
