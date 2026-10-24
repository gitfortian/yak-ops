package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.dao.entity.modeling.MetadataCollectionEntity;
import io.yak.ops.dao.entity.modeling.MetadataFieldEntity;
import io.yak.ops.dao.entity.modeling.MetadataTableEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Parameter-bound statements for metadata snapshots.
 *
 * <p>A snapshot is replaced as a whole: {@link #deleteByPublication} removes the previous run and
 * its children through the {@code ON DELETE CASCADE} keys before the new run is inserted in the same
 * transaction.
 */
@Mapper
public interface MetadataMapper {
    @Insert("INSERT INTO yak_ops_modeling_metadata_collection "
            + "(id,workspace_id,publication_id,status,table_count,field_count,error,created_time,finished_time,"
            + "create_by) VALUES (#{id},#{workspaceId},#{publicationId},#{status},#{tableCount},#{fieldCount},"
            + "#{error},#{createdTime},#{finishedTime},#{createBy})")
    int insertCollection(MetadataCollectionEntity row);

    @Insert("INSERT INTO yak_ops_modeling_metadata_table "
            + "(id,workspace_id,collection_id,datasource_id,database_name,schema_name,name,type,remarks) VALUES "
            + "(#{id},#{workspaceId},#{collectionId},#{datasourceId},#{databaseName},#{schemaName},#{name},"
            + "#{type},#{remarks})")
    int insertTable(MetadataTableEntity row);

    @Insert("INSERT INTO yak_ops_modeling_metadata_field "
            + "(id,workspace_id,table_id,ordinal,name,type_name,jdbc_type,size,scale,nullable,primary_key,"
            + "primary_key_position,remarks) VALUES "
            + "(#{id},#{workspaceId},#{tableId},#{ordinal},#{name},#{typeName},#{jdbcType},#{size},#{scale},"
            + "#{nullable},#{primaryKey},#{primaryKeyPosition},#{remarks})")
    int insertField(MetadataFieldEntity row);

    @Select("SELECT c.id,c.workspace_id,c.publication_id,c.status,c.table_count,c.field_count,c.error,"
            + "c.created_time,c.finished_time,c.create_by FROM yak_ops_modeling_metadata_collection c "
            + "WHERE c.workspace_id=#{workspaceId} AND c.publication_id=#{publicationId} "
            + "ORDER BY c.created_time DESC, c.id DESC LIMIT 1")
    MetadataCollectionEntity latestCollection(
            @Param("workspaceId") String workspaceId, @Param("publicationId") String publicationId);

    @Select("SELECT t.id,t.workspace_id,t.collection_id,t.datasource_id,t.database_name,t.schema_name,t.name,"
            + "t.type,t.remarks FROM yak_ops_modeling_metadata_table t "
            + "WHERE t.workspace_id=#{workspaceId} AND t.collection_id=#{collectionId} "
            + "ORDER BY t.name, t.id")
    List<MetadataTableEntity> listTables(
            @Param("workspaceId") String workspaceId, @Param("collectionId") String collectionId);

    @Select("SELECT f.id,f.workspace_id,f.table_id,f.ordinal,f.name,f.type_name,f.jdbc_type,f.size,f.scale,"
            + "f.nullable,f.primary_key,f.primary_key_position,f.remarks FROM yak_ops_modeling_metadata_field f "
            + "WHERE f.workspace_id=#{workspaceId} AND f.table_id=#{tableId} ORDER BY f.ordinal, f.id")
    List<MetadataFieldEntity> listFields(@Param("workspaceId") String workspaceId, @Param("tableId") String tableId);

    @Delete("DELETE FROM yak_ops_modeling_metadata_collection "
            + "WHERE workspace_id=#{workspaceId} AND publication_id=#{publicationId}")
    int deleteByPublication(@Param("workspaceId") String workspaceId, @Param("publicationId") String publicationId);
}
