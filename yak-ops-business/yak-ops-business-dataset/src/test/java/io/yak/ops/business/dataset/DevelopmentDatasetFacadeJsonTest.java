package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Field description must stay visible in JSON even when unset (global non_null inclusion). */
class DevelopmentDatasetFacadeJsonTest {

  private final ObjectMapper mapper =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  @Test
  void fieldSnapshotKeepsDescriptionKeyWhenNull() throws Exception {
    DevelopmentDatasetFacade.FieldSnapshot snapshot =
        new DevelopmentDatasetFacade.FieldSnapshot(
            "f-1", "order_id", "order_id", "STRING", false, null, null, "DIMENSION", 1);
    String json = mapper.writeValueAsString(snapshot);
    assertTrue(json.contains("\"description\":null"), json);
    assertTrue(json.contains("\"comment\":null"), json);
  }

  @Test
  void fieldDraftKeepsDescriptionKeyWhenNull() throws Exception {
    DevelopmentDatasetFacade.FieldDraft draft =
        new DevelopmentDatasetFacade.FieldDraft(
            null, "order_id", "order_id", "STRING", false, null, null, "DIMENSION");
    String json = mapper.writeValueAsString(draft);
    assertTrue(json.contains("\"description\":null"), json);
    assertTrue(json.contains("\"comment\":null"), json);
  }

  @Test
  void fieldSnapshotSerializesDescriptionValue() throws Exception {
    DevelopmentDatasetFacade.FieldSnapshot snapshot =
        new DevelopmentDatasetFacade.FieldSnapshot(
            "f-1", "order_id", "order_id", "STRING", false, "订单ID", "订单编号", "DIMENSION", 1);
    String json = mapper.writeValueAsString(snapshot);
    assertTrue(json.contains("\"description\":\"订单ID\""), json);
    assertTrue(json.contains("\"comment\":\"订单编号\""), json);
  }
}
