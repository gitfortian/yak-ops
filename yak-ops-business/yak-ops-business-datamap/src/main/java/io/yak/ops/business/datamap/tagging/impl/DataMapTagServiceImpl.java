package io.yak.ops.business.datamap.tagging.impl;

import io.yak.ops.business.datamap.tagging.DataMapTagService;
import io.yak.ops.common.bean.dto.datamap.DataMapTagInputDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapTagVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datamap.DataMapTableTagEntity;
import io.yak.ops.dao.entity.datamap.DataMapTagEntity;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTagMapper;
import jakarta.annotation.Resource;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default tag operations; uniqueness is enforced by the Workspace name index. */
@Service
public class DataMapTagServiceImpl implements DataMapTagService {

    @Resource
    private DataMapTagMapper tagMapper;

    @Resource
    private DataMapTableMapper tableMapper;

    @Override
    public List<DataMapTagVO> list(String workspaceId) {
        return tagMapper.listAll(workspaceId).stream()
                .map(tag -> new DataMapTagVO(tag.getId(), tag.getName()))
                .toList();
    }

    @Override
    public DataMapTagVO create(String workspaceId, String actor, DataMapTagInputDTO input) {
        String name = requireName(input.getName());
        if (tagMapper.findByName(workspaceId, name) != null)
            throw new BusinessException(DataMapErrorCode.DUPLICATE_TAG);
        DataMapTagEntity row = new DataMapTagEntity();
        row.setId(IdUtils.nextId());
        row.setWorkspaceId(workspaceId);
        row.setName(name);
        row.setCreateTime(DateUtils.now());
        row.setUpdateTime(row.getCreateTime());
        row.setCreateBy(StringUtils.trimToNull(actor) == null ? CommonConstants.SYSTEM_USER : actor);
        row.setUpdateBy(row.getCreateBy());
        try {
            tagMapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(DataMapErrorCode.DUPLICATE_TAG);
        }
        return new DataMapTagVO(row.getId(), row.getName());
    }

    @Override
    public DataMapTagVO rename(String workspaceId, String actor, String tagId, DataMapTagInputDTO input) {
        requireTag(workspaceId, tagId);
        String name = requireName(input.getName());
        DataMapTagEntity existing = tagMapper.findByName(workspaceId, name);
        if (existing != null && !existing.getId().equals(tagId)) {
            throw new BusinessException(DataMapErrorCode.DUPLICATE_TAG);
        }
        try {
            tagMapper.rename(workspaceId, tagId, name, DateUtils.now(), actor);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(DataMapErrorCode.DUPLICATE_TAG);
        }
        return new DataMapTagVO(tagId, name);
    }

    @Override
    public void delete(String workspaceId, String tagId) {
        requireTag(workspaceId, tagId);
        tagMapper.delete(workspaceId, tagId);
    }

    @Override
    @Transactional
    public void assign(String workspaceId, String actor, String tableId, List<String> tagIds) {
        if (tableMapper.findById(workspaceId, tableId) == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(tagIds);
        for (String tagId : unique) requireTag(workspaceId, tagId);
        tagMapper.deleteAssignments(workspaceId, tableId);
        for (String tagId : unique) {
            DataMapTableTagEntity row = new DataMapTableTagEntity();
            row.setWorkspaceId(workspaceId);
            row.setTableId(tableId);
            row.setTagId(tagId);
            row.setCreateTime(DateUtils.now());
            row.setCreateBy(actor);
            tagMapper.insertAssignment(row);
        }
    }

    private DataMapTagEntity requireTag(String workspaceId, String tagId) {
        DataMapTagEntity tag = tagMapper.findById(workspaceId, tagId);
        if (tag == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return tag;
    }

    private static String requireName(String value) {
        String name = StringUtils.trimToNull(value);
        if (name == null || name.length() > 64) throw new BusinessException(DataMapErrorCode.INVALID_TAG);
        return name;
    }
}
