package io.yak.ops.dao.mapper.modeling.standard;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Cross-module reference queries.
 *
 * <p>Every kind answers from the tables that point at it, so a delete dialog can say which tables
 * and fields depend on a record instead of only refusing. Reads are on demand: a page of fifty
 * standards would otherwise need fifty aggregates.
 */
@Mapper
public interface StandardReferenceMapper {
    /** Model fields that bind one field standard. */
    @Select("SELECT 'MODEL_FIELD' AS kind,m.id AS id,f.en_code AS name,"
            + "CONCAT(m.name,'.',f.en_code) AS path "
            + "FROM yak_ops_modeling_model_field f "
            + "JOIN yak_ops_modeling_model m ON m.workspace_id=f.workspace_id AND m.id=f.model_id "
            + "WHERE f.workspace_id=#{workspaceId} AND f.standard_id=#{id} ORDER BY m.name,f.en_code")
    List<ReferenceRow> modelFieldsByStandard(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Child field standards that specialize one parent standard. */
    @Select("SELECT 'FIELD_STANDARD' AS kind,s.id AS id,s.cn_name AS name,s.en_code AS path "
            + "FROM yak_ops_standard_field s "
            + "WHERE s.workspace_id=#{workspaceId} AND s.parent_standard_id=#{id} ORDER BY s.en_code")
    List<ReferenceRow> fieldStandardsByParent(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Field standards that reference one lookup table. */
    @Select("SELECT 'FIELD_STANDARD' AS kind,s.id AS id,s.cn_name AS name,s.en_code AS path "
            + "FROM yak_ops_standard_field s "
            + "WHERE s.workspace_id=#{workspaceId} AND s.lookup_id=#{id} ORDER BY s.en_code")
    List<ReferenceRow> fieldStandardsByLookup(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Model fields that bind one lookup table directly. */
    @Select("SELECT 'MODEL_FIELD' AS kind,m.id AS id,f.en_code AS name,"
            + "CONCAT(m.name,'.',f.en_code) AS path "
            + "FROM yak_ops_modeling_model_field f "
            + "JOIN yak_ops_modeling_model m ON m.workspace_id=f.workspace_id AND m.id=f.model_id "
            + "WHERE f.workspace_id=#{workspaceId} AND f.lookup_id=#{id} ORDER BY m.name,f.en_code")
    List<ReferenceRow> modelFieldsByLookup(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Field standards that reference one measurement unit. */
    @Select("SELECT 'FIELD_STANDARD' AS kind,s.id AS id,s.cn_name AS name,s.en_code AS path "
            + "FROM yak_ops_standard_field s "
            + "WHERE s.workspace_id=#{workspaceId} AND s.measure_unit_id=#{id} ORDER BY s.en_code")
    List<ReferenceRow> fieldStandardsByUnit(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Model fields that bind one measurement unit. */
    @Select("SELECT 'MODEL_FIELD' AS kind,m.id AS id,f.en_code AS name,"
            + "CONCAT(m.name,'.',f.en_code) AS path "
            + "FROM yak_ops_modeling_model_field f "
            + "JOIN yak_ops_modeling_model m ON m.workspace_id=f.workspace_id AND m.id=f.model_id "
            + "WHERE f.workspace_id=#{workspaceId} AND f.measure_unit_id=#{id} ORDER BY m.name,f.en_code")
    List<ReferenceRow> modelFieldsByUnit(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /** Models whose name was composed from one naming entry under an enabled layer rule. */
    @Select("SELECT 'MODEL' AS kind,m.id AS id,m.cn_name AS name,m.name AS path "
            + "FROM yak_ops_modeling_model m "
            + "WHERE m.workspace_id=#{workspaceId} AND m.naming_root_word_id=#{id} ORDER BY m.name")
    List<ReferenceRow> modelsByNamingRoot(@Param("workspaceId") String workspaceId, @Param("id") String id);

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
