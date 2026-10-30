package io.yak.ops.business.datamap.governance.impl;

import io.yak.ops.business.datamap.governance.DataMapGovernanceQuery;
import io.yak.ops.business.datamap.governance.GovernanceTableSnapshot;
import io.yak.ops.dao.mapper.datamap.DataMapGovernanceMapper;
import io.yak.ops.dao.mapper.datamap.GovernanceTableRow;
import java.util.List;
import org.springframework.stereotype.Service;

/** Reads the catalog projections governance needs without exposing its write model. */
@Service
public class DataMapGovernanceQueryImpl implements DataMapGovernanceQuery {

    private final DataMapGovernanceMapper mapper;

    public DataMapGovernanceQueryImpl(DataMapGovernanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean hasActiveCrawl(String workspaceId) {
        return mapper.countActiveCrawls(workspaceId) > 0;
    }

    @Override
    public long countActiveTables(String workspaceId) {
        return mapper.countActiveTables(workspaceId);
    }

    @Override
    public List<GovernanceTableSnapshot> activeTables(String workspaceId, String afterId, int limit) {
        String cursor = afterId == null ? "" : afterId;
        return mapper.listActiveTables(workspaceId, cursor, limit).stream()
                .map(DataMapGovernanceQueryImpl::toSnapshot)
                .toList();
    }

    private static GovernanceTableSnapshot toSnapshot(GovernanceTableRow row) {
        return new GovernanceTableSnapshot(
                row.getId(), row.getDisplayName(), row.getDescription(), row.getRemarks(), row.getOwnerId());
    }
}
