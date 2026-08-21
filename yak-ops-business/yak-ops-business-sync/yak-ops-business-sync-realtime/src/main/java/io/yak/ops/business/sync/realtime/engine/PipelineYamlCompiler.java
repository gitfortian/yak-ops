package io.yak.ops.business.sync.realtime.engine;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Compiles a password-free logical spec plus ephemeral datasource coordinates to Pipeline YAML. */
@Component
public class PipelineYamlCompiler {

  private static final Pattern SAFE_SCALAR = Pattern.compile("[A-Za-z0-9_.$:/?=&,*-]+");
  private static final Pattern ENV_NAME = Pattern.compile("[A-Z_][A-Z0-9_]*");

  private final RealtimeSyncProperties properties;

  public PipelineYamlCompiler(RealtimeSyncProperties properties) {
    this.properties = properties;
  }

  public CompiledPipeline compile(String name, CdcPipelineSpec spec, ResolvedCdcPipeline resolved) {
    String sourcePassword = envReference(properties.getSourcePasswordEnv());
    String sinkPassword = envReference(properties.getSinkPasswordEnv());
    ResolvedCdcPipeline.Endpoint source = resolved.source();
    ResolvedCdcPipeline.Endpoint sink = resolved.sink();

    StringBuilder yaml = new StringBuilder();
    yaml.append("pipeline:\n")
        .append("  name: ")
        .append(quote(name))
        .append('\n')
        .append("  parallelism: ")
        .append(spec.parallelism())
        .append('\n')
        .append("  schema.change.behavior: ")
        .append(schemaBehavior(spec.schemaEvolution()))
        .append('\n');

    yaml.append("source:\n")
        .append("  type: mysql\n")
        .append("  hostname: ")
        .append(quote(source.host()))
        .append('\n')
        .append("  port: ")
        .append(source.port())
        .append('\n')
        .append("  username: ")
        .append(quote(source.username()))
        .append('\n')
        .append("  password: ")
        .append(sourcePassword)
        .append('\n')
        .append("  tables: ")
        .append(quote(tablePattern(spec, source.database())))
        .append('\n')
        .append("  scan.startup.mode: ")
        .append(spec.startupMode())
        .append('\n');

    yaml.append("sink:\n")
        .append("  type: yak-jdbc\n")
        .append("  url: ")
        .append(quote(sink.jdbcUrl()))
        .append('\n')
        .append("  driver: ")
        .append(quote(sink.driver()))
        .append('\n')
        .append("  username: ")
        .append(quote(sink.username()))
        .append('\n')
        .append("  password: ")
        .append(sinkPassword)
        .append('\n')
        .append("  dialect: ")
        .append(dialect(sink.dbType()))
        .append('\n')
        .append("  max-retries: ")
        .append(spec.sink().maxRetries())
        .append('\n')
        .append("  batch-size: ")
        .append(spec.sink().batchSize())
        .append('\n')
        .append("  flush-interval-ms: ")
        .append(spec.sink().flushIntervalMs())
        .append('\n')
        .append("  max-batch-bytes: ")
        .append(spec.sink().maxBatchBytes())
        .append('\n')
        .append("  statement-cache-size: ")
        .append(spec.sink().statementCacheSize())
        .append('\n')
        .append("  replay-safety: ")
        .append(spec.sink().strictReplaySafety() ? "strict" : "allow-append-only")
        .append('\n');

    yaml.append("route:\n");
    for (CdcPipelineSpec.TableRoute route : spec.tables()) {
      yaml.append("  - source-table: ")
          .append(quote(Pattern.quote(source.database()) + "." + tableSelector(route)))
          .append('\n')
          .append("    sink-table: ")
          .append(quote(route.sinkTable()))
          .append('\n');
    }

    return new CompiledPipeline(yaml.toString(), summary(name, spec, resolved));
  }

  private String schemaBehavior(CdcPipelineSpec.SchemaEvolution behavior) {
    return switch (behavior) {
      case EVOLVE -> "evolve";
      case IGNORE -> "ignore";
      case FAIL -> "exception";
    };
  }

  private String tablePattern(CdcPipelineSpec spec, String database) {
    String databaseSelector = Pattern.quote(database) + ".";
    return String.join(
        ", ",
        spec.tables().stream().map(route -> databaseSelector + tableSelector(route)).toList());
  }

  private String tableSelector(CdcPipelineSpec.TableRoute route) {
    return route.matchMode() == CdcPipelineSpec.MatchMode.REGEX
        ? escapeSelectorSeparators(route.sourceTable())
        : Pattern.quote(route.sourceTable());
  }

  /** Protects regex dots/commas from Flink CDC's table-identifier tokenizer. */
  private String escapeSelectorSeparators(String expression) {
    return expression.replace(",", "\\,").replace(".", "\\.");
  }

  private String dialect(DataSourceDbType type) {
    return type == DataSourceDbType.POSTGRE_SQL ? "postgres" : "mysql";
  }

  private String envReference(String name) {
    if (name == null || !ENV_NAME.matcher(name).matches()) {
      throw new IllegalStateException("Runtime 密码环境变量名配置无效");
    }
    return "${ENV:" + name + "}";
  }

  private String quote(String value) {
    if (value == null || value.contains("\n") || value.contains("\r")) {
      throw new IllegalArgumentException("Pipeline YAML 字段不能为空或包含换行");
    }
    return SAFE_SCALAR.matcher(value).matches() ? value : "'" + value.replace("'", "''") + "'";
  }

  private String summary(String name, CdcPipelineSpec spec, ResolvedCdcPipeline resolved) {
    return name
        + " | mysql#"
        + resolved.source().dataSourceId()
        + " -> "
        + dialect(resolved.sink().dbType())
        + "#"
        + resolved.sink().dataSourceId()
        + " | tables="
        + spec.tables().size()
        + " | startup="
        + spec.startupMode();
  }

  public record CompiledPipeline(String yaml, String summary) {}
}
