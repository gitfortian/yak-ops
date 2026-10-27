package io.yak.ops.common.bean.vo.datamap;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One crawl run with up to two hundred per-table failures. */
@Getter
@Setter
public class DataMapCrawlDetailVO {

    /** Run state and counters. */
    private DataMapCrawlVO crawl;

    /** Failed tables, oldest first. */
    private List<DataMapCrawlFailureVO> failures = new ArrayList<>();
}
