package io.yak.ops.dao.mapper.task;

import lombok.Getter;
import lombok.Setter;

/** Raw consecutive-failure candidate of one published Task definition. */
@Getter
@Setter
public class TaskFailureCandidateRow {
    /** Definition identity. */
    private String definitionId;
    /** Definition display name. */
    private String displayName;
    /** Length of the leading run of FAILED terminal scheduled instances. */
    private Integer consecutiveFailures;
    /** Terminal scheduled instances considered inside the window. */
    private Integer sampleCount;
}
