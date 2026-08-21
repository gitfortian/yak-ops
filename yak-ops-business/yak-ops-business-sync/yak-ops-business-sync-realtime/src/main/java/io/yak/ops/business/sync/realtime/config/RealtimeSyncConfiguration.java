package io.yak.ops.business.sync.realtime.config;
import com.fasterxml.jackson.databind.ObjectMapper;import io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration;import java.net.http.HttpClient;import javax.sql.DataSource;import org.flywaydb.core.Flyway;import org.springframework.beans.factory.annotation.Qualifier;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.springframework.context.annotation.*;import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration(proxyBeanMethods=false) @EnableScheduling @EnableConfigurationProperties(RealtimeSyncProperties.class) @Import(BusinessDatabaseConfiguration.class) @ConditionalOnProperty(prefix="yak.sync.realtime",name="enabled",matchIfMissing=true)
public class RealtimeSyncConfiguration {
 @Bean(initMethod="migrate") Flyway realtimeSyncFlyway(@Qualifier("yakBusinessDataSource") DataSource d){return Flyway.configure().dataSource(d).locations("classpath:db/migration/yak-realtime-sync").table("yak_realtime_schema_history").baselineOnMigrate(true).load();}
 @Bean(name="realtimeHttpClient") HttpClient realtimeHttpClient(RealtimeSyncProperties p){return HttpClient.newBuilder().connectTimeout(p.getConnectTimeout()).build();}
 @Bean(name="realtimeObjectMapper") ObjectMapper realtimeObjectMapper(){return new ObjectMapper().findAndRegisterModules();}
}
