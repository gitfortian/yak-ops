package io.yak.ops.business.datamap.personal.impl;

import io.yak.ops.business.datamap.catalog.DataMapAssetAssembler;
import io.yak.ops.business.datamap.personal.DataMapPersonalService;
import io.yak.ops.common.bean.vo.datamap.DataMapTableSummaryVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.entity.datamap.DataMapViewEntity;
import io.yak.ops.dao.mapper.datamap.DataMapPersonalMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

/** Default per-member favourite and visit operations. */
@Service
public class DataMapPersonalServiceImpl implements DataMapPersonalService {

    @Resource
    private DataMapPersonalMapper personalMapper;

    @Resource
    private DataMapTableMapper tableMapper;

    @Resource
    private DataMapAssetAssembler assembler;

    @Override
    public PagingData<DataMapTableSummaryVO> favorites(String workspaceId, String userId, int pageNo, int pageSize) {
        long total = personalMapper.countFavorites(workspaceId, userId);
        if (total == 0) return PagingData.from(PageData.empty(pageNo, pageSize));
        List<String> ids = personalMapper.listFavoriteIds(workspaceId, userId, pageSize, (pageNo - 1) * pageSize);
        return PagingData.from(
                PageData.of(assembler.summariesInOrder(workspaceId, ids, null), total, pageNo, pageSize));
    }

    @Override
    public PagingData<DataMapTableSummaryVO> recent(String workspaceId, String userId, int pageNo, int pageSize) {
        long total = personalMapper.countRecent(workspaceId, userId);
        if (total == 0) return PagingData.from(PageData.empty(pageNo, pageSize));
        List<DataMapViewEntity> views =
                personalMapper.listRecentViews(workspaceId, userId, pageSize, (pageNo - 1) * pageSize);
        List<String> ids = views.stream().map(DataMapViewEntity::getTableId).toList();
        return PagingData.from(
                PageData.of(assembler.summariesInOrder(workspaceId, ids, null), total, pageNo, pageSize));
    }

    @Override
    public void addFavorite(String workspaceId, String userId, String tableId) {
        requireAsset(workspaceId, tableId);
        personalMapper.insertFavorite(workspaceId, userId, tableId, DateUtils.now());
    }

    @Override
    public void removeFavorite(String workspaceId, String userId, String tableId) {
        requireAsset(workspaceId, tableId);
        personalMapper.deleteFavorite(workspaceId, userId, tableId);
    }

    @Override
    public void recordView(String workspaceId, String userId, String tableId) {
        requireAsset(workspaceId, tableId);
        personalMapper.upsertView(workspaceId, userId, tableId, DateUtils.now());
    }

    private void requireAsset(String workspaceId, String tableId) {
        if (tableMapper.findById(workspaceId, tableId) == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
    }
}
