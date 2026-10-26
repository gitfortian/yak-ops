package io.yak.ops.business.modeling.standard.impl;

import io.yak.ops.business.modeling.standard.StandardCatalog;
import io.yak.ops.business.modeling.standard.StandardLookupService;
import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardLookupVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardLookupValueVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardKind;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardLookupEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardLookupValueEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardLookupRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardQuery;
import io.yak.ops.dao.repository.modeling.standard.StandardReferenceRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Owns lookup-table writes: one revision covers the table and its whole value list. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class StandardLookupServiceImpl implements StandardLookupService {
    private static final int MAX_VALUE_CODE = 64;

    /** Bounds one submitted value list; typical dictionaries stay under fifty rows. */
    private static final int MAX_VALUES = 1000;

    @Resource
    private StandardLookupRepository lookups;

    @Resource
    private StandardReferenceRepository references;

    @Resource
    private StandardCatalog catalog;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<StandardLookupVO> page(
            String workspaceId, String actor, String categoryId, String keyword, int pageNo, int pageSize) {
        catalog.authorize(workspaceId, actor);
        if (categoryId != null && keyword != null) throw StandardCatalog.invalid();
        long offset = StandardCatalog.offset(pageNo, pageSize);
        if (categoryId != null) catalog.category(workspaceId, categoryId);
        var query = new StandardQuery(categoryId, null, StandardCatalog.keyword(keyword), offset, pageSize);
        long total = lookups.count(workspaceId, query);
        var rows = lookups.page(workspaceId, query);
        var paths = catalog.categories(workspaceId);
        return PageData.of(rows.stream().map(row -> project(row, paths, null)).toList(), total, pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StandardLookupVO detail(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        var values = lookups.values(id);
        return project(row, catalog.categories(workspaceId), values);
    }

    @Override
    public StandardSavedVO create(String workspaceId, String actor, String categoryId, StandardLookupInputDTO input) {
        catalog.write(workspaceId, actor);
        StandardCatalog.requireKind(catalog.category(workspaceId, categoryId), StandardKind.LOOKUP);
        validate(input);
        var row = new StandardLookupEntity();
        row.setWorkspaceId(workspaceId);
        row.setCategoryId(categoryId);
        copy(row, input);
        row.setRevision(1);
        row.initCreate(actor);
        try {
            lookups.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(StandardErrorCode.DUPLICATE_CODE, failure);
        }
        replaceValues(workspaceId, row.getId(), input);
        return new StandardSavedVO(row.getId(), 1);
    }

    @Override
    public StandardSavedVO update(String workspaceId, String actor, String id, StandardLookupUpdateDTO input) {
        catalog.write(workspaceId, actor);
        var row = require(workspaceId, id);
        if (input == null) throw StandardCatalog.invalid();
        checkRevision(row.getRevision(), input.getRevision());
        StandardCatalog.requireKind(catalog.category(workspaceId, row.getCategoryId()), StandardKind.LOOKUP);
        validate(input);
        copy(row, input);
        row.initUpdate(actor);
        try {
            if (!lookups.update(row)) throw StandardCatalog.conflict();
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(StandardErrorCode.DUPLICATE_CODE, failure);
        }
        replaceValues(workspaceId, id, input);
        return new StandardSavedVO(id, Math.addExact(row.getRevision(), 1));
    }

    /**
     * Replaces the whole value list, translating a unique-key collision into the contract error.
     *
     * <p>The in-memory duplicate check compares folded codes, but the column's collation is the
     * authority on what counts as the same value (it also folds accents). A collision that only the
     * database sees must still answer {@code INVALID_LOOKUP_VALUE} rather than escape as a driver
     * exception and reach the client as a generic server error.
     */
    private void replaceValues(String workspaceId, String lookupId, StandardLookupInputDTO input) {
        try {
            lookups.replaceValues(lookupId, values(workspaceId, lookupId, input));
        } catch (DuplicateKeyException collision) {
            throw new BusinessException(StandardErrorCode.INVALID_LOOKUP_VALUE, collision);
        }
    }

    @Override
    public void delete(String workspaceId, String actor, String id, long revision) {
        catalog.write(workspaceId, actor);
        var row = require(workspaceId, id);
        checkRevision(row.getRevision(), revision);
        try {
            if (!lookups.delete(workspaceId, id, revision)) throw StandardCatalog.conflict();
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
        return !lookups.occupied(workspaceId, enCode, excludeId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StandardReferencesVO references(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        require(workspaceId, id);
        return StandardReferencesVO.of(references.byLookup(workspaceId, id));
    }

    private StandardLookupEntity require(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw StandardCatalog.invalid();
        return lookups.find(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private static void validate(StandardLookupInputDTO input) {
        if (input == null) throw StandardCatalog.invalid();
        StandardCatalog.validCode(input.getEnCode());
        StandardCatalog.validName(input.getCnName());
        StandardCatalog.validName(input.getEnName());
        if (input.getDescription() != null && input.getDescription().strip().length() > 500)
            throw StandardCatalog.invalid();
        if (input.getValues() == null) throw new BusinessException(StandardErrorCode.INVALID_LOOKUP_VALUE);
    }

    /**
     * Validates and orders the submitted values.
     *
     * <p>The list is replaced as a whole, so an empty or duplicated value code rejects the whole
     * list instead of leaving a partial one behind.
     */
    private static List<StandardLookupValueEntity> values(
            String workspaceId, String lookupId, StandardLookupInputDTO input) {
        var submitted = input.getValues();
        if (submitted.size() > MAX_VALUES) throw new BusinessException(StandardErrorCode.INVALID_LOOKUP_VALUE);
        var seen = new HashSet<String>();
        var rows = new ArrayList<StandardLookupValueEntity>(submitted.size());
        for (var index = 0; index < submitted.size(); index++) {
            var value = submitted.get(index);
            if (value == null) throw new BusinessException(StandardErrorCode.INVALID_LOOKUP_VALUE);
            var code =
                    value.getValueCode() == null ? null : value.getValueCode().strip();
            // Codes that differ only in case are the same value to the column's collation, so the
            // duplicate check has to fold them the same way before the database does it for us.
            if (code == null
                    || code.isEmpty()
                    || code.length() > MAX_VALUE_CODE
                    || !seen.add(code.toLowerCase(Locale.ROOT)))
                throw new BusinessException(StandardErrorCode.INVALID_LOOKUP_VALUE);
            var row = new StandardLookupValueEntity();
            row.setId(IdUtils.nextId());
            row.setWorkspaceId(workspaceId);
            row.setLookupId(lookupId);
            row.setOrdinal(index + 1);
            row.setValueCode(code);
            row.setCnName(StandardCatalog.validName(value.getCnName()));
            // The editor collects only the code, the Chinese name and the meaning of a value, so a
            // missing English name is the normal case rather than an invalid request. The column is
            // NOT NULL and the code is unique inside its table, so it is the safe stand-in.
            var enName = value.getEnName();
            row.setEnName(StandardCatalog.validName(enName == null || enName.isBlank() ? code : enName));
            row.setMeaning(StandardCatalog.optionalText(value.getMeaning(), 500));
            rows.add(row);
        }
        return rows;
    }

    private static void copy(StandardLookupEntity row, StandardLookupInputDTO input) {
        row.setEnCode(input.getEnCode());
        row.setCnName(input.getCnName().strip());
        row.setEnName(input.getEnName().strip());
        row.setDescription(StandardCatalog.optionalText(input.getDescription(), 500));
    }

    private static StandardLookupVO project(
            StandardLookupEntity row, Map<String, CategoryEntity> categories, List<StandardLookupValueEntity> values) {
        var path = StandardCatalog.path(row.getCategoryId(), categories);
        return new StandardLookupVO(
                row.getId(),
                row.getCategoryId(),
                String.join(" / ", path),
                row.getEnCode(),
                row.getCnName(),
                row.getEnName(),
                row.getDescription(),
                values == null ? row.getValueCount() : values.size(),
                values == null
                        ? null
                        : values.stream()
                                .map(value -> new StandardLookupValueVO(
                                        value.getOrdinal(),
                                        value.getValueCode(),
                                        value.getCnName(),
                                        value.getEnName(),
                                        value.getMeaning()))
                                .toList(),
                row.getUpdateTime(),
                row.getRevision());
    }

    private static void checkRevision(long current, Long expected) {
        if (expected == null || expected < 1) throw StandardCatalog.invalid();
        if (current != expected) throw StandardCatalog.conflict();
    }
}
