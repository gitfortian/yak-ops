package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for Workspace DWD/DIM models and their fields.
 *
 * <p>{@code type} is read as plain text on purpose: a hand-written row may carry a kind this build
 * does not know, and mapping it to an enum would fail the read instead of surfacing the row.
 */
@Mapper
public interface ModelMapper {
    String PROJECTION = "SELECT m.id,m.workspace_id,m.type,m.layer_id,m.category_id,m.process_id,m.domain_id,"
            + "m.datamart_id,m.dimension_id,m.name,m.naming_root_word_id,m.cn_name,m.storage_policy,m.lifecycle_days,"
            + "m.physical_properties,m.owner_id,m.description,m.revision,m.create_time,m.update_time,m.create_by,m.update_by,"
            + "l.cn_name AS layer_name, c.name AS category_name, p.name AS process_name, "
            + "dom.name AS domain_name, dm.name AS datamart_name, dim.name AS dimension_name, "
            + "(SELECT COUNT(*) FROM yak_ops_modeling_model_field f WHERE f.model_id=m.id) AS field_count "
            + "FROM yak_ops_modeling_model m "
            + "JOIN yak_ops_modeling_layer l ON l.workspace_id=m.workspace_id AND l.id=m.layer_id "
            + "JOIN yak_ops_modeling_category c ON c.workspace_id=m.workspace_id AND c.id=m.category_id "
            + "LEFT JOIN yak_ops_modeling_process p ON p.workspace_id=m.workspace_id AND p.id=m.process_id "
            + "LEFT JOIN yak_ops_modeling_domain dom ON dom.workspace_id=m.workspace_id AND dom.id=m.domain_id "
            + "LEFT JOIN yak_ops_modeling_datamart dm ON dm.workspace_id=m.workspace_id AND dm.id=m.datamart_id "
            + "LEFT JOIN yak_ops_modeling_dimension dim ON dim.workspace_id=m.workspace_id AND dim.id=m.dimension_id ";

    // name is ascii_bin while bound parameters arrive as utf8mb4; CONVERT keeps the LIKE collation-compatible.
    String FILTER = " WHERE m.workspace_id=#{workspaceId}"
            + "<if test='type != null'> AND m.type=#{type}</if>"
            + "<if test='layerId != null'> AND m.layer_id=#{layerId}</if>"
            + "<if test='ownerId != null'> AND m.owner_id=#{ownerId}</if>"
            + "<if test='domainId != null'> AND m.domain_id=#{domainId}</if>"
            + "<if test='datamartId != null'> AND m.datamart_id=#{datamartId}</if>"
            + "<if test='keyword != null'> AND (CONVERT(m.name USING utf8mb4) COLLATE utf8mb4_unicode_ci "
            + "LIKE #{keyword} ESCAPE '!' OR m.cn_name LIKE #{keyword} ESCAPE '!')</if> ";

