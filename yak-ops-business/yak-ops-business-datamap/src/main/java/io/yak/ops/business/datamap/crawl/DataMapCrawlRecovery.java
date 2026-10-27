package io.yak.ops.business.datamap.crawl;

/**
 * Startup recovery of runs interrupted by a process stop.
 *
 * <p>Implemented by the crawl service and invoked once per start, before any submission is
 * admitted. It is not exposed over HTTP.
 */
public interface DataMapCrawlRecovery {

    /** Fails every active run and releases its datasource scope lock. */
    void recoverInterruptedRuns();
}
