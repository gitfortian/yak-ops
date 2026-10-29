package io.yak.ops.dao.mapper.governance;

import lombok.Getter;
import lombok.Setter;

/** Raw severity count of the score-bearing Issues of one dimension. */
@Getter
@Setter
public class GovernanceSeverityCountRow {
    /** HIGH, MEDIUM or LOW. */
    private String severity;
    /** Number of Issues carrying that severity. */
    private Integer total;
}
