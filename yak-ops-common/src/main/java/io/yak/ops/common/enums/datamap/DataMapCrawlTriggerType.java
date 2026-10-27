package io.yak.ops.common.enums.datamap;

/**
 * How one crawl run was requested.
 *
 * <p>{@code SCHEDULED} is written by the platform scheduler path once it exists; runs submitted
 * from the console are always {@code MANUAL}. The value is provenance for the list, not authorisation.
 */
public enum DataMapCrawlTriggerType {
    MANUAL,
    SCHEDULED
}
