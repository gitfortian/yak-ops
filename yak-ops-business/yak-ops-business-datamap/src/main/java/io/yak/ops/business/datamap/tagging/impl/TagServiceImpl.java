package io.yak.ops.business.datamap.tagging.impl;

import io.yak.ops.business.datamap.tagging.TagService;
import io.yak.ops.common.bean.dto.tag.TagBindingBatchDTO;
import io.yak.ops.common.bean.dto.tag.TagKeyRenameDTO;
import io.yak.ops.common.bean.dto.tag.TagKeySaveDTO;
import io.yak.ops.common.bean.dto.tag.TagValueSaveDTO;
import io.yak.ops.common.bean.vo.tag.TagKeyVO;
import io.yak.ops.common.bean.vo.tag.TagValueVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.tag.TagBindingOperation;
import io.yak.ops.common.enums.tag.TagErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.entity.tag.TableTagValueEntity;
import io.yak.ops.dao.entity.tag.TagKeyEntity;
import io.yak.ops.dao.entity.tag.TagValueEntity;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import io.yak.ops.dao.mapper.tag.TagMapper;
import io.yak.ops.dao.mapper.tag.TagValueUsageRow;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default dictionary and binding operations.
 *
 * <p>Names are validated by Bean Validation at the boundary, so the service only trims whitespace:
 * blank and over-long names never reach it. Uniqueness is enforced by the Workspace name indexes and
 * the collation, so a losing racer surfaces as {@link DuplicateKeyException} and is reported as the
 * matching duplicate code. Delete protection is the {@code ON DELETE RESTRICT} on the binding
 * foreign key: the service catches the integrity violation and maps it to {@code TAG_IN_USE}
 * without any count pre-check, because a pre-check would leave a time-of-check/time-of-use gap.
 */
@Service
public class TagServiceImpl implements TagService {

    private static final Logger LOG = LoggerFactory.getLogger(TagServiceImpl.class);

    /** Values one asset may hold across all keys. */
    private static final int MAX_VALUES_PER_ASSET = 20;

    /** Values one key may hold. */
    private static final int MAX_VALUES_PER_KEY = 200;

    /** Assets one batch command may address. */
    private static final int MAX_BATCH_TABLES = 100;

    /** Values one batch command may carry. */
    private static final int MAX_BATCH_VALUES = 20;

    /** Bindings one batch command may write or remove. */
    private static final int MAX_BATCH_BINDINGS = 1000;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private DataMapTableMapper tableMapper;

    @Resource
    private WorkspaceService workspaceService;

    @Override
    public List<TagKeyVO> listKeys(String workspaceId) {
        Map<String, Long> usage = usageByValueId(workspaceId);
        List<TagKeyVO> keys = new ArrayList<>();
        for (TagKeyEntity key : tagMapper.listKeys(workspaceId)) {
            TagKeyVO vo = new TagKeyVO();
            vo.setId(key.getId());
            vo.setName(key.getName());
            keys.add(vo);
        }
        Map<String, TagKeyVO> byId = keys.stream().collect(Collectors.toMap(TagKeyVO::getId, key -> key));
        for (TagValueEntity value : tagMapper.listValues(workspaceId)) {
            TagKeyVO key = byId.get(value.getKeyId());
            if (key == null) continue;
            key.getValues().add(valueVO(value, usage));
        }
        return keys;
    }

    @Override
    @Transactional
    public TagKeyVO createKey(String workspaceId, String actor, TagKeySaveDTO input) {
        requireManager(workspaceId, actor);
        String name = input.getName().trim();
        if (tagMapper.findKeyByName(workspaceId, name) != null) {
            throw new BusinessException(TagErrorCode.DUPLICATE_TAG_KEY);
        }
        List<String> initialValues = uniqueValueNames(input.getValues());
        if (initialValues.size() > MAX_VALUES_PER_KEY) {
            throw new BusinessException(TagErrorCode.TOO_MANY_TAG_VALUES_PER_KEY);
        }
        LocalDateTime now = DateUtils.now();
        String actorId = actorOf(actor);
        TagKeyEntity key = new TagKeyEntity();
        key.setId(IdUtils.nextId());
        key.setWorkspaceId(workspaceId);
        key.setName(name);
        key.setCreateTime(now);
        key.setUpdateTime(now);
        key.setCreateBy(actorId);
        key.setUpdateBy(actorId);
        try {
            tagMapper.insertKey(key);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(TagErrorCode.DUPLICATE_TAG_KEY);
        }
        for (String valueName : initialValues) {
            insertValue(workspaceId, key.getId(), valueName, now, actorId);
        }
        LOG.info("Tag key created, workspaceId={}, keyId={}, actor={}", workspaceId, key.getId(), actorId);
        return loadKey(workspaceId, key.getId());
    }

