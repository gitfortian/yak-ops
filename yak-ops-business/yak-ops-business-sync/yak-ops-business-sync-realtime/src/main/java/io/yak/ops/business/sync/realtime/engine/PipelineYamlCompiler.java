package io.yak.ops.business.sync.realtime.engine;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PipelineYamlCompiler {
 private static final Pattern SAFE=Pattern.compile("[A-Za-z0-9_.$:/?=&,*-]+");
 public String compile(String name,CdcPipelineSpec s){
  StringBuilder y=new StringBuilder("pipeline:\n  name: ").append(q(name)).append("\n  parallelism: ").append(s.parallelism()).append("\n");
  y.append("  schema.change.behavior: ").append(s.schemaEvolution()?"evolve":"ignore").append("\nsource:\n  type: mysql\n")
   .append("  hostname: ").append(q(s.source().hostname())).append("\n  port: ").append(s.source().port()).append("\n  username: ").append(q(s.source().username()))
   .append("\n  password: ${ENV:SOURCE_PASSWORD}\n  database-name: ").append(q(s.source().database())).append("\n  table-name: ").append(q(tablePattern(s)))
   .append("\n  server-id: ").append(q(s.source().serverId())).append("\n  scan.startup.mode: ").append(s.startupMode()).append("\n");
  y.append("sink:\n  type: yak-jdbc\n  url: ").append(q(s.sink().url())).append("\n  driver: ").append(q(s.sink().driver())).append("\n  username: ").append(q(s.sink().username()))
   .append("\n  password: ${ENV:SINK_PASSWORD}\n  dialect: ").append(s.sink().dialect()).append("\n  max-retries: ").append(s.sink().maxRetries()).append("\n  batch-size: ").append(s.sink().batchSize())
   .append("\n  flush-interval-ms: ").append(s.sink().flushIntervalMs()).append("\n  max-batch-bytes: ").append(s.sink().maxBatchBytes()).append("\n  statement-cache-size: ").append(s.sink().statementCacheSize()).append("\n  replay-safety: ").append(s.sink().replaySafety()).append("\n");
  y.append("route:\n"); for(var r:s.tables()) y.append("  - source-table: ").append(q(r.source())).append("\n    sink-table: ").append(q(r.sink())).append("\n");
  y.append("restart-strategy:\n  type: ").append(s.restart().strategy()).append("\n  attempts: ").append(s.restart().attempts()).append("\n  delay-ms: ").append(s.restart().delayMs()).append("\n");
  return y.toString();
 }
 private String tablePattern(CdcPipelineSpec s){return String.join(",",s.tables().stream().map(CdcPipelineSpec.TableRoute::source).toList());}
 private String q(String v){ if(v==null||v.contains("\n")||v.contains("\r")) throw new IllegalArgumentException("invalid YAML scalar"); return SAFE.matcher(v).matches()?v:"'"+v.replace("'","''")+"'"; }
}
