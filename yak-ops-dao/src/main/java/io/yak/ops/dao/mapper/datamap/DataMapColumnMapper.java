package io.yak.ops.dao.mapper.datamap;

import io.yak.ops.dao.entity.datamap.DataMapColumnEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Parameter-bound statements for current-state asset columns. */
@Mapper
public interface DataMapColumnMapper {

    String COLUMNS = "id,workspace_id,table_id,ordinal,name,type_name,jdbc_type,size,scale,nullable,primary_key,"
            + "primary_key_position,remarks";

    @Insert("INSERT INTO yak_ops_data_map_column (" + COLUMNS + ") VALUES "
            + "(#{id},#{workspaceId},#{tableId},#{ordinal},#{name},#{typeName},#{jdbcType},#{size},#{scale},"
            + "#{nullable},#{primaryKey},#{primaryKeyPosition},#{remarks})")
    int insert(DataMapColumnEntity row);

    @Select("SELECT " + COLUMNS + " FROM yak_ops_data_map_column "
            + "WHERE workspace_id=#{workspaceId} AND table_id=#{tableId} ORDER BY ordinal,id")
    List<DataMapColumnEntity> listByTable(@Param("workspaceId") String workspaceId, @Param("tableId") String tableId);

    @Delete("DELETE FROM yak_ops_data_map_column WHERE workspace_id=#{workspaceId} AND table_id=#{tableId}")
    int deleteByTable(@Param("workspaceId") String workspaceId, @Param("tableId") String tableId);

    /**
     * Counts how many columns of the active assets carry a comment.
     *
     * <p>Missing assets are excluded by joining their table row: their columns are kept in storage
     * after the table disappears, and counting them would make the ratio drift with history
     * instead of describing the catalog an operator is looking at.
     *
     * @param workspaceId owning Workspace
     * @return column total and commented column count
     */
    @Select("SELECT COUNT(*) AS column_total,"
            + "COUNT(CASE WHEN c.remarks IS NOT NULL AND TRIM(c.remarks) <> '' THEN 1 END) AS described_columns "
            + "FROM yak_ops_data_map_column c "
            + "JOIN yak_ops_data_map_table t ON t.id=c.table_id AND t.workspace_id=c.workspace_id "
            + "WHERE c.workspace_id=#{workspaceId} AND t.status='ACTIVE'")
    DataMapColumnCoverageRow summarizeCoverage(@Param("workspaceId") String workspaceId);

    /** Column names that matched a keyword, so a result row can say why it matched. */
    @Select("SELECT name FROM yak_ops_data_map_column WHERE workspace_id=#{workspaceId} "
            + "AND table_id=#{tableId} AND (name LIKE #{pattern} ESCAPE '!' OR remarks LIKE #{pattern} ESCAPE '!') "
            + "ORDER BY ordinal,id")
    List<String> matchedNames(
            @Param("workspaceId") String workspaceId,
            @Param("tableId") String tableId,
            @Param("pattern") String pattern);

    /** Matching column names for one page of assets, fetched in a single statement. */
    @Select("<script>SELECT table_id,name FROM yak_ops_data_map_column WHERE workspace_id=#{workspaceId} "
            + "AND table_id IN <foreach collection=\"tableIds\" item=\"tableId\" open=\"(\" separator=\",\" "
            + "close=\")\">#{tableId}</foreach> "
            + "AND (name LIKE #{pattern} ESCAPE '!' OR remarks LIKE #{pattern} ESCAPE '!') "
            + "ORDER BY table_id,ordinal,id</script>")
    List<DataMapMatchedColumnRow> listMatchedNames(
            @Param("workspaceId") String workspaceId,
            @Param("tableIds") List<String> tableIds,
            @Param("pattern") String pattern);
}