    @Override
    public TagKeyVO renameKey(String workspaceId, String actor, String keyId, TagKeyRenameDTO input) {
        requireManager(workspaceId, actor);
        requireKey(workspaceId, keyId);
        String name = input.getName().trim();
        TagKeyEntity existing = tagMapper.findKeyByName(workspaceId, name);
        if (existing != null && !existing.getId().equals(keyId)) {
            throw new BusinessException(TagErrorCode.DUPLICATE_TAG_KEY);
        }
        String actorId = actorOf(actor);
        try {
            tagMapper.renameKey(workspaceId, keyId, name, DateUtils.now(), actorId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(TagErrorCode.DUPLICATE_TAG_KEY);
        }
        return loadKey(workspaceId, keyId);
    }

    @Override
    public void deleteKey(String workspaceId, String actor, String keyId) {
        requireManager(workspaceId, actor);
        requireKey(workspaceId, keyId);
        String actorId = actorOf(actor);
        try {
            tagMapper.deleteKey(workspaceId, keyId);
        } catch (DataIntegrityViolationException exception) {
            // A bound value rejects the cascade; the count lives in GET /keys, not in this error.
            throw new BusinessException(TagErrorCode.TAG_IN_USE);
        }
        LOG.info("Tag key deleted, workspaceId={}, keyId={}, actor={}", workspaceId, keyId, actorId);
    }

    @Override
    public TagValueVO addValue(String workspaceId, String actor, String keyId, TagValueSaveDTO input) {
        requireManager(workspaceId, actor);
        TagKeyEntity key = requireKey(workspaceId, keyId);
        String name = input.getName().trim();
        if (tagMapper.findValueByName(key.getId(), name) != null) {
            throw new BusinessException(TagErrorCode.DUPLICATE_TAG_VALUE);
        }
        if (tagMapper.countValuesByKey(workspaceId, keyId) >= MAX_VALUES_PER_KEY) {
            throw new BusinessException(TagErrorCode.TOO_MANY_TAG_VALUES_PER_KEY);
        }
        TagValueEntity value = insertValue(workspaceId, keyId, name, DateUtils.now(), actorOf(actor));
        return loadValue(workspaceId, value.getId());
    }

    @Override
    public TagValueVO renameValue(String workspaceId, String actor, String valueId, TagValueSaveDTO input) {
        requireManager(workspaceId, actor);
        TagValueEntity value = requireValue(workspaceId, valueId);
        String name = input.getName().trim();
        TagValueEntity existing = tagMapper.findValueByName(value.getKeyId(), name);
        if (existing != null && !existing.getId().equals(valueId)) {
            throw new BusinessException(TagErrorCode.DUPLICATE_TAG_VALUE);
        }
        String actorId = actorOf(actor);
        try {
            tagMapper.renameValue(workspaceId, valueId, name, DateUtils.now(), actorId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(TagErrorCode.DUPLICATE_TAG_VALUE);
        }
        return loadValue(workspaceId, valueId);
    }

    @Override
    public void deleteValue(String workspaceId, String actor, String valueId) {
        requireManager(workspaceId, actor);
        TagValueEntity value = requireValue(workspaceId, valueId);
        String actorId = actorOf(actor);
        try {
            tagMapper.deleteValue(workspaceId, valueId);
        } catch (DataIntegrityViolationException exception) {
            // The binding foreign key rejects the delete; no pre-check can close that gap.
            throw new BusinessException(TagErrorCode.TAG_IN_USE);
        }
        LOG.info(
                "Tag value deleted, workspaceId={}, keyId={}, valueId={}, actor={}",
                workspaceId,
                value.getKeyId(),
                valueId,
                actorId);
    }

    @Override
    @Transactional
    public void replaceTableValues(String workspaceId, String actor, String tableId, List<String> tagValueIds) {
        List<String> requested = new LinkedHashSet<>(tagValueIds).stream().toList();
        if (requested.size() > MAX_VALUES_PER_ASSET) {
            throw new BusinessException(TagErrorCode.TOO_MANY_TAGS_PER_ASSET);
        }
        // The asset row lock serialises concurrent replace-sets: without it two callers can
        // interleave delete and insert into a set neither of them asked for.
        if (tableMapper.lockById(workspaceId, tableId) == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        resolveValues(workspaceId, requested);
        tagMapper.deleteBindingsByTable(workspaceId, tableId);
        if (requested.isEmpty()) return;
        LocalDateTime now = DateUtils.now();
        String actorId = actorOf(actor);
        List<TableTagValueEntity> rows = new ArrayList<>(requested.size());
        for (String valueId : requested) {
            TableTagValueEntity row = new TableTagValueEntity();
            row.setWorkspaceId(workspaceId);
            row.setTableId(tableId);
            row.setTagValueId(valueId);
            row.setCreateTime(now);
            row.setCreateBy(actorId);
            rows.add(row);
        }
        tagMapper.insertBindings(rows);
    }

    @Override
    @Transactional
    public int applyBatch(String workspaceId, String actor, TagBindingBatchDTO input) {
        List<String> tableIds = new ArrayList<>(new LinkedHashSet<>(input.getTableIds()));
        List<String> valueIds = new ArrayList<>(new LinkedHashSet<>(input.getTagValueIds()));
        if (tableIds.size() > MAX_BATCH_TABLES || valueIds.size() > MAX_BATCH_VALUES) {
            throw new BusinessException(TagErrorCode.BATCH_TOO_LARGE);
        }
        if ((long) tableIds.size() * valueIds.size() > MAX_BATCH_BINDINGS) {
            throw new BusinessException(TagErrorCode.BATCH_TOO_LARGE);
        }
        resolveValues(workspaceId, valueIds);
        requireTables(workspaceId, tableIds);
        List<TableTagValueEntity> selected = tagMapper.listBindingsByTablesAndValues(workspaceId, tableIds, valueIds);
        if (input.getOperation() == TagBindingOperation.REMOVE) {
            if (selected.size() > MAX_BATCH_BINDINGS) {
                throw new BusinessException(TagErrorCode.BATCH_TOO_LARGE);
            }
            return tagMapper.deleteBindingsByTablesAndValues(workspaceId, tableIds, valueIds);
        }
        Set<String> bound = selected.stream()
                .map(row -> row.getTableId() + ":" + row.getTagValueId())
                .collect(Collectors.toSet());
        Map<String, Long> boundPerTable = tagMapper.listBindings(workspaceId, tableIds).stream()
                .collect(Collectors.groupingBy(TableTagValueEntity::getTableId, Collectors.counting()));
        LocalDateTime now = DateUtils.now();
        String actorId = actorOf(actor);
        List<TableTagValueEntity> rows = new ArrayList<>();
        for (String tableId : tableIds) {
            long missing = 0;
            for (String valueId : valueIds) {
                if (bound.contains(tableId + ":" + valueId)) continue;
                missing++;
                TableTagValueEntity row = new TableTagValueEntity();
                row.setWorkspaceId(workspaceId);
                row.setTableId(tableId);
                row.setTagValueId(valueId);
                row.setCreateTime(now);
                row.setCreateBy(actorId);
                rows.add(row);
            }
            if (boundPerTable.getOrDefault(tableId, 0L) + missing > MAX_VALUES_PER_ASSET) {
                throw new BusinessException(TagErrorCode.TOO_MANY_TAGS_PER_ASSET, tableId);
            }
        }
        if (rows.isEmpty()) return 0;
        if (rows.size() > MAX_BATCH_BINDINGS) {
            throw new BusinessException(TagErrorCode.BATCH_TOO_LARGE);
        }
        return tagMapper.insertBindings(rows);
    }

    private TagValueEntity insertValue(
            String workspaceId, String keyId, String name, LocalDateTime now, String actorId) {
        TagValueEntity value = new TagValueEntity();
        value.setId(IdUtils.nextId());
        value.setWorkspaceId(workspaceId);
        value.setKeyId(keyId);
        value.setName(name);
        value.setCreateTime(now);
        value.setUpdateTime(now);
        value.setCreateBy(actorId);
        value.setUpdateBy(actorId);
        try {
            tagMapper.insertValue(value);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(TagErrorCode.DUPLICATE_TAG_VALUE);
        }
        return value;
    }

    private void requireManager(String workspaceId, String actor) {
        if (!workspaceService.isManager(workspaceId, actor)) {
            throw new BusinessException(TagErrorCode.TAG_WRITE_FORBIDDEN);
        }
    }

    private TagKeyEntity requireKey(String workspaceId, String keyId) {
        TagKeyEntity key = tagMapper.findKeyById(workspaceId, keyId);
        if (key == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return key;
    }

    private TagValueEntity requireValue(String workspaceId, String valueId) {
        TagValueEntity value = tagMapper.findValueById(workspaceId, valueId);
        if (value == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return value;
    }

    /** Resolves every requested value inside the Workspace, rejecting an unknown identity. */
    private List<TagValueEntity> resolveValues(String workspaceId, Collection<String> valueIds) {
        List<TagValueEntity> values = tagMapper.findValuesByIds(workspaceId, List.copyOf(valueIds));
        if (values.size() != valueIds.size()) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        return values;
    }

    /** Resolves every requested asset inside the Workspace, naming the first unknown identity. */
    private void requireTables(String workspaceId, List<String> tableIds) {
        List<DataMapTableEntity> tables = tableMapper.findByIds(workspaceId, tableIds);
        if (tables.size() == tableIds.size()) return;
        Set<String> found = tables.stream().map(DataMapTableEntity::getId).collect(Collectors.toSet());
        String missing = tableIds.stream()
                .filter(tableId -> !found.contains(tableId))
                .findFirst()
                .orElseThrow();
        throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS, missing);
    }

    /**
     * Trims and de-duplicates the requested value names case-insensitively.
     *
     * <p>The comparison mirrors the collation, which already treats {@code Dept} and {@code dept}
     * as one name; rejecting here keeps the answer a duplicate code instead of an index failure.
     */
    private static List<String> uniqueValueNames(List<String> names) {
        if (names == null) return List.of();
        Set<String> unique = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            String value = StringUtils.trimToNull(name);
            if (value == null) continue;
            if (!unique.add(value)) throw new BusinessException(TagErrorCode.DUPLICATE_TAG_VALUE);
        }
        return List.copyOf(unique);
    }

    private Map<String, Long> usageByValueId(String workspaceId) {
        Map<String, Long> usage = new HashMap<>();
        for (TagValueUsageRow row : tagMapper.listUsageCounts(workspaceId)) {
            usage.put(row.getValueId(), row.getUsageCount());
        }
        return usage;
    }

    private TagKeyVO loadKey(String workspaceId, String keyId) {
        TagKeyEntity key = tagMapper.findKeyById(workspaceId, keyId);
        if (key == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        TagKeyVO vo = new TagKeyVO();
        vo.setId(key.getId());
        vo.setName(key.getName());
        Map<String, Long> usage = usageByValueId(workspaceId);
        for (TagValueEntity value : tagMapper.listValues(workspaceId)) {
            if (!keyId.equals(value.getKeyId())) continue;
            vo.getValues().add(valueVO(value, usage));
        }
        return vo;
    }

    private TagValueVO loadValue(String workspaceId, String valueId) {
        TagValueEntity value = tagMapper.findValueById(workspaceId, valueId);
        if (value == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return new TagValueVO(value.getId(), value.getName(), tagMapper.countBindingsByValue(workspaceId, valueId));
    }

    private static TagValueVO valueVO(TagValueEntity value, Map<String, Long> usage) {
        return new TagValueVO(value.getId(), value.getName(), usage.getOrDefault(value.getId(), 0L));
    }

    private static String actorOf(String actor) {
        String actorId = StringUtils.trimToNull(actor);
        return actorId == null ? CommonConstants.SYSTEM_USER : actorId;
    }
}
