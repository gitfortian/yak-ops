package io.yak.ops.business.task.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdmissionRequestTest {

    @Test
    void preservesAdmittedInputsAndExecutablePluginIdentity() {
        TaskExecutionContext context =
                new TaskExecutionContext("workspace", "definition", 2, "instance", "attempt");
        TaskParameters parameters = mock(TaskParameters.class);
        ExecutableTaskPlugin plugin = mock(ExecutableTaskPlugin.class);
        TaskExecutionPlan plan = new TaskExecutionPlan(List.of(), "resource-revision");

        AdmissionRequest request =
                new AdmissionRequest(context, "private-request-key", "intent", "actor", parameters, plan, plugin);
        AdmissionRequest another =
                new AdmissionRequest(context, "private-request-key", "intent", "actor", parameters, plan, plugin);

        assertSame(context, request.context());
        assertEquals("private-request-key", request.requestId());
        assertEquals("intent", request.requestFingerprint());
        assertEquals("actor", request.operatorId());
        assertSame(parameters, request.parameters());
        assertSame(plan, request.plan());
        assertSame(plugin, request.plugin());
        assertNotEquals(request, another);
        assertFalse(request.getClass().isRecord());
        verifyNoInteractions(plugin);
    }

    @Test
    void diagnosticsNeverInvokePluginOrRenderSubmissionInputs() {
        ExecutableTaskPlugin plugin = mock(ExecutableTaskPlugin.class);
        TaskParameters parameters = mock(TaskParameters.class);
        AdmissionRequest request = new AdmissionRequest(
                new TaskExecutionContext("workspace", "definition", 2, "instance", "attempt"),
                "private-request-key",
                "private-fingerprint",
                "sensitive-actor",
                parameters,
                new TaskExecutionPlan(List.of(), "private-resource-revision"),
                plugin);

        String diagnostic = request.toString();
        assertTrue(diagnostic.contains("redacted"));
        assertFalse(diagnostic.contains("private-request-key"));
        assertFalse(diagnostic.contains("private-fingerprint"));
        assertFalse(diagnostic.contains("private-resource-revision"));
        assertFalse(diagnostic.contains("sensitive-actor"));
        verifyNoInteractions(plugin);
        verifyNoInteractions(parameters);
    }
}
