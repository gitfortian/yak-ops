package io.yak.ops.dao.mapper.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedModifierEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for Workspace derived metrics and their ordered modifier links.
 *
 * <p>The composed code and name are stored columns; the unique key on them is what turns a parts
 * collision into a {@code DuplicateKeyException} the service converts to a metric error.
 */
@Mapper
public interface MetricDerivedMapper {
    String PROJECTION = "SELECT d.*,a.en_code AS atomic_en_code,a.cn_name AS atomic_cn_name,"
            + "p.en_code AS period_en_code,p.cn_name AS period_cn_name "
            + "FROM yak_ops_modeling_metric_derived d "
            + "JOIN yak_ops_modeling_metric_atomic a ON a.workspace_id=d.workspace_id AND a.id=d.atomic_metric_id "
            + "JOIN yak_ops_modeling_metric_time_period p ON p.workspace_id=d.workspace_id AND p.id=d.period_id ";

    @Select(PROJECTION + "WHERE d.workspace_id=#{workspaceId} "
            + "AND (#{query.processId} IS NULL OR a.process_id=#{query.processId}) "
            + "AND (#{query.keyword} IS NULL OR d.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(d.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!') "
            + "ORDER BY d.update_time DESC,d.id DESC LIMIT #{query.offset},#{query.pageSize}")
    List<MetricDerivedEntity> page(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_metric_derived d "
            + "JOIN yak_ops_modeling_metric_atomic a ON a.workspace_id=d.workspace_id AND a.id=d.atomic_metric_id "
            + "WHERE d.workspace_id=#{workspaceId} "
            + "AND (#{query.processId} IS NULL OR a.process_id=#{query.processId}) "
            + "AND (#{query.keyword} IS NULL OR d.cn_name LIKE #{query.keyword} ESCAPE '!' "
            + "OR CONVERT(d.en_code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE #{query.keyword} ESCAPE '!')")
    long count(@Param("workspaceId") String workspaceId, @Param("query") MetricQuery query);

    @Select(PROJECTION + "WHERE d.workspace_id=#{workspaceId} AND d.id=#{id}")
    MetricDerivedEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select(PROJECTION + "WHERE d.workspace_id=#{workspaceId} AND d.en_code=#{enCode}")
    MetricDerivedEntity findByCode(@Param("workspaceId") String workspaceId, @Param("enCode") String enCode);

    @Select(PROJECTION + "WHERE d.workspace_id=#{workspaceId} AND d.atomic_metric_id=#{atomicMetricId} ORDER BY d.id")
    List<MetricDerivedEntity> byAtomic(
            @Param("workspaceId") String workspaceId, @Param("atomicMetricId") String atomicMetricId);

    @Select(PROJECTION + "WHERE d.workspace_id=#{workspaceId} AND d.period_id=#{periodId} ORDER BY d.id")
    List<MetricDerivedEntity> byPeriod(@Param("workspaceId") String workspaceId, @Param("periodId") String periodId);

    @Select(PROJECTION + "WHERE d.workspace_id=#{workspaceId} AND EXISTS (SELECT 1 FROM "
            + "yak_ops_modeling_metric_derived_modifier l WHERE l.workspace_id=d.workspace_id "
            + "AND l.derived_id=d.id AND l.modifier_id=#{modifierId}) ORDER BY d.id")
    List<MetricDerivedEntity> byModifier(
            @Param("workspaceId") String workspaceId, @Param("modifierId") String modifierId);

    @Insert(
            "INSERT INTO yak_ops_modeling_metric_derived(id,workspace_id,en_code,cn_name,atomic_metric_id,period_id,owner_id,description,revision,create_time,update_time,create_by,update_by) VALUES(#{id},#{workspaceId},#{enCode},#{cnName},#{atomicMetricId},#{periodId},#{ownerId},#{description},#{revision},#{createTime},#{updateTime},#{createBy},#{updateBy})")
    void insert(MetricDerivedEntity node);

    @Update(
            "UPDATE yak_ops_modeling_metric_derived SET en_code=#{enCode},cn_name=#{cnName},atomic_metric_id=#{atomicMetricId},period_id=#{periodId},owner_id=#{ownerId},description=#{description},revision=revision+1,update_time=#{updateTime},update_by=#{updateBy} WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int update(MetricDerivedEntity node);

    @Delete(
            "DELETE FROM yak_ops_modeling_metric_derived WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int delete(@Param("workspaceId") String workspaceId, @Param("id") String id, @Param("revision") long revision);

    @Update("UPDATE yak_ops_modeling_metric_derived SET en_code=#{enCode},cn_name=#{cnName},revision=revision+1,"
            + "update_time=#{updateTime},update_by=#{updateBy} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND revision=#{revision}")
    int rename(MetricDerivedEntity node);

    @Select(
            "SELECT COUNT(*) FROM yak_ops_modeling_metric_derived WHERE workspace_id=#{workspaceId} AND en_code=#{enCode} "
                    + "AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    long occupied(
            @Param("workspaceId") String workspaceId,
            @Param("enCode") String enCode,
            @Param("excludeId") String excludeId);

    @Select("SELECT l.*,m.en_code AS modifier_en_code,m.cn_name AS modifier_cn_name "
            + "FROM yak_ops_modeling_metric_derived_modifier l "
            + "JOIN yak_ops_modeling_metric_modifier m ON m.workspace_id=l.workspace_id AND m.id=l.modifier_id "
            + "WHERE l.workspace_id=#{workspaceId} AND l.derived_id=#{derivedId} ORDER BY l.ordinal")
    List<MetricDerivedModifierEntity> modifierLinks(
            @Param("workspaceId") String workspaceId, @Param("derivedId") String derivedId);

    @Select("<script>SELECT l.*,m.en_code AS modifier_en_code,m.cn_name AS modifier_cn_name "
            + "FROM yak_ops_modeling_metric_derived_modifier l "
            + "JOIN yak_ops_modeling_metric_modifier m ON m.workspace_id=l.workspace_id AND m.id=l.modifier_id "
            + "WHERE l.workspace_id=#{workspaceId} AND l.derived_id IN "
            + "<foreach collection='derivedIds' item='derivedId' open='(' separator=',' close=')'>#{derivedId}</foreach> "
            + "ORDER BY l.derived_id,l.ordinal</script>")
    List<MetricDerivedModifierEntity> modifierLinksByDerived(
            @Param("workspaceId") String workspaceId, @Param("derivedIds") List<String> derivedIds);

    @Insert(
            "INSERT INTO yak_ops_modeling_metric_derived_modifier(id,workspace_id,derived_id,modifier_id,ordinal) VALUES(#{id},#{workspaceId},#{derivedId},#{modifierId},#{ordinal})")
    void insertModifierLink(MetricDerivedModifierEntity link);

    @Delete(
            "DELETE FROM yak_ops_modeling_metric_derived_modifier WHERE workspace_id=#{workspaceId} AND derived_id=#{derivedId}")
    int deleteModifierLinks(@Param("workspaceId") String workspaceId, @Param("derivedId") String derivedId);
}
