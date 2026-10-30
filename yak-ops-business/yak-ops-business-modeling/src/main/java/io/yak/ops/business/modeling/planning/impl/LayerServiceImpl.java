package io.yak.ops.business.modeling.planning.impl;

import io.yak.ops.business.modeling.planning.LayerService;
import io.yak.ops.business.modeling.publication.PhysicalDialect;
import io.yak.ops.common.bean.dto.modeling.LayerInputDTO;
import io.yak.ops.common.bean.dto.modeling.LayerTargetsInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateLayerDTO;
import io.yak.ops.common.bean.vo.modeling.LayerDataSourceVO;
import io.yak.ops.common.bean.vo.modeling.LayerOptionVO;
import io.yak.ops.common.bean.vo.modeling.LayerTargetVO;
import io.yak.ops.common.bean.vo.modeling.LayerVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.LayerErrorCode;
import io.yak.ops.common.enums.modeling.PublicationEnvironment;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.entity.modeling.LayerTargetEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.dao.repository.modeling.LayerTargetRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Owns preset seeding, Workspace authorization and layer binding rules. */
@Service
public class LayerServiceImpl implements LayerService {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
    private static final int CUSTOM_SORT_NO = 1000;
    private static final List<Preset> PRESETS = List.of(
            new Preset("ODS", "贴源层", 10),
            new Preset("DWD", "明细层", 20),
            new Preset("DWS", "汇总层", 30),
            new Preset("ADS", "应用层", 40),
            new Preset("DIM", "公共维度层", 50));

    @Resource
    private LayerRepository layers;

    @Resource
    private LayerTargetRepository layerTargets;

    @Resource
    private WorkspaceService workspaces;

