package io.yak.ops.business.modeling.model.impl;

import io.yak.ops.business.modeling.model.ModelService;
import io.yak.ops.business.modeling.naming.NamingRuleService;
import io.yak.ops.business.modeling.planning.LayerService;
import io.yak.ops.common.bean.dto.modeling.ModelFieldInputDTO;
import io.yak.ops.common.bean.dto.modeling.ModelInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelDTO;
import io.yak.ops.common.bean.vo.modeling.LayerOptionVO;
import io.yak.ops.common.bean.vo.modeling.ModelFieldVO;
import io.yak.ops.common.bean.vo.modeling.ModelVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.enums.modeling.PublicationStatus;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.entity.modeling.PlanningNodeEntity;
import io.yak.ops.dao.repository.modeling.DimensionRepository;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardLookupRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardMeasureUnitRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns DWD/DIM model transactions: layer-derived table names, scope rules, field replacement and the
 * three attributes inherited from a bound field standard.
 */
@Service
public class ModelServiceImpl implements ModelService {
    private static final Pattern SUFFIX = Pattern.compile("[a-z][a-z0-9_]{0,40}");
    private static final Pattern FIELD_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_TABLE_NAME = 64;
    private static final int MAX_KEYWORD = 128;
    private static final int MIN_LIFECYCLE_DAYS = 1;
    private static final int MAX_LIFECYCLE_DAYS = 36000;

    @Resource
    private ModelRepository models;

    @Resource
    private PlanningRepository planning;

    @Resource
    private DimensionRepository dimensions;

    @Resource
    private LayerService layers;

    @Resource
    private NamingRuleService naming;

    @Resource
    private StandardLookupRepository lookups;

    @Resource
    private StandardMeasureUnitRepository units;

    @Resource
    private MetricDerivedRepository derivedMetrics;

    /** Read-only here: the model surface shows the newest publication status but never writes one. */
    @Resource
    private PublicationRepository publications;

