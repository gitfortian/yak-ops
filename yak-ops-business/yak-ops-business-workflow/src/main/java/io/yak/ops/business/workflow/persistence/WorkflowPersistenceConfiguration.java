package io.yak.ops.business.workflow.persistence;

import io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Workflow persistence shares the Yak Ops business database but owns its Flyway history. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(BusinessDatabaseConfiguration.class)
public class WorkflowPersistenceConfiguration {

  @Bean(initMethod = "migrate", name = "workflowFlyway")
  public Flyway workflowFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-workflow")
        .table("yak_workflow_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
