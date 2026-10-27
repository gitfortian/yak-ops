package io.yak.ops.business.datamap.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of crawl execution and bounded preview.
 *
 * <p>Defaults are the values FYOIP-15 fixes: two crawl workers with a queue of sixty-four, a
 * thirty-minute run budget, a two-thousand-table ceiling, and a preview of one hundred rows,
 * one megabyte and four concurrent queries. The ceiling and the row cap are not configurable
 * because the proposal states them as product guarantees.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "yak.data-map")
public class DataMapProperties {

    /** Crawl worker defaults. */
    private final Crawl crawl = new Crawl();

    /** Preview admission defaults. */
    private final Preview preview = new Preview();

    /** Crawl execution settings. */
    @Getter
    @Setter
    public static class Crawl {

        /** Number of workers; the datasource scope lock keeps them on different datasources. */
        private int workerCount = 2;

        /** Queue capacity; a full queue rejects the submission with the busy error. */
        private int queueCapacity = 64;

        /** Wall-clock budget of one run, checked between metadata calls. */
        private Duration budget = Duration.ofMinutes(30);
    }

    /** Preview admission settings. */
    @Getter
    @Setter
    public static class Preview {

        /** Maximum preview queries in flight on this node. */
        private int maxConcurrentQueries = 4;

        /** Seconds before the statement timeout cancels the sample. */
        private int timeoutSeconds = 5;

        /** Characters retained per text cell. */
        private int cellCharacters = 200;
    }
}