    /**
     * Lists layers, seeding the presets on first use.
     *
     * <p>Runs at {@code READ_COMMITTED} on purpose: the seeder waits on the Workspace row lock, so its first
     * consistent read happens after the winner committed. Under {@code REPEATABLE_READ} the snapshot taken by the
     * pre-lock count would still be empty and this call would return no layers at all.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<LayerVO> list(String workspaceId, String actor) {
        requireActor(workspaceId, actor);
        ensurePresets(workspaceId, actor);
        var datasourceNames = datasourceNames(workspaceId);
        return layers.layers(workspaceId).stream()
                .map(row -> project(row, datasourceNames))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<LayerOptionVO> options(String workspaceId, String actor) {
        requireActor(workspaceId, actor);
        ensurePresets(workspaceId, actor);
        var datasourceNames = datasourceNames(workspaceId);
        return layers.layers(workspaceId).stream()
                .filter(LayerEntity::isEnabled)
                .map(row -> new LayerOptionVO(
                        row.getId(),
                        row.getEnCode(),
                        row.getCnName(),
                        row.getDataSourceId(),
                        datasourceNames.get(row.getDataSourceId()),
                        row.isNamingRuleEnabled()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LayerDataSourceVO> datasources(String workspaceId, String actor) {
        requireActor(workspaceId, actor);
        return layers.datasources(workspaceId).stream()
                .map(row -> new LayerDataSourceVO(
                        row.getId(),
                        row.getName(),
                        row.getDbType(),
                        row.getEnvironment() == null
                                ? null
                                : row.getEnvironment().name(),
                        row.getConnStatus() == null ? null : row.getConnStatus().name()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean available(String workspaceId, String actor, String enCode, String excludeId) {
        requireActor(workspaceId, actor);
        var code = normalizeCode(enCode);
        if (excludeId != null) {
            // Editing a layer keeps its own abbreviation available; everything else is unchanged.
            var editing = requireLayer(workspaceId, excludeId);
            if (code.equals(editing.getEnCode())) return !layers.occupied(workspaceId, code, excludeId);
        }
        // A preset abbreviation is refused by create and update whatever the table holds, so the
        // pre-check has to answer the same way or the form would offer a code the save rejects.
        if (reserved(code)) return false;
        return !layers.occupied(workspaceId, code, excludeId);
    }

    /**
     * Creates a custom layer.
     *
     * <p>Presets are seeded by the read paths, not here, so this method refuses a preset abbreviation outright:
     * otherwise a Workspace whose first action is a create would store a look-alike code and the later seeding
     * upsert would silently skip that default.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LayerVO create(String workspaceId, String actor, LayerInputDTO input) {
        requireActor(workspaceId, actor);
        var fields = normalize(input);
        if (reserved(fields.enCode())) throw new BusinessException(LayerErrorCode.DUPLICATE_CODE);
        requireBinding(workspaceId, fields.dataSourceId(), fields.enabled());
        var row = new LayerEntity();
        row.setWorkspaceId(workspaceId);
        row.setEnCode(fields.enCode());
        row.setCnName(fields.cnName());
        row.setDataSourceId(fields.dataSourceId());
        row.setDescription(fields.description());
        row.setEnabled(fields.enabled());
        row.setBuiltin(false);
        row.setSortNo(CUSTOM_SORT_NO);
        row.setRevision(1);
        row.initCreate(actor);
        try {
            layers.add(row);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(LayerErrorCode.DUPLICATE_CODE, conflict);
        }
        return project(row, datasourceNames(workspaceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LayerVO update(String workspaceId, String actor, String id, UpdateLayerDTO input) {
        requireActor(workspaceId, actor);
        var row = requireLayer(workspaceId, id);
        if (input == null || input.getRevision() == null || input.getRevision() < 1) throw invalid();
        if (row.getRevision() != input.getRevision()) throw new BusinessException(LayerErrorCode.REVISION_CONFLICT);
        var fields = normalize(input);
        if (!row.isBuiltin() && reserved(fields.enCode())) throw new BusinessException(LayerErrorCode.DUPLICATE_CODE);
        if (row.isBuiltin() && !presetFieldsUnchanged(row, fields))
            throw new BusinessException(LayerErrorCode.BUILTIN_IMMUTABLE);
        // A model's stored table name is the layer prefix plus its suffix, frozen when it was saved.
        // Changing the abbreviation under existing models would leave every one of them contradicting
        // the naming rule and the physical table it was published to, so it needs the same guard as
        // deletion: move or remove the models first.
        if (!row.getEnCode().equals(fields.enCode()) && layers.modelsInLayer(workspaceId, id) > 0)
            throw new BusinessException(LayerErrorCode.HAS_DEPENDENCIES);
        requireBinding(workspaceId, fields.dataSourceId(), fields.enabled());
        row.setEnCode(fields.enCode());
        row.setCnName(fields.cnName());
        row.setDataSourceId(fields.dataSourceId());
        row.setDescription(fields.description());
        row.setEnabled(fields.enabled());
        row.initUpdate(actor);
        try {
            if (!layers.update(row)) throw new BusinessException(LayerErrorCode.REVISION_CONFLICT);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(LayerErrorCode.DUPLICATE_CODE, conflict);
        }
        return project(row, datasourceNames(workspaceId), Math.addExact(input.getRevision(), 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workspaceId, String actor, String id, long revision) {
        requireActor(workspaceId, actor);
        var row = requireLayer(workspaceId, id);
        if (row.isBuiltin()) throw new BusinessException(LayerErrorCode.BUILTIN_UNDELETABLE);
        if (revision < 1 || row.getRevision() != revision)
            throw new BusinessException(LayerErrorCode.REVISION_CONFLICT);
        // Models cite their layer with ON DELETE RESTRICT: answer with the business error instead
        // of letting the foreign key surface as a 500 from the persistence layer. A model saved
        // between the count and the delete still reaches the database, so that case is mapped too.
        if (layers.modelsInLayer(workspaceId, id) > 0) throw new BusinessException(LayerErrorCode.HAS_DEPENDENCIES);
        try {
            if (!layers.delete(workspaceId, id, revision))
                throw new BusinessException(LayerErrorCode.REVISION_CONFLICT);
        } catch (DataIntegrityViolationException stillReferenced) {
            throw new BusinessException(LayerErrorCode.HAS_DEPENDENCIES, stillReferenced);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<LayerTargetVO> targets(String workspaceId, String actor, String layerId) {
        requireActor(workspaceId, actor);
        requireLayer(workspaceId, layerId);
        var names = datasourceNames(workspaceId);
        return layerTargets.listByLayer(workspaceId, layerId).stream()
                .map(row -> project(row, names))
                .toList();
    }

    /**
     * Replaces the per-environment bindings of one layer.
     *
     * <p>The datasource must exist in the Workspace and speak a publishable dialect, because a
     * binding the publish dialog could never resolve would only move the failure to publish time.
     * Schema rules follow the dialect: engines with an independent schema require one, engines
     * without one refuse it.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LayerTargetVO> bindTargets(
            String workspaceId, String actor, String layerId, LayerTargetsInputDTO input) {
        requireActor(workspaceId, actor);
        requireLayer(workspaceId, layerId);
        var entries =
                input == null || input.targets() == null ? List.<LayerTargetsInputDTO.Target>of() : input.targets();
        if (entries.size() > 2) throw invalid();
        var names = datasourceNames(workspaceId);
        var dbTypes = layers.datasources(workspaceId).stream()
                .collect(Collectors.toMap(
                        DataSourceEntity::getId, DataSourceEntity::getDbType, (first, ignored) -> first));
        var seen = new HashSet<String>();
        var rows = new ArrayList<LayerTargetEntity>(entries.size());
        for (var entry : entries) {
            var environment = PublicationEnvironment.from(entry.environment());
            if (environment == null || !seen.add(environment.name())) throw invalid();
            var dataSourceId = entry.dataSourceId().strip();
            if (!layers.datasourceExists(workspaceId, dataSourceId))
                throw new BusinessException(LayerErrorCode.INVALID_DATASOURCE);
            var dialect = PhysicalDialect.of(dbTypes.get(dataSourceId));
            if (dialect == null) {
                throw new BusinessException("数据源类型 " + dbTypes.get(dataSourceId) + " 暂不支持发布，无法绑定");
            }
            var database = entry.database().strip();
            var schema = entry.schema() == null || entry.schema().isBlank()
                    ? null
                    : entry.schema().strip();
            if (dialect.requiresSchema() && schema == null) {
                throw new BusinessException("该引擎要求显式配置目标 Schema");
            }
            if (!dialect.requiresSchema() && schema != null) {
                throw new BusinessException("该引擎没有独立 Schema，请勿填写");
            }
            var row = new LayerTargetEntity();
            row.setWorkspaceId(workspaceId);
            row.setLayerId(layerId);
            row.setEnvironment(environment.name());
            row.setDataSourceId(dataSourceId);
            row.setPhysicalDatabase(database);
            row.setPhysicalSchema(schema);
            row.initCreate(actor);
            rows.add(row);
        }
        layerTargets.replace(workspaceId, layerId, rows);
        return layerTargets.listByLayer(workspaceId, layerId).stream()
                .map(row -> project(row, names))
                .toList();
    }

    private static LayerTargetVO project(LayerTargetEntity row, Map<String, String> datasourceNames) {
        return new LayerTargetVO(
                row.getId(),
                row.getLayerId(),
                PublicationEnvironment.from(row.getEnvironment()),
                row.getDataSourceId(),
                datasourceNames.get(row.getDataSourceId()),
                row.getPhysicalDatabase(),
                row.getPhysicalSchema());
    }

    /**
     * Seeds the five presets once per Workspace.
     *
     * <p>Presets cannot be deleted, so the unique abbreviation is a stable marker: a second writer would discard
     * its duplicate codes, but the concurrent upserts still deadlock on the shared unique index. Seeding therefore
     * locks the Workspace row first and re-reads the count, so only the first reader inserts. Callers must run at
     * {@code READ_COMMITTED}, otherwise the re-read after the lock still sees the pre-lock snapshot.
     */
    private void ensurePresets(String workspaceId, String actor) {
        if (layers.builtinCount(workspaceId) >= PRESETS.size()) return;
        layers.lockWorkspace(workspaceId);
        if (layers.builtinCount(workspaceId) >= PRESETS.size()) return;
        var rows = PRESETS.stream()
                .map(preset -> {
                    var row = new LayerEntity();
                    row.setWorkspaceId(workspaceId);
                    row.setEnCode(preset.enCode());
                    row.setCnName(preset.cnName());
                    row.setEnabled(false);
                    row.setBuiltin(true);
                    row.setSortNo(preset.sortNo());
                    row.setRevision(1);
                    row.initCreate(actor);
                    return row;
                })
                .toList();
        layers.seed(rows);
    }

