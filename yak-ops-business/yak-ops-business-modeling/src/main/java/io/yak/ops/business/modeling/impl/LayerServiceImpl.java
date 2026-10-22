package io.yak.ops.business.modeling.impl;

import io.yak.ops.business.modeling.LayerService;
import io.yak.ops.common.bean.dto.modeling.LayerInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateLayerDTO;
import io.yak.ops.common.bean.vo.modeling.LayerDataSourceVO;
import io.yak.ops.common.bean.vo.modeling.LayerOptionVO;
import io.yak.ops.common.bean.vo.modeling.LayerVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.LayerErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
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
    private WorkspaceService workspaces;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LayerVO> list(String workspaceId, String actor) {
        requireActor(workspaceId, actor);
        ensurePresets(workspaceId, actor);
        var datasourceNames = datasourceNames(workspaceId);
        return layers.layers(workspaceId).stream()
                .map(row -> project(row, datasourceNames))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
                        datasourceNames.get(row.getDataSourceId())))
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
        if (excludeId != null) requireLayer(workspaceId, excludeId);
        return !layers.occupied(workspaceId, code, excludeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LayerVO create(String workspaceId, String actor, LayerInputDTO input) {
        requireActor(workspaceId, actor);
        var fields = normalize(input);
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
        if (row.isBuiltin() && !presetFieldsUnchanged(row, fields))
            throw new BusinessException(LayerErrorCode.BUILTIN_IMMUTABLE);
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
        if (!layers.delete(workspaceId, id, revision)) throw new BusinessException(LayerErrorCode.REVISION_CONFLICT);
    }

    /**
     * Seeds the five presets once per Workspace.
     *
     * <p>Presets cannot be deleted, so the unique abbreviation is a stable marker: concurrent first readers insert
     * the same codes and the unique key discards the loser, which keeps the default set exactly once without a
     * separate catalog row or an extra write on later reads.
     */
    private void ensurePresets(String workspaceId, String actor) {
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

    private static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
    }

    /** Normalized editable layer fields. */
    private record Fields(String cnName, String enCode, String dataSourceId, String description, boolean enabled) {}

    /** Immutable preset definition seeded per Workspace. */
    private record Preset(String enCode, String cnName, int sortNo) {}
}