    @Select("<script>"
            + PROJECTION
            + FILTER
            + "ORDER BY m.update_time DESC, m.id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<ModelEntity> page(
            @Param("workspaceId") String workspaceId,
            @Param("type") String type,
            @Param("layerId") String layerId,
            @Param("ownerId") String ownerId,
            @Param("domainId") String domainId,
            @Param("datamartId") String datamartId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM yak_ops_modeling_model m" + FILTER + "</script>")
    long count(
            @Param("workspaceId") String workspaceId,
            @Param("type") String type,
            @Param("layerId") String layerId,
            @Param("ownerId") String ownerId,
            @Param("domainId") String domainId,
            @Param("datamartId") String datamartId,
            @Param("keyword") String keyword);

    @Select(PROJECTION + "WHERE m.workspace_id=#{workspaceId} AND m.process_id=#{processId} "
            + "ORDER BY m.update_time DESC, m.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ModelEntity> pageByProcess(
            @Param("workspaceId") String workspaceId,
            @Param("processId") String processId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_model WHERE workspace_id=#{workspaceId} "
            + "AND process_id=#{processId}")
    long countByProcess(@Param("workspaceId") String workspaceId, @Param("processId") String processId);

    @Select(PROJECTION + "WHERE m.workspace_id=#{workspaceId} AND m.id=#{id}")
    ModelEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT * FROM yak_ops_modeling_model WHERE workspace_id=#{workspaceId} AND id=#{id} FOR UPDATE")
    ModelEntity lock(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_model WHERE workspace_id=#{workspaceId} AND name=#{name} "
            + "AND (#{excludeId} IS NULL OR id<>#{excludeId})")
    long countName(
            @Param("workspaceId") String workspaceId, @Param("name") String name, @Param("excludeId") String excludeId);

    @Insert("INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,domain_id,"
            + "datamart_id,dimension_id,name,naming_root_word_id,cn_name,storage_policy,lifecycle_days,"
            + "physical_properties,owner_id,"
            + "description,revision,create_time,update_time,create_by,update_by) "
            + "VALUES(#{id},#{workspaceId},#{type},#{layerId},"
            + "#{categoryId},#{processId},#{domainId},#{datamartId},#{dimensionId},#{name},#{namingRootWordId},"
            + "#{cnName},#{storagePolicy},#{lifecycleDays},#{physicalProperties},#{ownerId},#{description},"
            + "#{revision},#{createTime},"
            + "#{updateTime},#{createBy},#{updateBy})")
    int insert(ModelEntity row);

    @Update("UPDATE yak_ops_modeling_model SET type=#{type},layer_id=#{layerId},category_id=#{categoryId},"
            + "process_id=#{processId},domain_id=#{domainId},datamart_id=#{datamartId},"
            + "dimension_id=#{dimensionId},name=#{name},naming_root_word_id=#{namingRootWordId},"
            + "cn_name=#{cnName},storage_policy=#{storagePolicy},"
            + "lifecycle_days=#{lifecycleDays},physical_properties=#{physicalProperties},owner_id=#{ownerId},description=#{description},"
            + "revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(ModelEntity row);

    @Delete("DELETE FROM yak_ops_modeling_model WHERE workspace_id=#{workspaceId} AND id=#{id} "
            + "AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select("SELECT f.id,f.workspace_id,f.model_id,f.ordinal,f.en_code,f.cn_name,f.data_type,f.length_precision,"
            + "f.is_primary_key,f.is_not_null,f.is_partition,f.aggregate_type,f.standard_id,"
            + "f.lookup_id,f.measure_unit_id,f.metric_id,f.description,"
            + "f.create_time,"
            + "f.update_time,f.create_by,f.update_by,s.en_code AS standard_en_code,"
            + "l.en_code AS lookup_en_code,u.en_code AS measure_unit_en_code,"
            + "md.en_code AS metric_en_code,md.cn_name AS metric_cn_name "
            + "FROM yak_ops_modeling_model_field f "
            + "LEFT JOIN yak_ops_standard_field s ON s.workspace_id=f.workspace_id AND s.id=f.standard_id "
            + "LEFT JOIN yak_ops_standard_lookup l ON l.workspace_id=f.workspace_id AND l.id=f.lookup_id "
            + "LEFT JOIN yak_ops_standard_measure_unit u ON u.workspace_id=f.workspace_id AND u.id=f.measure_unit_id "
            + "LEFT JOIN yak_ops_modeling_metric_derived md ON md.workspace_id=f.workspace_id AND md.id=f.metric_id "
            + "WHERE f.model_id=#{modelId} ORDER BY f.ordinal,f.id")
    List<ModelFieldEntity> fields(@Param("modelId") String modelId);

    @Delete("DELETE FROM yak_ops_modeling_model_field WHERE model_id=#{modelId}")
    int deleteFields(@Param("modelId") String modelId);

    @Insert("INSERT INTO yak_ops_modeling_model_field(id,workspace_id,model_id,ordinal,en_code,cn_name,data_type,"
            + "length_precision,is_primary_key,is_not_null,is_partition,aggregate_type,standard_id,"
            + "lookup_id,measure_unit_id,metric_id,description,"
            + "create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{modelId},#{ordinal},"
            + "#{enCode},#{cnName},#{dataType},#{lengthPrecision},#{isPrimaryKey},#{isNotNull},#{isPartition},"
            + "#{aggregateType},#{standardId},#{lookupId},#{measureUnitId},#{metricId},#{description},#{createTime},"
            + "#{updateTime},#{createBy},#{updateBy})")
    int insertField(ModelFieldEntity row);

    @Select("SELECT s.en_code, s.cn_name, s.data_type, s.length_precision, s.is_not_null "
            + "FROM yak_ops_standard_field s WHERE s.workspace_id=#{workspaceId} AND s.id=#{id}")
    StandardProjection standard(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT m.id,m.type,m.name,m.cn_name,m.naming_root_word_id,p.code AS process_code "
            + "FROM yak_ops_modeling_model m "
            + "LEFT JOIN yak_ops_modeling_process p ON p.workspace_id=m.workspace_id AND p.id=m.process_id "
            + "WHERE m.workspace_id=#{workspaceId} AND m.layer_id=#{layerId} AND m.type IN ('DWD','DWS') "
            + "ORDER BY m.name,m.id")
    List<NamingRow> namingRows(@Param("workspaceId") String workspaceId, @Param("layerId") String layerId);

    @Update("UPDATE yak_ops_modeling_model SET naming_root_word_id=#{rootWordId} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    int backfillNamingRoot(
            @Param("workspaceId") String workspaceId, @Param("id") String id, @Param("rootWordId") String rootWordId);

    /** Minimal standard projection supplying the five inherited attributes. */
    @Getter
    @Setter
    static class StandardProjection {
        /** Inherited field code. */
        private String enCode;
        /** Inherited Chinese display name. */
        private String cnName;
        /** Inherited logical type text, converted by the service. */
        private String dataType;
        /** Inherited length or precision. */
        private String lengthPrecision;
        /** Inherited not-null flag. */
        private Boolean isNotNull;
    }

    /** One DWD or DWS model of a layer as the naming rule sees it. */
    @Getter
    @Setter
    static class NamingRow {
        /** Model identity. */
        private String id;
        /** Persisted model kind. */
        private String type;
        /** Full table name including the layer prefix. */
        private String name;
        /** Business display name. */
        private String cnName;
        /** Root word recorded when the name was composed under a rule, or null. */
        private String namingRootWordId;
        /** Code of the owning business process, or null for a malformed row. */
        private String processCode;
    }
}