    private void requireBinding(String workspaceId, String dataSourceId, boolean enabled) {
        if (dataSourceId != null && !layers.datasourceExists(workspaceId, dataSourceId))
            throw new BusinessException(LayerErrorCode.INVALID_DATASOURCE);
        if (enabled && dataSourceId == null) throw new BusinessException(LayerErrorCode.DATASOURCE_REQUIRED);
    }

    private LayerEntity requireLayer(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw invalid();
        return layers.layer(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void requireActor(String workspaceId, String actor) {
        if (!workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(WorkspaceErrorCode.ACCESS_DENIED);
    }

    private Map<String, String> datasourceNames(String workspaceId) {
        return layers.datasources(workspaceId).stream()
                .collect(Collectors.toMap(
                        DataSourceEntity::getId, DataSourceEntity::getName, (existing, ignored) -> existing));
    }

    private static LayerVO project(LayerEntity row, Map<String, String> datasourceNames) {
        return project(row, datasourceNames, row.getRevision());
    }

    private static LayerVO project(LayerEntity row, Map<String, String> datasourceNames, long revision) {
        return new LayerVO(
                row.getId(),
                row.getEnCode(),
                row.getCnName(),
                row.getDataSourceId(),
                datasourceNames.get(row.getDataSourceId()),
                row.getDescription(),
                row.isEnabled(),
                row.isBuiltin(),
                row.isNamingRuleEnabled(),
                row.getSortNo(),
                revision);
    }

    private static boolean presetFieldsUnchanged(LayerEntity row, Fields fields) {
        return fields.enCode().equals(row.getEnCode())
                && fields.cnName().equals(row.getCnName())
                && Objects.equals(fields.description(), row.getDescription());
    }

    private static Fields normalize(LayerInputDTO input) {
        if (input == null || input.getCnName() == null || input.getCnName().isBlank()) throw invalid();
        var cnName = input.getCnName().strip();
        if (cnName.length() > 64) throw invalid();
        var description =
                input.getDescription() == null || input.getDescription().isBlank()
                        ? null
                        : input.getDescription().strip();
        if (description != null && description.length() > 500) throw invalid();
        var dataSourceId =
                input.getDataSourceId() == null || input.getDataSourceId().isBlank()
                        ? null
                        : input.getDataSourceId().strip();
        return new Fields(
                cnName,
                normalizeCode(input.getEnCode()),
                dataSourceId,
                description,
                Boolean.TRUE.equals(input.getEnabled()));
    }

    private static String normalizeCode(String enCode) {
        var normalized = enCode == null ? "" : enCode.strip().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) throw new BusinessException(LayerErrorCode.INVALID_CODE);
        return normalized;
    }

    /**
     * Reports whether an abbreviation belongs to a preset.
     *
     * <p>Presets are seeded lazily, so a custom layer created before the first read would otherwise take a preset
     * code and leave the Workspace with four defaults plus one look-alike.
     */
    private static boolean reserved(String enCode) {
        return PRESETS.stream().anyMatch(preset -> preset.enCode().equals(enCode));
    }

    private static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
    }

    /** Normalized editable layer fields. */
    private record Fields(String cnName, String enCode, String dataSourceId, String description, boolean enabled) {}

    /** Immutable preset definition seeded per Workspace. */
    private record Preset(String enCode, String cnName, int sortNo) {}
}
