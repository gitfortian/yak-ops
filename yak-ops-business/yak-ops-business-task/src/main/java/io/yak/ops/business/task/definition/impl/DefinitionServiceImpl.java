package io.yak.ops.business.task.definition.impl;

import io.yak.ops.business.task.definition.DefinitionService;
import io.yak.ops.common.bean.dto.task.CreateDefinitionDTO;
import io.yak.ops.common.bean.dto.task.DefinitionQueryDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskValidationContext;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Task Definition is the sole owner of identity, immutable versions and publication. */
@Service
public class DefinitionServiceImpl implements DefinitionService {
    @Resource
    private DefinitionRepository definitionRepository;
    @Resource
    private DefinitionVersionRepository versionRepository;
    @Resource
    private TaskPluginRegistry pluginRegistry;
    @Resource
    private TaskValidationContext validationContext;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVO create(CreateDefinitionDTO dto, String operatorId) {
        if (dto == null) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String name = requiredName(dto.getName());
        checkNameAvailable(workspaceId, name, null);
        String type = TaskPluginRegistry.normalizeType(dto.getTaskType());
        String parameters = validatedParameters(type, dto.getParameters());
        DefinitionEntity definition = new DefinitionEntity();
        definition.setWorkspaceId(workspaceId);
        definition.setName(name);
        definition.setTaskType(type);
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        definition.setCurrentVersion(1);
        definition.setRemark(dto.getRemark());
        definition.initCreate(operatorId);
        definitionRepository.add(definition);
        appendVersion(definition, parameters, operatorId);
        return toVO(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVO update(String id, UpdateDefinitionDTO dto, String operatorId) {
        if (dto == null) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        DefinitionEntity definition = requireDefinition(id, true);
        requireUnpublished(definition);
        String name = requiredName(dto.getName());
        checkNameAvailable(definition.getWorkspaceId(), name, id);
        if (StringUtils.isNotBlank(dto.getParameters())) {
            String updated = validatedParameters(definition.getTaskType(), dto.getParameters());
            String existing = currentVersion(definition).getParameters();
            if (!updated.equals(existing)) {
                definition.setCurrentVersion(definition.getCurrentVersion() + 1);
                appendVersion(definition, updated, operatorId);
            }
        }
        definition.setName(name);
        definition.setRemark(dto.getRemark());
        definition.initUpdate(operatorId);
        if (definitionRepository.update(definition.getWorkspaceId(), definition) == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        return toVO(definition);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVO publish(String id, String operatorId) {
        DefinitionEntity definition = requireDefinition(id, true);
        validatedParameters(definition.getTaskType(), currentVersion(definition).getParameters());
        definition.setStatus(DefinitionStatus.PUBLISHED);
        definition.initUpdate(operatorId);
        definitionRepository.update(definition.getWorkspaceId(), definition);
        return toVO(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVO unpublish(String id, String operatorId) {
        DefinitionEntity definition = requireDefinition(id, true);
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        definition.initUpdate(operatorId);
        definitionRepository.update(definition.getWorkspaceId(), definition);
        return toVO(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String operatorId) {
        DefinitionEntity definition = requireDefinition(id, true);
        requireUnpublished(definition);
        if (!definitionRepository.softDelete(
                definition.getWorkspaceId(), id, DateUtils.now(),
                StringUtils.trimToNull(operatorId) == null ? "system" : operatorId)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
    }


    @Override
    public DefinitionVO query(String id) {
        return toVO(requireDefinition(id, false));
    }

    @Override
    public PagingData<DefinitionVO> queryPage(DefinitionQueryDTO dto) {
        if (dto == null || dto.getPageNo() == null || dto.getPageSize() == null
                || dto.getPageNo() < 1 || dto.getPageSize() < 1 || dto.getPageSize() > 200
                || !dto.getSorts().isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        Map<String, String> filters = dto.getParameterFilters() == null ? Map.of() : dto.getParameterFilters();
        if (filters.size() > 16 || filters.keySet().stream().anyMatch(k -> k == null || !k.matches("[A-Za-z][A-Za-z0-9_]*"))) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        DefinitionPageQuery filter = new DefinitionPageQuery(
                WorkspaceContext.requireWorkspaceId(), dto.getPageNo(), dto.getPageSize(),
                StringUtils.trimToNull(dto.getKeyword()), StringUtils.trimToNull(dto.getTaskType()),
                dto.getStatus() == null ? null : dto.getStatus().getValue(), filters);
        return PagingData.from(definitionRepository.queryPage(filter).map(this::toVO));
    }

    @Override
    public List<DefinitionVersionVO> queryVersions(String id) {
        DefinitionEntity definition = requireDefinition(id, false);
        return versionRepository.queryByDefinition(definition.getWorkspaceId(), definition.getId()).stream()
                .map(v -> {
                    DefinitionVersionVO vo = BeanCopyUtils.copy(v, DefinitionVersionVO.class);
                    return vo;
                })
                .toList();
    }


    private DefinitionEntity requireDefinition(String id, boolean forUpdate) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return (forUpdate ? definitionRepository.queryByIdForUpdate(workspaceId, id)
                        : definitionRepository.queryById(workspaceId, id))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private DefinitionVO toVO(DefinitionEntity entity) {
        DefinitionVersionEntity version = versionRepository
                .queryCurrent(entity.getWorkspaceId(), entity.getId(), entity.getCurrentVersion())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        DefinitionVO vo = BeanCopyUtils.copy(entity, DefinitionVO.class);
        vo.setStatus(entity.getStatus().name());
        vo.setDefinitionVersion(entity.getCurrentVersion());
        vo.setParameters(version.getParameters());
        vo.setParameterSchemaVersion(version.getParameterSchemaVersion());
        return vo;
    }

    private String validatedParameters(String type, String json) {
        return JSONUtils.toJson(pluginRegistry.validateDefinition(type, json, validationContext));
    }

    private void appendVersion(DefinitionEntity entity, String json, String operator) {
        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setWorkspaceId(entity.getWorkspaceId());
        version.setDefinitionId(entity.getId());
        version.setVersion(entity.getCurrentVersion());
        version.setParameterSchemaVersion(pluginRegistry.get(entity.getTaskType()).parameterSchemaVersion());
        version.setParameters(json);
        version.initCreate(operator);
        versionRepository.append(version);
    }

    private String requiredName(String name) {
        String normalized = StringUtils.trimToNull(name);
        if (normalized == null || normalized.length() > 128) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        return normalized;
    }

    private void checkNameAvailable(String workspaceId, String name, String excludeId) {
        if (definitionRepository.existsByName(workspaceId, name, excludeId)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }
    }

}
