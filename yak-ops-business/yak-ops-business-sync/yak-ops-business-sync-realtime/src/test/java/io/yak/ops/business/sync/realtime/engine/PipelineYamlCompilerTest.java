package io.yak.ops.business.sync.realtime.engine;
import static org.assertj.core.api.Assertions.*;import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;import java.util.List;import org.junit.jupiter.api.Test;
class PipelineYamlCompilerTest {
 @Test void mapsRuntimeOptionsWithoutSecrets(){var s=new CdcPipelineSpec(1L,2L,new CdcPipelineSpec.Source("mysql",3306,"reader","shop","5400-5408"),new CdcPipelineSpec.Sink("jdbc:postgresql://pg/dw","org.postgresql.Driver","writer","postgres",3,100,1000,1048576,20,true),List.of(new CdcPipelineSpec.TableRoute("shop.orders","public.orders")),"initial",true,2,new CdcPipelineSpec.Restart("fixed-delay",3,1000));String y=new PipelineYamlCompiler().compile("orders",s);assertThat(y).contains("type: mysql","type: yak-jdbc","dialect: postgres","password: ${ENV:SOURCE_PASSWORD}","password: ${ENV:SINK_PASSWORD}","max-batch-bytes: 1048576","replay-safety: true").doesNotContain("secret");}
}
