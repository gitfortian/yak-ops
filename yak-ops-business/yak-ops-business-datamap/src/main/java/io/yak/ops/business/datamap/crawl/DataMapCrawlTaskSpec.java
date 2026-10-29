package io.yak.ops.business.datamap.crawl;

import java.util.List;

/**
 * Configuration snapshot handed from a task to one run.
 *
 * <p>The crawl executor never reads the task row: the exclusions this spec carries are exactly the
 * rules the run applies, so editing a task cannot change a run that already started.
 *
 * @param taskId task that produced the run
 * @param datasourceId datasource whose namespace is enumerated
 * @param database catalog slot to enumerate, required
 * @param schema schema slot to enumerate, or null for the datasource binding
 * @param excludePattern Java regex of table names kept alive but not re-crawled
 * @param excludeTables exact table names kept alive but not re-crawled
 */
public record DataMapCrawlTaskSpec(
        String taskId,
        String datasourceId,
        String database,
        String schema,
        String excludePattern,
        List<String> excludeTables) {}
