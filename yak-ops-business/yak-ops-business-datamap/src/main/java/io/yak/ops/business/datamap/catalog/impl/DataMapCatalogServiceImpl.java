package io.yak.ops.business.datamap.catalog.impl;

import io.yak.ops.business.datamap.catalog.DataMapAssetAssembler;
import io.yak.ops.business.datamap.catalog.DataMapCatalogService;
import io.yak.ops.business.datamap.support.DataMapSearchRateLimiter;
import io.yak.ops.business.datamap.support.DataMapSearchText;
import io.yak.ops.business.modeling.planning.PlanningService;
import io.yak.ops.common.bean.dto.datamap.DataMapCuratedUpdateDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapTableQueryDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapFacetSummaryVO;
import io.yak.ops.common.bean.vo.datamap.DataMapOverviewFacetsVO;
import io.yak.ops.common.bean.vo.datamap.DataMapOverviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableChangeVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableSummaryVO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.PlanningNodeVO;
import io.yak.ops.common.bean.vo.modeling.PlanningTreeVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.entity.tag.TagValueEntity;
import io.yak.ops.dao.mapper.datamap.DataMapFacetCountRow;
import io.yak.ops.dao.mapper.datamap.DataMapPersonalMapper;
import io.yak.ops.dao.mapper.datamap.DataMapSearchCriteria;
import io.yak.ops.dao.mapper.datamap.DataMapTableChangeMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import io.yak.ops.dao.mapper.datamap.DataMapViewTotalRow;
import io.yak.ops.dao.mapper.tag.TagMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Default catalog reader and curation writer.
 *
 * <p>The two writers stay disjoint: this service only ever writes {@code description},
 * {@code owner_id}, {@code domain_id} and the audit columns, while the crawler writes the observed
 * columns. No read here merges the two.
 */
@Service
public class DataMapCatalogServiceImpl implements DataMapCatalogService {

    private static final int HOT_TABLE_LIMIT = 10;
    private static final int CHANGE_LIMIT = 100;

    @Resource
    private DataMapTableMapper tableMapper;

    @Resource
    private DataMapTableChangeMapper changeMapper;

    @Resource
    private DataMapPersonalMapper personalMapper;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private DataMapAssetAssembler assembler;

    @Resource
    private DataMapSearchRateLimiter rateLimiter;

    @Resource
    private PlanningService planningService;

    @Override
    public DataMapOverviewVO overview(String workspaceId, String actor) {
        DataMapOverviewVO overview = new DataMapOverviewVO();
        overview.setAssetCount(tableMapper.countActive(workspaceId));
        overview.setManagedDatasourceCount(tableMapper.countDatasources(workspaceId));
        overview.setNewTablesLastSevenDays(
                tableMapper.countFirstSeenSince(workspaceId, DateUtils.now().minusDays(7)));
        overview.setUnclassifiedCount(tableMapper.countUnclassified(workspaceId));
        overview.setDescribedCount(tableMapper.countDescribed(workspaceId));
        overview.setOwnedCount(tableMapper.countOwned(workspaceId));
        overview.setHotTables(hotTables(workspaceId));
        return overview;
    }

    @Override
    public DataMapOverviewFacetsVO overviewFacets(String workspaceId) {
        DataMapOverviewFacetsVO facets = new DataMapOverviewFacetsVO();
        facets.setDomains(facetSummaries(tableMapper.selectDomainFacets(workspaceId)));
        facets.setDatasources(facetSummaries(tableMapper.selectDatasourceFacets(workspaceId)));
        return facets;
    }

    private List<DataMapFacetSummaryVO> facetSummaries(List<DataMapFacetCountRow> rows) {
        return rows.stream()
                .map(row -> new DataMapFacetSummaryVO(
                        row.getFacetKey(), row.getAssetCount(), row.getDescribedCount(), row.getOwnedCount()))
                .toList();
    }

    @Override
    public PagingData<DataMapTableSummaryVO> search(String workspaceId, String actor, DataMapTableQueryDTO query) {
        rateLimiter.acquire(workspaceId, actor);
        int pageNo = query.getPageNo() == null ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        String pattern = DataMapSearchText.containsPattern(StringUtils.trimToNull(query.getKeyword()));
        List<List<String>> tagValueGroups = tagValueGroups(workspaceId, query.getTagValueIds());
        DataMapSearchCriteria criteria = criteria(workspaceId, query, pattern, tagValueGroups, pageNo, pageSize);
        long total = tableMapper.count(criteria);
        List<DataMapTableEntity> rows = total == 0 ? List.of() : tableMapper.selectPage(criteria);
        return PagingData.from(PageData.of(assembler.summaries(workspaceId, rows, pattern), total, pageNo, pageSize));
    }

    @Override
    public DataMapTableDetailVO detail(String workspaceId, String actor, String tableId) {
        return assembler.detail(workspaceId, requireAsset(workspaceId, tableId), actor);
    }

