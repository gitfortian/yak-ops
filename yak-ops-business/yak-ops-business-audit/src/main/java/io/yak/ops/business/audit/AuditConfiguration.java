package io.yak.ops.business.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.project.CurrentProject;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Wires the shared audit store onto the Yak Ops business database. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(name = "yakBusinessDataSource")
public class AuditConfiguration {

  @Bean(initMethod = "migrate")
  public Flyway yakAuditFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-audit")
        .table("yak_audit_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }

  @Bean
  public BusinessAuditService businessAuditService(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager,
      CurrentProject currentProject,
      ObjectMapper objectMapper) {
    return new JdbcBusinessAuditService(dataSource, transactionManager, currentProject, objectMapper);
  }
}
