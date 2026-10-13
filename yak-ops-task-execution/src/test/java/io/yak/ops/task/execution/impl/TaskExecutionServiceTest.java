package io.yak.ops.task.execution.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.SubmissionService;
import io.yak.ops.business.task.submission.TaskSubmissionCommand;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.task.execution.dispatch.TaskExecutionCoordinator;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/** Verifies durable admission and idempotent dispatch without an HTTP Workspace context. */
class TaskExecutionServiceTest {

    private final TaskExecutionServiceImpl service = new TaskExecutionServiceImpl();
    private final SubmissionService submissions = mock(SubmissionService.class);
    private final TaskExecutionCoordinator dispatcher = mock(TaskExecutionCoordinator.class);

    TaskExecutionServiceTest() {
        inject("submissions", submissions);
        inject("dispatcher", dispatcher);
    }

    @Test
    void dispatchesNewSubmissionAfterAdmission() {
        TaskSubmissionCommand command = manual();
        SubmissionResult result = new SubmissionResult("i", 2, TaskInstanceStatus.SUBMITTED, false);
        when(submissions.submit(command)).thenReturn(result);

        assertEquals(result, service.submit(command));

        verify(dispatcher).dispatch("ws", "i");
    }

    @Test
    void idempotentReplayDoesNotDispatchAgain() {
        TaskSubmissionCommand command = manual();
        SubmissionResult result = new SubmissionResult("i", 2, TaskInstanceStatus.RUNNING, true);
        when(submissions.submit(command)).thenReturn(result);

        assertEquals(result, service.submit(command));

        verify(dispatcher, never()).dispatch("ws", "i");
    }

    @Test
    void workflowSubmissionDispatchesInItsExplicitWorkspace() {
        TaskSubmissionCommand command =
                new TaskSubmissionCommand("workflow-workspace", "d", TaskTriggerType.WORKFLOW, "workflow-123", "system");
        SubmissionResult result = new SubmissionResult("i", 2, TaskInstanceStatus.SUBMITTED, false);
        when(submissions.submit(command)).thenReturn(result);

        assertEquals(result, service.submit(command));

        verify(dispatcher).dispatch("workflow-workspace", "i");
    }

    @Test
    void cancellationUsesTheDispatcherFence() {
        when(dispatcher.cancel("ws", "i")).thenReturn(TaskInstanceStatus.CANCELLING);
        assertEquals(TaskInstanceStatus.CANCELLING, service.cancel("ws", "i"));
    }

    private TaskSubmissionCommand manual() {
        return new TaskSubmissionCommand("ws", "d", TaskTriggerType.MANUAL, "request-123", "user");
    }

    private void inject(String fieldName, Object dependency) {
        try {
            Field field = TaskExecutionServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
