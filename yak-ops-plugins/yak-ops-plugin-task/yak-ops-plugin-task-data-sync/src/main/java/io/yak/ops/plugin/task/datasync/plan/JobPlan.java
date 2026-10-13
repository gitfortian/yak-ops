package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.datasync.resource.RuntimeTable;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.util.Objects;

/**
 * Immutable DATA_SYNC plan containing pinned endpoint parameters, resolved table metadata and
 * admitted resource claims.
 *
 * <p>This value contains no connection credentials, physical Connector instances or running
 * execution handles. Runtime connections are resolved only after admission.
 *
 * @param sourceParameters selected Source definition
 * @param sinkParameters selected Sink definition
 * @param source authorized Source table and logical schema
 * @param target authorized target table and logical schema
 * @param admissionPlan exclusive target claim and resource binding fingerprint
 */
public record JobPlan(
        SourceParameters sourceParameters,
        SinkParameters sinkParameters,
        RuntimeTable source,
        RuntimeTable target,
        TaskAdmissionPlan admissionPlan) {

    public JobPlan {
        Objects.requireNonNull(sourceParameters, "sourceParameters");
        Objects.requireNonNull(sinkParameters, "sinkParameters");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(admissionPlan, "admissionPlan");
    }
}
