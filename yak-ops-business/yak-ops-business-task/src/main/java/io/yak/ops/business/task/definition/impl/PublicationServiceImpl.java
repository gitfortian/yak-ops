package io.yak.ops.business.task.definition.impl;

import io.yak.ops.business.task.definition.PublicationService;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pins the externally validated version and updates publication state atomically. */
@Service
public class PublicationServiceImpl implements PublicationService {

    @Resource
    private DefinitionRepository definitions;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionEntity publish(String id, int expectedVersion, String operatorId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DefinitionEntity definition = definitions
                .queryByIdForUpdate(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        if (!Integer.valueOf(expectedVersion).equals(definition.getCurrentVersion())) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }
        definition.setStatus(DefinitionStatus.PUBLISHED);
        definition.initUpdate(operatorId);
        if (definitions.update(workspaceId, definition) == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        return definition;
    }
}
