package io.yak.ops.business.datamap.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wires the dedicated crawl executor and the preview semaphore.
 *
 * <p>The executor is not the shared {@code @Async} pool: a crawl must not compete with request
 * threads. Two workers are safe only because one datasource can hold one active run, so the pool
 * can never widen the load on a single target database.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataMapProperties.class)
public class DataMapConfiguration {

    /** Crawl executor used exclusively by the data map worker. */
    @Bean("dataMapCrawlExecutor")
    ThreadPoolTaskExecutor dataMapCrawlExecutor(DataMapProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCrawl().getWorkerCount());
        executor.setMaxPoolSize(properties.getCrawl().getWorkerCount());
        executor.setQueueCapacity(properties.getCrawl().getQueueCapacity());
        executor.setThreadNamePrefix("data-map-crawl-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
