package io.yak.ops.task.execution.impl;

import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.SubmissionService;
import io.yak.ops.business.task.submission.TaskSubmissionCommand;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.task.execution.TaskExecutionService;
import io.yak.ops.task.execution.dispatch.TaskExecutionCoordinator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** Dispatches admitted Task submissions through the configured execution coordinator. */
@Service
public class TaskExecutionServiceImpl implements TaskExecutionService {

    @Resource
    private SubmissionService submissions;

    @Resource
    private TaskExecutionCoordinator dispatcher;

    @Override
    public SubmissionResult submit(TaskSubmissionCommand command) {
        SubmissionResult result = submissions.submit(command);
        if (!result.replayed()) {
            dispatcher.dispatch(command.workspaceId(), result.instanceId());
        }
        return result;
    }

    @Override
    public TaskInstanceStatus cancel(String workspaceId, String instanceId) {
        return dispatcher.cancel(workspaceId, instanceId);
    }
}
