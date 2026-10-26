package io.yak.ops.business.modeling.standard.impl;

import io.yak.ops.business.modeling.standard.StandardCatalog;
import io.yak.ops.business.modeling.standard.StandardNamingEntryService;
import io.yak.ops.common.bean.dto.modeling.standard.StandardNamingEntryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardNamingEntryUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardNamingEntryVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.standard.StandardNamingEntryEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardNamingEntryRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardQuery;
import io.yak.ops.dao.repository.modeling.standard.StandardReferenceRepository;
import jakarta.annotation.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Owns naming-dictionary writes; the dictionary is flat and Workspace-scoped. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class StandardNamingEntryServiceImpl implements StandardNamingEntryService {
    @Resource
    private StandardNamingEntryRepository entries;

    @Resource
    private StandardReferenceRepository references;

    @Resource
    private StandardCatalog catalog;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<StandardNamingEntryVO> page(
            String workspaceId, String actor, String keyword, int pageNo, int pageSize) {
        catalog.authorize(workspaceId, actor);
        long offset = StandardCatalog.offset(pageNo, pageSize);
        var query = new StandardQuery(null, null, StandardCatalog.keyword(keyword), offset, pageSize);
        long total = entries.count(workspaceId, query);
        return PageData.of(
                entries.page(workspaceId, query).stream()
                        .map(StandardNamingEntryServiceImpl::project)
                        .toList(),
                total,
                pageNo,
                pageSize);
    }

    @Override
    public StandardSavedVO create(String workspaceId, String actor, StandardNamingEntryInputDTO input) {
        catalog.write(workspaceId, actor);
        validate(input);
        var row = new StandardNamingEntryEntity();
        row.setWorkspaceId(workspaceId);
        copy(row, input);
        row.setRevision(1);
        row.initCreate(actor);
        try {
            entries.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(StandardErrorCode.DUPLICATE_CODE, failure);
        }
        return new StandardSavedVO(row.getId(), 1);
    }

    @Override
    public StandardSavedVO update(String workspaceId, String actor, String id, StandardNamingEntryUpdateDTO input) {
        catalog.write(workspaceId, actor);
        var row = require(workspaceId, id);
        if (input == null) throw StandardCatalog.invalid();
        checkRevision(row.getRevision(), input.getRevision());
        validate(input);
        copy(row, input);
        row.initUpdate(actor);
        try {
            if (!entries.update(row)) throw StandardCatalog.conflict();
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(StandardErrorCode.DUPLICATE_CODE, failure);
        }
        return new StandardSavedVO(id, Math.addExact(row.getRevision(), 1));
    }

    @Override
    public void delete(String workspaceId, String actor, String id, long revision) {
        catalog.write(workspaceId, actor);
        var row = require(workspaceId, id);
        checkRevision(row.getRevision(), revision);
        try {
            if (!entries.delete(workspaceId, id, revision)) throw StandardCatalog.conflict();
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessException(StandardErrorCode.REFERENCED, failure);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public boolean available(String workspaceId, String actor, String enCode, String excludeId) {
        catalog.authorize(workspaceId, actor);
        if (excludeId != null) require(workspaceId, excludeId);
        StandardCatalog.validCode(enCode);
        return !entries.occupied(workspaceId, enCode, excludeId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StandardReferencesVO references(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        require(workspaceId, id);
        return StandardReferencesVO.of(references.byNamingRoot(workspaceId, id));
    }

    private StandardNamingEntryEntity require(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw StandardCatalog.invalid();
        return entries.find(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private static void validate(StandardNamingEntryInputDTO input) {
        if (input == null) throw StandardCatalog.invalid();
        StandardCatalog.validCode(input.getEnCode());
        StandardCatalog.validName(input.getEnName());
        StandardCatalog.validName(input.getCnName());
        if (input.getDescription() != null && input.getDescription().strip().length() > 500)
            throw StandardCatalog.invalid();
    }

    private static void copy(StandardNamingEntryEntity row, StandardNamingEntryInputDTO input) {
        row.setEnCode(input.getEnCode());
        row.setEnName(input.getEnName().strip());
        row.setCnName(input.getCnName().strip());
        row.setDescription(StandardCatalog.optionalText(input.getDescription(), 500));
    }

    private static StandardNamingEntryVO project(StandardNamingEntryEntity row) {
        return new StandardNamingEntryVO(
                row.getId(),
                row.getEnCode(),
                row.getEnName(),
                row.getCnName(),
                row.getDescription(),
                row.getUpdateTime(),
                row.getRevision());
    }

    private static void checkRevision(long current, Long expected) {
        if (expected == null || expected < 1) throw StandardCatalog.invalid();
        if (current != expected) throw StandardCatalog.conflict();
    }
}
