package io.yak.ops.dao.mapper.task;

import lombok.Getter;
import lombok.Setter;

/** Raw slow-run candidate of one published Task definition. */
@Getter
@Setter
public class TaskSlowCandidateRow {
    /** Definition identity. */
    private String definitionId;
    /** Definition display name. */
    private String displayName;
    /** Average wall-clock seconds of the finished scheduled instances in the window. */
    private Long averageSeconds;
    /** Finished scheduled instances the average was computed from. */
    private Integer sampleCount;
}