    @Override
    public DataMapTableDetailVO curate(
            String workspaceId, String actor, String tableId, DataMapCuratedUpdateDTO input) {
        requireAsset(workspaceId, tableId);
        String ownerId = StringUtils.trimToNull(input.getOwnerId());
        if (ownerId != null) {
            List<OwnerOptionVO> owners = planningService.owners(workspaceId, actor);
            if (owners.stream().noneMatch(owner -> ownerId.equals(owner.userId()))) {
                throw new BusinessException(DataMapErrorCode.INVALID_OWNER);
            }
        }
        String domainId = StringUtils.trimToNull(input.getDomainId());
        if (domainId != null && !domainIds(workspaceId, actor).contains(domainId)) {
            throw new BusinessException(DataMapErrorCode.INVALID_DOMAIN);
        }
        tableMapper.updateCurated(
                workspaceId, tableId, StringUtils.trimToNull(input.getDescription()), ownerId, domainId);
        return detail(workspaceId, actor, tableId);
    }

    @Override
    public List<DataMapTableChangeVO> changes(String workspaceId, String actor, String tableId) {
        requireAsset(workspaceId, tableId);
        return changeMapper.listRecent(workspaceId, tableId, CHANGE_LIMIT).stream()
                .map(change ->
                        new DataMapTableChangeVO(change.getChangeType(), change.getDetail(), change.getCreatedTime()))
                .toList();
    }

    private List<DataMapTableSummaryVO> hotTables(String workspaceId) {
        List<DataMapViewTotalRow> totals = personalMapper.hotTables(workspaceId, HOT_TABLE_LIMIT);
        if (totals.isEmpty()) return List.of();
        List<DataMapTableSummaryVO> summaries = assembler.summariesInOrder(
                workspaceId,
                totals.stream().map(DataMapViewTotalRow::getTableId).toList(),
                null);
        Map<String, Long> counts = totals.stream()
                .collect(Collectors.toMap(DataMapViewTotalRow::getTableId, DataMapViewTotalRow::getViewCount));
        summaries.forEach(summary -> summary.setViewCount(counts.get(summary.getId())));
        return summaries;
    }

    private DataMapTableEntity requireAsset(String workspaceId, String tableId) {
        DataMapTableEntity row = tableMapper.findById(workspaceId, tableId);
        if (row == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return row;
    }

    private Set<String> domainIds(String workspaceId, String actor) {
        PlanningTreeVO tree = planningService.tree(workspaceId, actor);
        return tree.categories().stream()
                .flatMap(category -> category.getChildren().stream())
                .filter(node -> node.getKind() == NodeKind.DOMAIN)
                .map(PlanningNodeVO::getId)
                .collect(Collectors.toSet());
    }

    private static DataMapSearchCriteria criteria(
            String workspaceId,
            DataMapTableQueryDTO query,
            String pattern,
            List<List<String>> tagValueGroups,
            int pageNo,
            int pageSize) {
        DataMapSearchCriteria criteria = new DataMapSearchCriteria();
        criteria.setWorkspaceId(workspaceId);
        criteria.setKeywordPattern(pattern);
        criteria.setDatasourceId(StringUtils.trimToNull(query.getDatasourceId()));
        criteria.setDatabaseName(StringUtils.trimToNull(query.getDatabase()));
        criteria.setSchemaName(StringUtils.trimToNull(query.getSchema()));
        criteria.setDomainId(StringUtils.trimToNull(query.getDomainId()));
        criteria.setOwnerId(StringUtils.trimToNull(query.getOwnerId()));
        criteria.setUnclassified(Boolean.TRUE.equals(query.getUnclassified()));
        criteria.setTagValueGroups(tagValueGroups);
        criteria.setIncludeMissing(Boolean.TRUE.equals(query.getIncludeMissing()));
        criteria.setIds(List.of());
        criteria.setLimit(pageSize);
        criteria.setOffset((pageNo - 1) * pageSize);
        return criteria;
    }

    /**
     * Groups the requested tag values by their key.
     *
     * <p>The catalog filter ANDs keys and ORs the values of one key, so the criteria carries one
     * group per key and the SQL builds one {@code EXISTS} clause per group. An unknown value id is
     * rejected rather than silently dropped from the filter.
     */
    private List<List<String>> tagValueGroups(String workspaceId, List<String> tagValueIds) {
        if (tagValueIds == null || tagValueIds.isEmpty()) return List.of();
        List<String> requested = new LinkedHashSet<>(tagValueIds).stream().toList();
        List<TagValueEntity> values = tagMapper.findValuesByIds(workspaceId, requested);
        if (values.size() != requested.size()) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        for (TagValueEntity value : values) {
            byKey.computeIfAbsent(value.getKeyId(), key -> new ArrayList<>()).add(value.getId());
        }
        return List.copyOf(byKey.values());
    }
}