    @Resource
    private WorkspaceService workspaces;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<ModelVO> page(
            String workspaceId,
            String actor,
            ModelType type,
            String layerId,
            String ownerId,
            String domainId,
            String datamartId,
            String keyword,
            int pageNo,
            int pageSize) {
        requireActor(workspaceId, actor);
        if (pageNo < 1 || pageSize < 1 || pageSize > 100)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        long offset = (pageNo - 1L) * pageSize;
        if (offset > Integer.MAX_VALUE) throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        String typeText = type == null ? null : type.name();
        String pattern = keywordPattern(keyword);
        long total = models.count(
                workspaceId, typeText, trim(layerId), trim(ownerId), trim(domainId), trim(datamartId), pattern);
        var rows = total == 0
                ? List.<ModelEntity>of()
                : models.page(
                        workspaceId,
                        typeText,
                        trim(layerId),
                        trim(ownerId),
                        trim(domainId),
                        trim(datamartId),
                        pattern,
                        pageSize,
                        (int) offset);
        var statuses = publications.latestStatusByModel(
                workspaceId, rows.stream().map(ModelEntity::getId).toList());
        return PageData.of(
                rows.stream()
                        .map(row -> project(row, null, statusOf(statuses, row.getId())))
                        .toList(),
                total,
                pageNo,
                pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ModelVO detail(String workspaceId, String actor, String id) {
        requireActor(workspaceId, actor);
        var row = models.find(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        var statuses = publications.latestStatusByModel(workspaceId, List.of(row.getId()));
        return project(row, models.fields(row.getId()), statusOf(statuses, row.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelVO create(String workspaceId, String actor, ModelInputDTO input) {
        requireActor(workspaceId, actor);
        if (input == null || input.getType() == null)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        var layer = requireLayer(workspaceId, actor, input.getLayerId());
        var row = new ModelEntity();
        row.setWorkspaceId(workspaceId);
        row.setRevision(1);
        row.initCreate(actor);
        row.setLayerId(layer.id());
        var suffix = suffix(input.getNameSuffix());
        row.setName(fullName(layer, suffix));
        var processCode = applyModel(row, workspaceId, actor, input);
        applyNaming(row, layer, workspaceId, input.getType(), suffix, processCode);
        var fields = validateFields(workspaceId, actor, row.getId(), input.getType(), input.getFields());
        if (models.occupied(workspaceId, row.getName(), null))
            throw new BusinessException(DimensionModelingErrorCode.DUPLICATE_TABLE_NAME);
        try {
            models.add(row);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            throw new BusinessException(DimensionModelingErrorCode.DUPLICATE_TABLE_NAME, conflict);
        }
        for (var field : fields) models.addField(field);
        return detail(workspaceId, actor, row.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelVO update(String workspaceId, String actor, String id, UpdateModelDTO input) {
        requireActor(workspaceId, actor);
        if (input == null || input.getRevision() == null || input.getRevision() < 1)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        var row = lockRow(workspaceId, id);
        if (row.getRevision() != input.getRevision())
            throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        var stored = ModelType.from(row.getType());
        // A row written by hand with a kind this build does not know stays readable but immutable.
        if (stored == null) throw new BusinessException(DimensionModelingErrorCode.UNKNOWN_MODEL_TYPE);
        // Turning a detail table into a dimension table is a different object, not an edit.
        if (input.getType() != stored) throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        var layer = requireLayer(workspaceId, actor, input.getLayerId());
        row.setLayerId(layer.id());
        var suffix = suffix(input.getNameSuffix());
        row.setName(fullName(layer, suffix));
        var processCode = applyModel(row, workspaceId, actor, input);
        applyNaming(row, layer, workspaceId, stored, suffix, processCode);
        var fields = validateFields(workspaceId, actor, id, stored, input.getFields());
        if (models.occupied(workspaceId, row.getName(), id))
            throw new BusinessException(DimensionModelingErrorCode.DUPLICATE_TABLE_NAME);
        row.initUpdate(actor);
        try {
            if (!models.update(row)) throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            throw new BusinessException(DimensionModelingErrorCode.DUPLICATE_TABLE_NAME, conflict);
        }
        models.deleteFields(id);
        for (var field : fields) models.addField(field);
        row.setRevision(Math.addExact(input.getRevision(), 1));
        return detail(workspaceId, actor, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workspaceId, String actor, String id, long revision) {
        requireActor(workspaceId, actor);
        if (revision < 1) throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        var row = lockRow(workspaceId, id);
        if (row.getRevision() != revision) throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        if (!models.delete(workspaceId, id, revision))
            throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public boolean nameAvailable(
            String workspaceId, String actor, String layerId, String nameSuffix, String excludeId) {
        requireActor(workspaceId, actor);
        var layer = requireLayer(workspaceId, actor, layerId);
        return !models.occupied(workspaceId, fullName(layer, suffix(nameSuffix)), trim(excludeId));
    }

    /**
     * Resolves the layer that supplies the table name prefix.
     *
     * <p>Only enabled layers are projected by {@link LayerService#options}, and the database keeps an
     * enabled layer bound to a datasource, so a missing option means the layer is not selectable yet.
     */
    private LayerOptionVO requireLayer(String workspaceId, String actor, String layerId) {
        var requested = trim(layerId);
        if (requested == null) throw new BusinessException(DimensionModelingErrorCode.LAYER_NOT_SELECTABLE);
        return layers.options(workspaceId, actor).stream()
                .filter(option -> option.id().equals(requested))
                .findFirst()
                .orElseThrow(() -> new BusinessException(DimensionModelingErrorCode.LAYER_NOT_SELECTABLE));
    }

    /** Builds the full table name; the prefix follows the selected layer so a rename cannot drift. */
    private static String fullName(LayerOptionVO layer, String suffix) {
        var name = layer.enCode().toLowerCase(Locale.ROOT) + "_" + suffix;
        if (name.length() > MAX_TABLE_NAME) throw new BusinessException(DimensionModelingErrorCode.INVALID_TABLE_NAME);
        return name;
    }

    /** Validates the suffix shape; {@code INVALID_TABLE_NAME} keeps meaning a malformed or over-long name. */
    private static String suffix(String value) {
        var normalized = trim(value);
        if (normalized == null || !SUFFIX.matcher(normalized).matches())
            throw new BusinessException(DimensionModelingErrorCode.INVALID_TABLE_NAME);
        return normalized;
    }

    /**
     * Applies the layer naming rule and records the root word the name was composed from.
     *
     * <p>The rule is checked on every save, not only when it is enabled, because both halves of the
     * composed head stay editable: a process code can be lengthened and a dictionary abbreviation
     * can be renamed. {@code DIM} and {@code ADS} are exempt because they carry no process token.
     */
    private void applyNaming(
            ModelEntity row,
            LayerOptionVO layer,
            String workspaceId,
            ModelType type,
            String suffix,
            String processCode) {
        row.setNamingRootWordId(null);
        if (!layer.namingRuleEnabled() || !type.isPublicLayer()) return;
        if (processCode == null) throw new BusinessException(DimensionModelingErrorCode.NAMING_RULE_VIOLATION);
        row.setNamingRootWordId(naming.rootWord(workspaceId, suffix, processCode));
    }

    /**
     * Validates and copies every non-identity attribute, including the scope ownership rules.
     *
     * @return the code of the owning business process for a public-layer model, null otherwise
     */
    private String applyModel(ModelEntity row, String workspaceId, String actor, ModelInputDTO input) {
        if (input.getType() == null) throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        row.setType(input.getType().name());
        var categoryId = requireIdentifier(input.getCategoryId());
        requireNode(workspaceId, NodeKind.CATEGORY, categoryId);
        row.setCategoryId(categoryId);
        String processCode = null;
        if (input.getType().isPublicLayer()) processCode = applyProcessOwnership(row, workspaceId, categoryId, input);
        else if (input.getType() == ModelType.DIM) applyDimensionOwnership(row, workspaceId, categoryId, input);
        else applyApplicationOwnership(row, workspaceId, input);
        row.setCnName(requireText(input.getCnName(), 128));
        if (input.getStoragePolicy() == null) throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        row.setStoragePolicy(input.getStoragePolicy());
        var lifecycle = input.getLifecycleDays();
        if (lifecycle == null || lifecycle < MIN_LIFECYCLE_DAYS || lifecycle > MAX_LIFECYCLE_DAYS)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        row.setLifecycleDays(lifecycle);
        row.setOwnerId(requireOwner(workspaceId, actor, input.getOwnerId()));
        row.setDescription(optionalText(input.getDescription(), 500));
        return processCode;
    }

    /** DWD and DWS hang below a business process inside one data domain of the selected category. */
    private String applyProcessOwnership(ModelEntity row, String workspaceId, String categoryId, ModelInputDTO input) {
        var domainId = requireIdentifier(input.getDomainId());
        var domain = requireNode(workspaceId, NodeKind.DOMAIN, domainId);
        if (!categoryId.equals(domain.getParentId()))
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        var processId = requireIdentifier(input.getProcessId());
        var process = requireNode(workspaceId, NodeKind.PROCESS, processId);
        if (!domainId.equals(process.getParentId()))
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        if (trim(input.getDatamartId()) != null || trim(input.getDimensionId()) != null)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        row.setProcessId(processId);
        row.setDomainId(domainId);
        row.setDatamartId(null);
        row.setDimensionId(null);
        return process.getCode();
    }

    /** DIM is anchored to exactly one scope; a public-layer DIM must bind a dimension of that domain. */
    private void applyDimensionOwnership(ModelEntity row, String workspaceId, String categoryId, ModelInputDTO input) {
        var domainId = trim(input.getDomainId());
        var datamartId = trim(input.getDatamartId());
        if ((domainId == null) == (datamartId == null) || trim(input.getProcessId()) != null)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        row.setProcessId(null);
        if (domainId != null) {
            var domain = requireNode(workspaceId, NodeKind.DOMAIN, domainId);
            if (!categoryId.equals(domain.getParentId()))
                throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
            var dimensionId = requireIdentifier(input.getDimensionId());
            requireDimension(workspaceId, dimensionId, DimensionScope.PUBLIC, domainId);
            row.setDomainId(domainId);
            row.setDatamartId(null);
            row.setDimensionId(dimensionId);
            return;
        }
        requireNode(workspaceId, NodeKind.DATASET, datamartId);
        var dimensionId = trim(input.getDimensionId());
        if (dimensionId != null) requireDimension(workspaceId, dimensionId, DimensionScope.APPLICATION, datamartId);
        row.setDomainId(null);
        row.setDatamartId(datamartId);
        row.setDimensionId(dimensionId);
    }

    /** ADS hangs below a data mart and never binds a concept dimension or a business process. */
    private void applyApplicationOwnership(ModelEntity row, String workspaceId, ModelInputDTO input) {
        if (trim(input.getProcessId()) != null
                || trim(input.getDomainId()) != null
                || trim(input.getDimensionId()) != null)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        var datamartId = requireIdentifier(input.getDatamartId());
        requireNode(workspaceId, NodeKind.DATASET, datamartId);
        row.setProcessId(null);
        row.setDomainId(null);
        row.setDatamartId(datamartId);
        row.setDimensionId(null);
    }

    /** Confirms the dimension exists in the Workspace and belongs to the scope the model claims. */
    private void requireDimension(String workspaceId, String dimensionId, DimensionScope scope, String scopeId) {
        var dimension = dimensions
                .find(workspaceId, dimensionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        if (dimension.getScope() != scope || !scopeId.equals(dimension.ownerScope()))
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
    }

    private PlanningNodeEntity requireNode(String workspaceId, NodeKind kind, String id) {
        return planning.find(workspaceId, kind, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    /**
     * Builds the replacement field rows.
     *
     * <p>Binding is authoritative rather than advisory: a bound standard overwrites code, Chinese
     * name, type, length and nullability before validation, so a client cannot desynchronize a field
     * from the standard it claims to follow. A lookup table supplies code and Chinese name only when
     * no standard is bound, and a unit never overrides anything. The aggregate is checked after that
     * inheritance, so a standard that resolves to {@code STRING} cannot carry {@code SUM} either.
     * A derived-metric link records provenance only: it is accepted on measure kinds, must resolve
     * inside the Workspace, and never overwrites the field's own attributes.
     */
    private List<ModelFieldEntity> validateFields(
            String workspaceId, String actor, String modelId, ModelType type, List<ModelFieldInputDTO> input) {
        var submitted = input == null ? List.<ModelFieldInputDTO>of() : input;
        var rows = new ArrayList<ModelFieldEntity>(submitted.size());
        var codes = new HashSet<String>();
        var partitions = 0;
        var measures = 0;
        for (var index = 0; index < submitted.size(); index++) {
            var field = submitted.get(index);
            if (field == null) throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
            var enCode = trim(field.getEnCode());
            var cnName = field.getCnName();
            var dataType = field.getDataType();
            var length = trim(field.getLengthPrecision());
            var isNotNull = Boolean.TRUE.equals(field.getIsNotNull());
            var standardId = trim(field.getStandardId());
            var lookupId = trim(field.getLookupId());
            var unitId = trim(field.getMeasureUnitId());
            var metricId = trim(field.getMetricId());
            if (standardId != null) {
                var inherited = models.standard(workspaceId, standardId);
                if (inherited == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
                enCode = inherited.enCode();
                cnName = inherited.cnName();
                dataType = inherited.dataType();
                length = inherited.lengthPrecision();
                // Nullability is not inherited: the standard only seeds the flag on the client,
                // because the same standard is cited by ODS, DWD and DIM models whose data carries
                // different constraints. A submitted value wins; only an absent one takes the
                // standard as its default.
                if (field.getIsNotNull() == null) isNotNull = inherited.isNotNull();
            } else if (lookupId != null) {
                var lookup = lookups.find(workspaceId, lookupId)
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
                enCode = lookup.getEnCode();
                cnName = lookup.getCnName();
            }
            if (unitId != null && units.find(workspaceId, unitId).isEmpty())
                throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
            // The metric link is advisory: it records where the field came from, but the field's
            // own attributes stay authoritative after import. Only measure kinds carry one.
            if (metricId != null) {
                if (!type.allowsAggregate()) throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
                if (derivedMetrics.row(workspaceId, metricId).isEmpty())
                    throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
            }
            if (enCode == null || !FIELD_CODE.matcher(enCode).matches() || !codes.add(enCode))
                throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
            if (dataType == null) throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
            var displayName = requireText(cnName, 128, DimensionModelingErrorCode.INVALID_FIELD);
            if (length != null && length.length() > 32)
                throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
            var partition = Boolean.TRUE.equals(field.getIsPartition());
            if (partition) {
                if (dataType != DataType.STRING) throw new BusinessException(DimensionModelingErrorCode.PARTITION_RULE);
                if (++partitions > 1) throw new BusinessException(DimensionModelingErrorCode.PARTITION_RULE);
            }
            var aggregate = field.getAggregateType();
            if (aggregate != null) {
                if (!type.allowsAggregate() || !aggregate.supports(dataType))
                    throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
                measures++;
            }
            var row = new ModelFieldEntity();
            row.initCreate(actor);
            row.setWorkspaceId(workspaceId);
            row.setModelId(modelId);
            row.setOrdinal(index + 1);
            row.setEnCode(enCode);
            row.setCnName(displayName);
            row.setDataType(dataType);
            row.setLengthPrecision(length);
            row.setIsPrimaryKey(Boolean.TRUE.equals(field.getIsPrimaryKey()));
            row.setIsNotNull(isNotNull);
            row.setIsPartition(partition);
            row.setAggregateType(aggregate);
            row.setStandardId(standardId);
            row.setLookupId(lookupId);
            row.setMeasureUnitId(unitId);
            row.setMetricId(metricId);
            row.setDescription(optionalText(field.getDescription(), 500, DimensionModelingErrorCode.INVALID_FIELD));
            rows.add(row);
        }
        if (type == ModelType.DWS && measures == 0)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
        return rows;
    }

    private String requireOwner(String workspaceId, String actor, String ownerId) {
        var requested = trim(ownerId);
        var owner = requested == null ? actor : requested;
        if (owner == null || owner.isBlank() || owner.length() > 64 || !workspaces.isActiveMember(workspaceId, owner))
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        return owner;
    }

    private ModelEntity lockRow(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return models.lock(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void requireActor(String workspaceId, String actor) {
        if (!workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(WorkspaceErrorCode.ACCESS_DENIED);
    }

    private static String requireIdentifier(String value) {
        var normalized = trim(value);
        if (normalized == null || normalized.length() > 64)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        return normalized;
    }

    private static String requireText(String value, int max) {
        return requireText(value, max, DimensionModelingErrorCode.INVALID_MODEL);
    }

    private static String requireText(String value, int max, DimensionModelingErrorCode failure) {
        if (value == null) throw new BusinessException(failure);
        var normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > max) throw new BusinessException(failure);
        return normalized;
    }

    private static String optionalText(String value, int max) {
        return optionalText(value, max, DimensionModelingErrorCode.INVALID_MODEL);
    }

    private static String optionalText(String value, int max, DimensionModelingErrorCode failure) {
        var normalized = trim(value);
        if (normalized == null) return null;
        if (normalized.length() > max) throw new BusinessException(failure);
        return normalized;
    }

    private static String keywordPattern(String keyword) {
        var normalized = trim(keyword);
        if (normalized == null) return null;
        if (normalized.length() > MAX_KEYWORD) throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        return "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static String trim(String value) {
        if (value == null) return null;
        var normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static ModelVO project(ModelEntity row, List<ModelFieldEntity> fields, PublicationStatus status) {
        return new ModelVO(
                row.getId(),
                ModelType.from(row.getType()),
                row.getName(),
                row.getCnName(),
                row.getLayerId(),
                row.getLayerName(),
                row.getCategoryId(),
                row.getCategoryName(),
                row.getProcessId(),
                row.getProcessName(),
                row.getDomainId(),
                row.getDomainName(),
                row.getDatamartId(),
                row.getDatamartName(),
                row.getDimensionId(),
                row.getDimensionName(),
                row.getStoragePolicy(),
                row.getLifecycleDays(),
                row.getOwnerId(),
                row.getDescription(),
                row.getNamingRootWordId(),
                fields == null ? row.getFieldCount() : fields.size(),
                row.getRevision(),
                fields == null
                        ? null
                        : fields.stream().map(ModelServiceImpl::project).toList(),
                status);
    }

    /** Reads a stored status without inventing one: a value this build does not know stays null. */
    private static PublicationStatus statusOf(Map<String, String> statuses, String modelId) {
        return PublicationStatus.from(statuses.get(modelId));
    }

    private static ModelFieldVO project(ModelFieldEntity row) {
        return new ModelFieldVO(
                row.getId(),
                row.getOrdinal(),
                row.getEnCode(),
                row.getCnName(),
                row.getDataType(),
                row.getLengthPrecision(),
                Boolean.TRUE.equals(row.getIsPrimaryKey()),
                Boolean.TRUE.equals(row.getIsNotNull()),
                Boolean.TRUE.equals(row.getIsPartition()),
                row.getAggregateType(),
                row.getStandardId(),
                row.getStandardEnCode(),
                row.getLookupId(),
                row.getLookupEnCode(),
                row.getMeasureUnitId(),
                row.getMeasureUnitEnCode(),
                row.getMetricId(),
                row.getMetricEnCode(),
                row.getMetricCnName(),
                row.getDescription());
    }
}
