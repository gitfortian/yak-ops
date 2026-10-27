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

    String COLUMNS = "id,workspace_id,datasource_id,datasource_revision,database_name,schema_name,status,"
            + "active_scope,table_count,column_count,added_count,updated_count,removed_count,failed_count,error,"
            + "created_time,started_time,finished_time,create_by";

    @Insert("INSERT INTO yak_ops_data_map_crawl (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{datasourceId},#{datasourceRevision},#{databaseName},#{schemaName},#{status},"
            + "#{activeScope},#{tableCount},#{columnCount},#{addedCount},#{updatedCount},#{removedCount},"
            + "#{failedCount},#{error},#{createdTime},#{startedTime},#{finishedTime},#{createBy})")
    int insert(DataMapCrawlEntity row);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_crawl WHERE id=#{id}")
    DataMapCrawlEntity findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_crawl WHERE workspace_id=#{workspaceId} AND id=#{id}")
    DataMapCrawlEntity findByIdForWorkspace(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Update("UPDATE yak_ops_data_map_crawl SET status='RUNNING',started_time=#{startedTime} "
            + "WHERE id=#{id} AND status='PENDING'")
    int claim(@Param("id") String id, @Param("startedTime") LocalDateTime startedTime);

    @Update("UPDATE yak_ops_data_map_crawl SET status='SUCCEEDED',active_scope=NULL,table_count=#{tableCount},"
            + "column_count=#{columnCount},added_count=#{addedCount},updated_count=#{updatedCount},"
            + "removed_count=#{removedCount},failed_count=#{failedCount},finished_time=#{finishedTime} "
            + "WHERE id=#{id} AND status='RUNNING'")
    int finishSucceeded(
            @Param("id") String id,
            @Param("tableCount") int tableCount,
            @Param("columnCount") int columnCount,
            @Param("addedCount") int addedCount,
            @Param("updatedCount") int updatedCount,
            @Param("removedCount") int removedCount,
            @Param("failedCount") int failedCount,
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

    /** Fails every active run after a restart and releases its scope lock in the same statement. */
    @Update("UPDATE yak_ops_data_map_crawl SET status='FAILED',active_scope=NULL,error=#{error},"
            + "finished_time=#{finishedTime} WHERE active_scope IS NOT NULL")
    int sweepInterrupted(@Param("error") String error, @Param("finishedTime") LocalDateTime finishedTime);

    @Insert("INSERT INTO yak_ops_data_map_crawl_failure (id,workspace_id,crawl_id,table_name,error,created_time) "
            + "VALUES (#{id},#{workspaceId},#{crawlId},#{tableName},#{error},#{createdTime})")
    int insertFailure(DataMapCrawlFailureEntity row);

    @Select("SELECT id,workspace_id,crawl_id,table_name,error,created_time FROM yak_ops_data_map_crawl_failure "
            + "WHERE crawl_id=#{crawlId} ORDER BY created_time, id LIMIT #{limit}")
    List<DataMapCrawlFailureEntity> listFailures(@Param("crawlId") String crawlId, @Param("limit") int limit);

    @Select("<script>SELECT " + COLUMNS + " FROM yak_ops_data_map_crawl WHERE workspace_id=#{workspaceId}"
            + "<if test=\"datasourceId != null\"> AND datasource_id=#{datasourceId}</if>"
            + " ORDER BY created_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<DataMapCrawlEntity> listPage(
            @Param("workspaceId") String workspaceId,
            @Param("datasourceId") String datasourceId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM yak_ops_data_map_crawl WHERE workspace_id=#{workspaceId}"
            + "<if test=\"datasourceId != null\"> AND datasource_id=#{datasourceId}</if></script>")
    long count(@Param("workspaceId") String workspaceId, @Param("datasourceId") String datasourceId);
}
