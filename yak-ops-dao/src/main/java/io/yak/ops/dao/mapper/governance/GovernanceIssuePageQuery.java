package io.yak.ops.dao.mapper.governance;

import lombok.Getter;
import lombok.Setter;

/** Filter and window of one page of the Issue board. Blank strings mean "no filter". */
@Getter
@Setter
public class GovernanceIssuePageQuery {
    /** OPEN, HANDLING, IGNORED or RESOLVED. */
    private String status;
    /** HIGH, MEDIUM or LOW. */
    private String severity;
    /** DEV or COMPUTE. */
    private String dimension;
    /** Stable rule code. */
    private String ruleCode;
    /** Assigned Workspace member. */
    private String ownerId;
    /** DATA_MAP_TABLE or TASK_DEFINITION. */
    private String targetType;
    /** Identity of the governed object. */
    private String targetId;
    /** Substring of the target name. */
    private String keyword;
    /** Rows to skip. */
    private int offset;
    /** Rows to return. */
    private int limit;
}
