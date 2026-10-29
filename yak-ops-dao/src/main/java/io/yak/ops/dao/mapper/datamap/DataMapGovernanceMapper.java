package io.yak.ops.dao.mapper.datamap;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Read-only catalog projections the governance rules need; no write path exists here. */
@Mapper
public interface DataMapGovernanceMapper {

    @Select("SELECT COUNT(1) FROM yak_ops_data_map_crawl "
            + "WHERE workspace_id=#{workspaceId} AND status IN ('PENDING', 'RUNNING')")
    long countActiveCrawls(@Param("workspaceId") String workspaceId);

    /**
     * Keyset page of ACTIVE catalog tables.
     *
     * <p>{@code MISSING} assets are never returned: a dropped table cannot be repaired by a member,
     * so accusing it would be a permanent false positive.
     */
    @Select("SELECT id, CONCAT_WS('.', schema_name, name) AS display_name, description, remarks, owner_id "
            + "FROM yak_ops_data_map_table WHERE workspace_id=#{workspaceId} AND status='ACTIVE' "
            + "AND id > #{afterId} ORDER BY id LIMIT #{limit}")
    List<GovernanceTableRow> listActiveTables(
            @Param("workspaceId") String workspaceId, @Param("afterId") String afterId, @Param("limit") int limit);

    /** Eligible base of the DEV dimension: every ACTIVE catalog table of the Workspace. */
    @Select("SELECT COUNT(1) FROM yak_ops_data_map_table WHERE workspace_id=#{workspaceId} AND status='ACTIVE'")
    long countActiveTables(@Param("workspaceId") String workspaceId);
}
