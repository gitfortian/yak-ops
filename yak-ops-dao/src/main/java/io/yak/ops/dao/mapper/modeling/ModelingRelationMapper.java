package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.dao.entity.modeling.ModelingRelationColumnEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationEntity;
import io.yak.ops.dao.repository.modeling.RelationKeyScanRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for Workspace model relations and their ordered key pairs.
 *
 * <p>Key pairs reference field {@code en_code} values, never field row ids: model fields are
 * replaced wholesale on every save, so codes are the stable business contract. A code that no
 * longer resolves is surfaced as a first-class broken state by the service layer.
 */
@Mapper
public interface ModelingRelationMapper {
    String RELATION_PROJECTION = "SELECT r.id,r.workspace_id,r.name,r.from_model_id,r.to_model_id,r.join_type,"
            + "r.description,r.revision,r.create_time,r.update_time,r.create_by,r.update_by,"
            + "mf.cn_name AS from_model_name, mf.type AS from_model_type,"
            + "mt.cn_name AS to_model_name, mt.type AS to_model_type "
            + "FROM yak_ops_modeling_relation r "
            + "JOIN yak_ops_modeling_model mf ON mf.workspace_id=r.workspace_id AND mf.id=r.from_model_id "
            + "JOIN yak_ops_modeling_model mt ON mt.workspace_id=r.workspace_id AND mt.id=r.to_model_id ";

    @Select(RELATION_PROJECTION + "WHERE r.workspace_id=#{workspaceId} AND r.id=#{id}")
    ModelingRelationEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT * FROM yak_ops_modeling_relation WHERE workspace_id=#{workspaceId} AND id=#{id} FOR UPDATE")
    ModelingRelationEntity lock(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_relation WHERE workspace_id=#{workspaceId} AND name=#{name} "
            + "AND (#{excludeId} IS NULL OR id<>#{excludeId})")
    long countName(
            @Param("workspaceId") String workspaceId, @Param("name") String name, @Param("excludeId") String excludeId);

    @Select(RELATION_PROJECTION
            + "WHERE r.workspace_id=#{workspaceId} AND (r.from_model_id=#{modelId} OR r.to_model_id=#{modelId}) "
            + "ORDER BY r.update_time DESC, r.id DESC")
    List<ModelingRelationEntity> listByModel(
            @Param("workspaceId") String workspaceId, @Param("modelId") String modelId);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_relation WHERE workspace_id=#{workspaceId} "
            + "AND (from_model_id=#{modelId} OR to_model_id=#{modelId})")
    long countByModel(@Param("workspaceId") String workspaceId, @Param("modelId") String modelId);

    @Insert("INSERT INTO yak_ops_modeling_relation(id,workspace_id,name,from_model_id,to_model_id,join_type,"
            + "description,revision,create_time,update_time,create_by,update_by) "
            + "VALUES(#{id},#{workspaceId},#{name},#{fromModelId},#{toModelId},#{joinType},#{description},"
            + "#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insert(ModelingRelationEntity row);

    @Update("UPDATE yak_ops_modeling_relation SET name=#{name},join_type=#{joinType},description=#{description},"
            + "revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(ModelingRelationEntity row);

    @Delete("DELETE FROM yak_ops_modeling_relation WHERE workspace_id=#{workspaceId} AND id=#{id} "
            + "AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select("SELECT id,workspace_id,relation_id,ordinal,from_field_code,to_field_code,"
            + "create_time,update_time,create_by,update_by "
            + "FROM yak_ops_modeling_relation_column WHERE relation_id=#{relationId} ORDER BY ordinal,id")
    List<ModelingRelationColumnEntity> columns(@Param("relationId") String relationId);

    @Insert("INSERT INTO yak_ops_modeling_relation_column(id,workspace_id,relation_id,ordinal,from_field_code,"
            + "to_field_code,revision,create_time,update_time,create_by,update_by) "
            + "VALUES(#{id},#{workspaceId},#{relationId},#{ordinal},#{fromFieldCode},#{toFieldCode},"
            + "#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    int insertColumn(ModelingRelationColumnEntity row);

    @Delete("DELETE FROM yak_ops_modeling_relation_column WHERE relation_id=#{relationId}")
    int deleteColumns(@Param("relationId") String relationId);

    /**
     * Lists every model that owns at least one unresolvable or unstandardized relation key. A model
     * owning several such relations appears once per relation; the rule deduplicates by model id.
     */
    @Select("SELECT r.id AS relation_id, m.id AS model_id, m.cn_name AS model_name "
            + "FROM yak_ops_modeling_relation r "
            + "JOIN yak_ops_modeling_relation_column rc ON rc.relation_id = r.id "
            + "JOIN yak_ops_modeling_model m ON m.workspace_id = r.workspace_id AND m.id = r.from_model_id "
            + "LEFT JOIN yak_ops_modeling_model_field ff "
            + "ON ff.model_id = r.from_model_id AND ff.en_code = rc.from_field_code "
            + "WHERE r.workspace_id = #{workspaceId} AND (ff.id IS NULL OR ff.standard_id IS NULL) "
            + "UNION "
            + "SELECT r.id AS relation_id, m.id AS model_id, m.cn_name AS model_name "
            + "FROM yak_ops_modeling_relation r "
            + "JOIN yak_ops_modeling_relation_column rc ON rc.relation_id = r.id "
            + "JOIN yak_ops_modeling_model m ON m.workspace_id = r.workspace_id AND m.id = r.to_model_id "
            + "LEFT JOIN yak_ops_modeling_model_field tf "
            + "ON tf.model_id = r.to_model_id AND tf.en_code = rc.to_field_code "
            + "WHERE r.workspace_id = #{workspaceId} AND (tf.id IS NULL OR tf.standard_id IS NULL)")
    List<RelationKeyScanRow> scanUnstandardizedKeys(@Param("workspaceId") String workspaceId);
}
