package io.yak.ops.dao.mapper.governance;

import lombok.Getter;
import lombok.Setter;

/** Raw Issue count of one status. */
@Getter
@Setter
public class GovernanceStatusCountRow {
    /** OPEN, HANDLING, IGNORED or RESOLVED. */
    private String status;
    /** Number of Issues in that status. */
    private Integer total;
}
