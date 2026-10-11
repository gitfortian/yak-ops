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

    // COMMAND_METHODS

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

    // INTERNAL_HELPERS
}
