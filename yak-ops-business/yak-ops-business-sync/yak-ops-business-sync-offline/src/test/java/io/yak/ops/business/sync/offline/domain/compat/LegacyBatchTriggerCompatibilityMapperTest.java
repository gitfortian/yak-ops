package io.yak.ops.business.sync.offline.domain.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LegacyBatchTriggerCompatibilityMapperTest {

  @Test
  void scheduleTokenCarriesStableScheduleIdentity() {
    Instant plannedFireTime = Instant.parse("2026-08-23T03:15:00Z");

    String token = LegacyBatchTriggerCompatibilityMapper.scheduleToken(
        "offline-sync:42", plannedFireTime);
    LegacyBatchTriggerCompatibilityMapper.Mapping mapping =
        LegacyBatchTriggerCompatibilityMapper.parse(token);

    assertEquals("SCHEDULE", mapping.legacyTriggerType());
    assertEquals(BatchTrigger.SCHEDULE, mapping.batchTrigger());
    assertEquals(BatchKey.schedule("offline-sync:42", plannedFireTime), mapping.batchKey());
  }
}
