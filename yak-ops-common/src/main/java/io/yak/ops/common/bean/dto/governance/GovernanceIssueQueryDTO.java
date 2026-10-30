package io.yak.ops.common.bean.dto.governance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Filter of the governance Issue board; every field is optional. */
@Getter
@Setter
public class GovernanceIssueQueryDTO {

    /** OPEN, HANDLING, IGNORED or RESOLVED. */
    @Size(max = 16)
    private String status;

    /** HIGH, MEDIUM or LOW. */
    @Size(max = 16)
    private String severity;

    /** DEV or COMPUTE. */
    @Size(max = 16)
    private String dimension;

    /** Stable rule code compiled into the product. */
    @Size(max = 64)
    private String ruleCode;

    /** Assigned Workspace member. */
    @Size(max = 64)
    private String ownerId;

    /** DATA_MAP_TABLE or TASK_DEFINITION; the data map tab sends both with a target identity. */
    @Size(max = 24)
    private String targetType;

    /** Identity of the governed object. */
    @Size(max = 64)
    private String targetId;

    /** Matched against the target name. */
    @Size(max = 128)
    private String keyword;

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 20;
}
