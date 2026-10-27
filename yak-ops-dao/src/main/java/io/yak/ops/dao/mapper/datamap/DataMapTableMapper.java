package io.yak.ops.dao.mapper.datamap;

import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for catalog assets.
 *
 * <p>Writes are split by owner: {@link #updateObserved} names only crawler-owned columns and the
 * audit update time, while {@link #updateCurated} names only the three curated columns. Neither
 * statement can reach the other's columns, so no merge rule is needed.
 */
@Mapper
public interface DataMapTableMapper {

    String COLUMNS = "id,workspace_id,datasource_id,database_name,schema_name,name,table_type,remarks,status,"
            + "first_seen_time,last_seen_time,description,owner_id,domain_id,create_time,update_time,create_by,"
            + "update_by";

    @Insert("INSERT INTO yak_ops_data_map_table (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{datasourceId},#{databaseName},#{schemaName},#{name},#{tableType},"
            + "#{remarks},#{status},#{firstSeenTime},#{lastSeenTime},#{description},#{ownerId},#{domainId},"
            + "#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(DataMapTableEntity row);

    @Select(DataMapTableSqlProvider.PROJECTION + " WHERE t.workspace_id=#{workspaceId} "
            + "AND t.datasource_id=#{datasourceId} AND t.database_name=#{databaseName} "
            + "AND t.schema_name=#{schemaName} AND t.name=#{name}")
    DataMapTableEntity findByIdentity(
            @Param("workspaceId") String workspaceId,
            @Param("datasourceId") String datasourceId,
            @Param("databaseName") String databaseName,
            @Param("schemaName") String schemaName,
            @Param("name") String name);

    @Select(DataMapTableSqlProvider.PROJECTION + " WHERE t.workspace_id=#{workspaceId} AND t.id=#{id}")
    DataMapTableEntity findById(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("<script>" + DataMapTableSqlProvider.PROJECTION + " WHERE t.workspace_id=#{workspaceId} "
            + "AND t.id IN <foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}"
            + "</foreach></script>")
    List<DataMapTableEntity> findByIds(@Param("workspaceId") String workspaceId, @Param("ids") List<String> ids);

    /** Refreshes crawler-owned metadata only; curated columns are unreachable from here. */
    @Update("UPDATE yak_ops_data_map_table SET table_type=#{tableType},remarks=#{remarks},status=#{status},"
            + "last_seen_time=#{lastSeenTime},update_time=#{updateTime} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int updateObserved(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("tableType") String tableType,
            @Param("remarks") String remarks,
            @Param("status") String status,
            @Param("lastSeenTime") LocalDateTime lastSeenTime,
            @Param("updateTime") LocalDateTime updateTime);

    /** Replaces the three curated columns only; observed and audit columns are unreachable here. */
    @Update("UPDATE yak_ops_data_map_table SET description=#{description},owner_id=#{ownerId},"
            + "domain_id=#{domainId} WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int updateCurated(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("description") String description,
            @Param("ownerId") String ownerId,
            @Param("domainId") String domainId);

    /** Marks active assets of one namespace as absent after a complete run. */
    @Update("UPDATE yak_ops_data_map_table SET status='MISSING',update_time=#{now} "
            + "WHERE workspace_id=#{workspaceId} AND datasource_id=#{datasourceId} "
            + "AND database_name=#{databaseName} AND schema_name=#{schemaName} "
            + "AND status='ACTIVE' AND last_seen_time<#{startedTime}")
    int markMissing(
            @Param("workspaceId") String workspaceId,
            @Param("datasourceId") String datasourceId,
            @Param("databaseName") String databaseName,
            @Param("schemaName") String schemaName,
            @Param("startedTime") LocalDateTime startedTime,
            @Param("now") LocalDateTime now);

    @SelectProvider(type = DataMapTableSqlProvider.class, method = "select")
    List<DataMapTableEntity> selectPage(DataMapSearchCriteria criteria);

    @SelectProvider(type = DataMapTableSqlProvider.class, method = "count")
    long count(DataMapSearchCriteria criteria);

    @Select("SELECT COUNT(*) FROM yak_ops_data_map_table WHERE workspace_id=#{workspaceId} AND status='ACTIVE'")
    long countActive(@Param("workspaceId") String workspaceId);

    @Select("SELECT COUNT(*) FROM yak_ops_data_map_table WHERE workspace_id=#{workspaceId} "
            + "AND status='ACTIVE' AND domain_id IS NULL")
    long countUnclassified(@Param("workspaceId") String workspaceId);

    @Select("SELECT COUNT(*) FROM yak_ops_data_map_table WHERE workspace_id=#{workspaceId} "
            + "AND status='ACTIVE' AND first_seen_time>=#{since}")
    long countFirstSeenSince(@Param("workspaceId") String workspaceId, @Param("since") LocalDateTime since);

    @Select("SELECT COUNT(DISTINCT datasource_id) FROM yak_ops_data_map_table WHERE workspace_id=#{workspaceId}")
    long countDatasources(@Param("workspaceId") String workspaceId);
}
