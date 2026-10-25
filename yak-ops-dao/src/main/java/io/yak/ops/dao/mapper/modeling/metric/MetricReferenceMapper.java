package io.yak.ops.dao.mapper.modeling.metric;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Cross-module reference queries for the metric kinds.
 *
 * <p>Every referenceable kind answers from the tables that point at it, so a delete dialog can say
 * which metrics and model fields depend on a record instead of only refusing. Reads are on demand:
 * a page of metrics would otherwise need one aggregate per row.
 */
@Mapper
public interface MetricReferenceMapper {
    /** Derived metrics built on one atomic metric. */
    @Select("SELECT 'DERIVED_METRIC' AS kind,d.id AS id,d.cn_name AS name,d.en_code AS path "
            + "FROM yak_ops_modeling_metric_derived d "
            + "WHERE d.workspace_id=#{workspaceId} AND d.atomic_metric_id=#{id} ORDER BY d.en_code")
    List<ReferenceRow> derivedByAtomic(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Derived metrics whose composition uses one modifier. */
    @Select("SELECT 'DERIVED_METRIC' AS kind,d.id AS id,d.cn_name AS name,d.en_code AS path "
            + "FROM yak_ops_modeling_metric_derived_modifier l "
            + "JOIN yak_ops_modeling_metric_derived d ON d.workspace_id=l.workspace_id AND d.id=l.derived_id "
            + "WHERE l.workspace_id=#{workspaceId} AND l.modifier_id=#{id} ORDER BY d.en_code")
    List<ReferenceRow> derivedByModifier(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Derived metrics whose composition uses one time period. */
    @Select("SELECT 'DERIVED_METRIC' AS kind,d.id AS id,d.cn_name AS name,d.en_code AS path "
            + "FROM yak_ops_modeling_metric_derived d "
            + "WHERE d.workspace_id=#{workspaceId} AND d.period_id=#{id} ORDER BY d.en_code")
    List<ReferenceRow> derivedByPeriod(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Composite metrics whose expression references one derived metric. */
    @Select("SELECT 'COMPOSITE_METRIC' AS kind,c.id AS id,c.cn_name AS name,c.en_code AS path "
            + "FROM yak_ops_modeling_metric_composite_ref r "
            + "JOIN yak_ops_modeling_metric_composite c ON c.workspace_id=r.workspace_id AND c.id=r.composite_id "
            + "WHERE r.workspace_id=#{workspaceId} AND r.derived_metric_id=#{id} ORDER BY c.en_code")
    List<ReferenceRow> compositesByDerived(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Model fields that imported one derived metric, with their owning table path. */
    @Select("SELECT 'MODEL_FIELD' AS kind,m.id AS id,f.en_code AS name,"
            + "CONCAT(m.name,'.',f.en_code) AS path "
            + "FROM yak_ops_modeling_model_field f "
            + "JOIN yak_ops_modeling_model m ON m.workspace_id=f.workspace_id AND m.id=f.model_id "
            + "WHERE f.workspace_id=#{workspaceId} AND f.metric_id=#{id} ORDER BY m.name,f.en_code")
    List<ReferenceRow> modelFieldsByMetric(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** One row of a reference query before it is projected into the response contract. */
    @Getter
    @Setter
    class ReferenceRow {
        /** Stored kind name, converted by the repository. */
        private String kind;
        /** Identity needed to open the item; a model field reports its owning model. */
        private String id;
        /** Display name of the item. */
        private String name;
        /** Locator an operator can search for. */
        private String path;
    }
}
