package io.yak.ops.business.task.submission.impl;

import io.yak.ops.business.task.submission.AdmissionRequest;
import io.yak.ops.business.task.submission.AdmissionService;
import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.SubmissionService;
import io.yak.ops.business.task.submission.TaskSubmissionCommand;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.validation.TaskValidationContextFactory;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Validates a request and prepares a read-only execution plan before durable admission.
 *
 * <p>Does not submit a physical Job; dispatch follows durable admission.
 */
@Service
public class SubmissionServiceImpl implements SubmissionService {
    @Resource
    private DefinitionRepository definitions;

    @Resource
    private DefinitionVersionRepository versions;

    @Resource
    private InstanceRepository instances;

    @Resource
    private AttemptRepository attempts;

    @Resource
    private TaskPluginRegistry plugins;

    @Resource
    private TaskValidationContextFactory validationContexts;

    @Resource
    private AdmissionService admission;

    @Override
    public SubmissionResult submit(TaskSubmissionCommand command) {
        if (command == null
                || command.workspaceId() == null
                || command.workspaceId().isBlank()
                || command.triggerType() == null
                || command.definitionId() == null
                || command.definitionId().isBlank()
                || command.requestId() == null
                || !command.requestId().matches("[A-Za-z0-9_-]{8,128}")) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        String workspaceId = command.workspaceId();
        String definitionId = command.definitionId();
        String requestId = command.requestId();
        String fingerprint = command.triggerType().name() + ":" + definitionId;
        var existing = instances.findByRequest(workspaceId, command.triggerType(), requestId);
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), fingerprint);
        }

        DefinitionEntity definition = definitions
                .queryById(workspaceId, definitionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        if (definition.getStatus() != DefinitionStatus.PUBLISHED) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        TaskPlugin plugin = plugins.get(definition.getTaskType());
        if (!(plugin instanceof ExecutableTaskPlugin executable)) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        int version = definition.getCurrentVersion();
        var snapshot = versions.queryCurrent(workspaceId, definitionId, version)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        ReadonlyConfig parameters = executable.parse(snapshot.getParameters());
        executable.validate(parameters, validationContexts.forWorkspace(workspaceId));
        TaskExecutionIdentity context =
                new TaskExecutionIdentity(workspaceId, definitionId, version, IdUtils.nextId(), IdUtils.nextId());
        TaskAdmissionPlan plan = executable.plan(parameters, context);
        AdmissionRequest request = new AdmissionRequest(
                context,
                command.triggerType(),
                requestId,
                fingerprint,
                command.operatorId(),
                parameters,
                plan,
                executable);
        try {
            return admission.admit(request);
        } catch (DuplicateKeyException duplicate) {
            return instances
                    .findByRequest(workspaceId, command.triggerType(), requestId)
                    .map(instance -> replay(instance, fingerprint))
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION));
        }
    }

    private SubmissionResult replay(InstanceEntity instance, String fingerprint) {
        if (!fingerprint.equals(instance.getRequestFingerprint())) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }
        List<AttemptEntity> previous = attempts.findByInstance(instance.getWorkspaceId(), instance.getId());
        if (previous.isEmpty()) {
            throw new IllegalStateException("Task Instance has no persisted Attempt");
        }
        return new SubmissionResult(
                instance.getId(),
                previous.getFirst().getId(),
                instance.getDefinitionVersion(),
                instance.getStatus(),
                true);
    }
}
