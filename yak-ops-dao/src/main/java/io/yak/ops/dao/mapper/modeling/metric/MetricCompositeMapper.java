package io.yak.ops.dao.mapper.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricCompositeEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricCompositeRefEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Parameter-bound statements for Workspace composite metrics and their resolved references. */
@Mapper
public interface MetricCompositeMapper {
    @Select("SELECT * FROM yak_ops_modeling_metric_composite WHERE workspace_id=#{workspaceId} "
            + "AND (#{query.keyword} IS NULL OR cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!') "
            + "ORDER BY update_time DESC,id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<MetricCompositeEntity> page(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_metric_composite WHERE workspace_id=#{workspaceId} "
            + "AND (#{query.keyword} IS NULL OR cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!')")
    long count(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select("SELECT * FROM yak_ops_modeling_metric_composite WHERE workspace_id=#{workspaceId} AND id=#{id}")
    MetricCompositeEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Insert(
            "INSERT INTO yak_ops_modeling_metric_composite(id,workspace_id,en_code,cn_name,expression,owner_id,description,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{enCode},#{cnName},#{expression},#{ownerId},#{description},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insert(MetricCompositeEntity node);

    @Update(
            "UPDATE yak_ops_modeling_metric_composite SET en_code=#{enCode},cn_name=#{cnName},expression=#{expression},owner_id=#{ownerId},description=#{description},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(MetricCompositeEntity node);

    @Delete(
            "DELETE FROM yak_ops_modeling_metric_composite WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_modeling_metric_composite WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} "
                    + "AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);

    @Select("SELECT r.*,d.en_code AS derived_en_code,d.cn_name AS derived_cn_name "
            + "FROM yak_ops_modeling_metric_composite_ref r "
            + "JOIN yak_ops_modeling_metric_derived d ON d.workspace_id=r.workspace_id AND d.id=r.derived_metric_id "
            + "WHERE r.workspace_id=#{workspaceId} AND r.composite_id=#{compositeId} ORDER BY r.id")
    List<MetricCompositeRefEntity> references(
            @Param("workspaceId") String workspaceId, @Param("compositeId") String compositeId);

    @Select("<script>SELECT r.*,d.en_code AS derived_en_code,d.cn_name AS derived_cn_name "
            + "FROM yak_ops_modeling_metric_composite_ref r "
            + "JOIN yak_ops_modeling_metric_derived d ON d.workspace_id=r.workspace_id AND d.id=r.derived_metric_id "
            + "WHERE r.workspace_id=#{workspaceId} AND r.composite_id IN "
            + "<foreach collection='compositeIds' item='compositeId' open='(' separator=',' close=')'>#{compositeId}</foreach> "
            + "ORDER BY r.composite_id,r.id</script>")
    List<MetricCompositeRefEntity> referencesByComposite(
            @Param("workspaceId") String workspaceId, @Param("compositeIds") List<String> compositeIds);

    @Select("SELECT c.id,c.en_code,c.expression,c.revision FROM yak_ops_modeling_metric_composite c "
            + "JOIN yak_ops_modeling_metric_composite_ref r ON r.workspace_id=c.workspace_id AND r.composite_id=c.id "
            + "WHERE c.workspace_id=#{workspaceId} AND r.derived_metric_id=#{derivedId}")
    List<MetricCompositeEntity> citingDerived(
            @Param("workspaceId") String workspaceId, @Param("derivedId") String derivedId);

    @Update("UPDATE yak_ops_modeling_metric_composite SET expression=#{expression},revision=revision+1,"
            + "update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int rewriteExpression(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("revision") long revision,
            @Param("expression") String expression,
            @Param("updateTime") LocalDateTime updateTime,
            @Param("updateBy") String updateBy);

    @Insert(
            "INSERT INTO yak_ops_modeling_metric_composite_ref(id,workspace_id,composite_id,derived_metric_id) VALUES(#{id},#{workspaceId},#{compositeId},#{derivedMetricId})")
    void insertReference(MetricCompositeRefEntity link);

    @Delete(
            "DELETE FROM yak_ops_modeling_metric_composite_ref WHERE workspace_id=#{workspaceId} AND composite_id=#{compositeId}")
    int deleteReferences(@Param("workspaceId") String workspaceId, @Param("compositeId") String compositeId);
}
