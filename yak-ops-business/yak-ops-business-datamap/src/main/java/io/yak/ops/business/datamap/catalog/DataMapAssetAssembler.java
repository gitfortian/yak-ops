package io.yak.ops.business.datamap.catalog;

import io.yak.ops.common.bean.vo.datamap.DataMapColumnVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableSummaryVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTagRefVO;
import io.yak.ops.dao.entity.datamap.DataMapColumnEntity;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.mapper.datamap.DataMapColumnMapper;
import io.yak.ops.dao.mapper.datamap.DataMapMatchedColumnRow;
import io.yak.ops.dao.mapper.datamap.DataMapPersonalMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import io.yak.ops.dao.mapper.tag.TableTagRefRow;
import io.yak.ops.dao.mapper.tag.TagMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Projects persisted assets into the catalog's read shapes. */
@Component
public class DataMapAssetAssembler {

    @Resource
    private DataMapColumnMapper columnMapper;

    @Resource
    private DataMapTableMapper tableMapper;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private DataMapPersonalMapper personalMapper;

    /**
     * Projects assets in the caller-supplied identity order, keeping that order in the result.
     *
     * @param workspaceId current Workspace
     * @param tableIds identities in display order
     * @param keywordPattern escaped keyword pattern, or null when no keyword was given
     * @return summaries in {@code tableIds} order, skipping identities that no longer exist
     */
    public List<DataMapTableSummaryVO> summariesInOrder(
            String workspaceId, List<String> tableIds, String keywordPattern) {
        if (tableIds.isEmpty()) return List.of();
        Map<String, DataMapTableEntity> byId = new HashMap<>();
        for (DataMapTableEntity row : tableMapper.findByIds(workspaceId, tableIds)) {
            byId.put(row.getId(), row);
        }
        List<DataMapTableEntity> ordered = new ArrayList<>(tableIds.size());
        for (String tableId : tableIds) {
            DataMapTableEntity row = byId.get(tableId);
            if (row != null) ordered.add(row);
        }
        return summaries(workspaceId, ordered, keywordPattern);
    }

    /**
     * Projects one page of assets, fetching tag values and matched columns in single statements.
     *
     * @param workspaceId current Workspace
     * @param rows assets of the page
     * @param keywordPattern escaped keyword pattern, or null when no keyword was given
     * @return summaries in the order of {@code rows}
     */
    public List<DataMapTableSummaryVO> summaries(
            String workspaceId, List<DataMapTableEntity> rows, String keywordPattern) {
        if (rows.isEmpty()) return List.of();
        List<String> tableIds = rows.stream().map(DataMapTableEntity::getId).toList();
        Map<String, List<DataMapTagRefVO>> tags = loadTagRefs(workspaceId, tableIds);
        Map<String, List<String>> matched = matchedColumns(workspaceId, tableIds, keywordPattern);
        List<DataMapTableSummaryVO> result = new ArrayList<>(rows.size());
        for (DataMapTableEntity row : rows) {
            DataMapTableSummaryVO summary = new DataMapTableSummaryVO();
            copyObserved(row, summary);
            summary.setTags(tags.getOrDefault(row.getId(), List.of()));
            summary.setMatchedColumns(matched.getOrDefault(row.getId(), List.of()));
            result.add(summary);
        }
        return result;
    }

    /**
     * Projects one asset with its columns, tags and the caller's favourite state.
     *
     * @param workspaceId current Workspace
     * @param row persisted asset
     * @param userId authenticated caller
     * @return the detail shape
     */
    public DataMapTableDetailVO detail(String workspaceId, DataMapTableEntity row, String userId) {
        DataMapTableDetailVO detail = new DataMapTableDetailVO();
        copyObserved(row, detail);
        detail.setFavorited(personalMapper.favoriteExists(workspaceId, userId, row.getId()) > 0);
        detail.setTags(loadTagRefs(workspaceId, List.of(row.getId())).getOrDefault(row.getId(), List.of()));
        List<DataMapColumnEntity> columns = columnMapper.listByTable(workspaceId, row.getId());
        List<DataMapColumnVO> projected = new ArrayList<>(columns.size());
        for (DataMapColumnEntity column : columns) {
            DataMapColumnVO vo = new DataMapColumnVO();
            vo.setOrdinal(column.getOrdinal());
            vo.setName(column.getName());
            vo.setTypeName(column.getTypeName());
            vo.setJdbcType(column.getJdbcType());
            vo.setSize(column.getSize());
            vo.setScale(column.getScale());
            vo.setNullable(column.getNullable());
            vo.setPrimaryKey(column.getPrimaryKey());
            vo.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
            vo.setRemarks(column.getRemarks());
            projected.add(vo);
        }
        detail.setColumns(projected);
        return detail;
    }

    private Map<String, List<String>> matchedColumns(String workspaceId, List<String> tableIds, String keywordPattern) {
        if (keywordPattern == null) return Map.of();
        Map<String, List<String>> matched = new LinkedHashMap<>();
        for (DataMapMatchedColumnRow row : columnMapper.listMatchedNames(workspaceId, tableIds, keywordPattern)) {
            matched.computeIfAbsent(row.getTableId(), key -> new ArrayList<>()).add(row.getName());
        }
        return matched;
    }

    /**
     * Projects the tag values of the listed assets, keeping the key then value order of the query.
     *
     * <p>Each row carries its key name, so the catalog list and the asset detail both render
     * {@code key = value} without a second read of the dictionary.
     */
    private Map<String, List<DataMapTagRefVO>> loadTagRefs(String workspaceId, List<String> tableIds) {
        Map<String, List<DataMapTagRefVO>> result = new LinkedHashMap<>();
        for (TableTagRefRow row : tagMapper.listTagRefs(workspaceId, tableIds)) {
            result.computeIfAbsent(row.getTableId(), key -> new ArrayList<>())
                    .add(new DataMapTagRefVO(row.getKeyId(), row.getKeyName(), row.getValueId(), row.getValueName()));
        }
        return result;
    }

    private static void copyObserved(DataMapTableEntity row, DataMapTableSummaryVO target) {
        target.setId(row.getId());
        target.setDatasourceId(row.getDatasourceId());
        target.setDatasourceName(row.getDatasourceName());
        target.setDatabase(row.getDatabaseName());
        target.setSchema(row.getSchemaName());
        target.setName(row.getName());
        target.setTableType(row.getTableType());
        target.setRemarks(row.getRemarks());
        target.setStatus(row.getStatus());
        target.setDescription(row.getDescription());
        target.setOwnerId(row.getOwnerId());
        target.setDomainId(row.getDomainId());
        target.setFirstSeenTime(row.getFirstSeenTime());
        target.setLastSeenTime(row.getLastSeenTime());
    }

    private static void copyObserved(DataMapTableEntity row, DataMapTableDetailVO target) {
        target.setId(row.getId());
        target.setDatasourceId(row.getDatasourceId());
        target.setDatasourceName(row.getDatasourceName());
        target.setDatabase(row.getDatabaseName());
        target.setSchema(row.getSchemaName());
        target.setName(row.getName());
        target.setTableType(row.getTableType());
        target.setRemarks(row.getRemarks());
        target.setStatus(row.getStatus());
        target.setDescription(row.getDescription());
        target.setOwnerId(row.getOwnerId());
        target.setDomainId(row.getDomainId());
        target.setFirstSeenTime(row.getFirstSeenTime());
        target.setLastSeenTime(row.getLastSeenTime());
    }
}
