package io.yak.ops.boot.config;

import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Installs the datasource plugin registry from providers on the application classpath. */
@Configuration(proxyBeanMethods = false)
public class DataSourcePluginConfiguration {

    @Bean
    DataSourcePluginRegistry dataSourcePluginRegistry() {
        return new DataSourcePluginRegistry();
    }
}
