package io.yak.ops.business.sync.realtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RealtimeJobListQueryTest {

  @Test
  void acceptsMissingSpecForTwoStageDrafts() throws Exception {
    RealtimeJobListQuery query =
        new RealtimeJobListQuery(Mockito.mock(DataSource.class), new ObjectMapper());
    Method readSpec = RealtimeJobListQuery.class.getDeclaredMethod("readSpec", String.class);
    readSpec.setAccessible(true);

    assertThat((CdcPipelineSpec) readSpec.invoke(query, new Object[] {null})).isNull();
    assertThat((CdcPipelineSpec) readSpec.invoke(query, "  ")).isNull();
  }
}
