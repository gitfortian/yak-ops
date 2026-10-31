package io.yak.ops.dao.mapper.datamap;

import lombok.Getter;
import lombok.Setter;

/** Quality figures of one run window: today's ratio, a seven-day success rate and run duration. */
@Getter
@Setter
public class DataMapRunQualityRow {
    /** Terminal runs submitted today, every outcome. */
    private Long todayTotal;
    /** Terminal runs submitted today that ended succeeded. */
    private Long todaySucceeded;
    /** Terminal runs submitted in the last seven days, every outcome. */
    private Long weekTotal;
    /** Terminal runs submitted in the last seven days that ended succeeded. */
    private Long weekSucceeded;
    /** Average duration of the successful runs in the last seven days, in seconds. */
    private Long avgSeconds;
}
