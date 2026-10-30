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

    /**
     * Finds an asset stored before a database was required, i.e. one whose catalog slot is empty.
     *
     * <p>Runs submitted without a database wrote {@code database_name=''} while enumerating every
     * visible catalog. Once a database is mandatory the same physical table would be inserted a
     * second time under its real database, so the crawler adopts the legacy row instead.
     */
    @Select(DataMapTableSqlProvider.PROJECTION + " WHERE t.workspace_id=#{workspaceId} "
            + "AND t.datasource_id=#{datasourceId} AND t.database_name='' "
            + "AND t.schema_name=#{schemaName} AND t.name=#{name}")
    DataMapTableEntity findLegacyIdentity(
            @Param("workspaceId") String workspaceId,
            @Param("datasourceId") String datasourceId,
            @Param("schemaName") String schemaName,
            @Param("name") String name);

    /** Rewrites the catalog slot of a legacy asset so its identity matches the observed namespace. */
    @Update("UPDATE yak_ops_data_map_table SET database_name=#{databaseName} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int adoptScope(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("databaseName") String databaseName);

    /**
     * Locks the asset row so a tag replace-set serialises on the asset instead of interleaving.
     *
     * <p>The lock names only the asset table: the catalog projection joins the datasource for its
     * display name, and a binding write has no reason to hold that row.
     */
    @Select("SELECT id FROM yak_ops_data_map_table WHERE workspace_id=#{workspaceId} AND id=#{id} FOR UPDATE")
    String lockById(@Param("workspaceId") String workspaceId, @Param("id") String id);

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

    /**
     * Marks assets stored before a database was required as absent, after a scoped run succeeds.
     *
     * <p>Those rows carry an empty catalog slot, so a scoped run can never address them again. The
     * ones this run did observe were re-keyed into the run's database, which refreshed their
     * {@code last_seen_time}; anything still empty and older is no longer reachable from any
     * database-scoped crawl and would otherwise stay ACTIVE forever.
     */
    @Update("UPDATE yak_ops_data_map_table SET status='MISSING',update_time=#{now} "
            + "WHERE workspace_id=#{workspaceId} AND datasource_id=#{datasourceId} "
            + "AND database_name='' AND status='ACTIVE' AND last_seen_time<#{startedTime}")
    int markLegacyMissing(
            @Param("workspaceId") String workspaceId,
            @Param("datasourceId") String datasourceId,
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
