package io.yak.ops.boot.config;

import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Discovers Task plugins once during application startup using their direct ServiceLoader SPI.
 *
 * <p>Duplicate types are rejected before handling Task Definition requests. This configuration
 * does not start tasks or connect them to YakFlow Runtime.
 */
@Configuration(proxyBeanMethods = false)
public class TaskPluginConfiguration {

    @Bean
    TaskPluginRegistry taskPluginRegistry() {
        return new TaskPluginRegistry();
    }
}
