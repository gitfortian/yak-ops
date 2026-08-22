package io.yak.ops.business.sync.realtime.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeDefinitionVersionMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDefinitionMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeDefinitionVersionPO;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecCompatibilityMapper;
import io.yak.ops.business.sync.realtime.domain.DefinitionDigest;
import io.yak.ops.business.sync.realtime.domain.RuntimeEnvironmentRef;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition;
import io.yak.ops.business.sync.realtime.domain.SyncDefinitionDigestCalculator;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.DomainMappingState;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.PublicationCandidate;
import io.yak.ops.business.sync.realtime.repository.DefinitionVersionRepository.StoredVersion;
import io.yak.ops.business.sync.realtime.repository.support.RealtimeJsonCodec;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefinitionVersionRepositoryAdapterTest {

  private static final long TASK_ID = 9L;
  private static final String SOURCE_DIGEST = "c".repeat(64);

  @Test
  void samePublishedContentReusesExistingImmutableVersion() {
    RealtimeDefinitionVersionMapper versionMapper = mock(RealtimeDefinitionVersionMapper.class);
    RealtimeJobDefinitionMapper definitionMapper = mock(RealtimeJobDefinitionMapper.class);
    RealtimeJsonCodec json = mock(RealtimeJsonCodec.class);
    DefinitionVersionRepositoryAdapter repository =
        new DefinitionVersionRepositoryAdapter(versionMapper, definitionMapper, json);
    RealtimeDefinitionVersionPO existing = storedPo(41L, 2, SOURCE_DIGEST);
    when(versionMapper.selectList(any())).thenReturn(List.of(existing));

    StoredVersion result = repository.findOrCreate(candidate());

    assertThat(result.id()).isEqualTo(41L);
    assertThat(result.versionNo()).isEqualTo(2);
    verify(versionMapper, never()).insert(any());
  }

  @Test
  void changedPublishedContentAllocatesNextMonotonicVersionNumber() {
    RealtimeDefinitionVersionMapper versionMapper = mock(RealtimeDefinitionVersionMapper.class);
    RealtimeJobDefinitionMapper definitionMapper = mock(RealtimeJobDefinitionMapper.class);
    RealtimeJsonCodec json = mock(RealtimeJsonCodec.class);
    DefinitionVersionRepositoryAdapter repository =
        new DefinitionVersionRepositoryAdapter(versionMapper, definitionMapper, json);
    RealtimeDefinitionVersionPO previous = storedPo(40L, 2, "d".repeat(64));
    when(versionMapper.selectList(any())).thenReturn(List.of(), List.of(previous));
    when(json.write(any())).thenReturn("{}");
    when(versionMapper.insert(any()))
        .thenAnswer(
            invocation -> {
              RealtimeDefinitionVersionPO inserted = invocation.getArgument(0);
              inserted.setId(42L);
              return 1;
            });

    StoredVersion result = repository.findOrCreate(candidate());

    assertThat(result.id()).isEqualTo(42L);
    assertThat(result.versionNo()).isEqualTo(3);
    assertThat(result.sourceConfigDigest()).isEqualTo(SOURCE_DIGEST);
  }

  private PublicationCandidate candidate() {
    CdcPipelineSpec spec = spec();
    SyncDefinition definition = new CdcPipelineSpecCompatibilityMapper().toDomain(spec).definition();
    DefinitionDigest digest =
        SyncDefinitionDigestCalculator.calculate(definition, new RuntimeEnvironmentRef(3L));
    return new PublicationCandidate(
        TASK_ID,
        5,
        3L,
        spec,
        SOURCE_DIGEST,
        definition,
        digest,
        DomainMappingState.MAPPED);
  }

  private RealtimeDefinitionVersionPO storedPo(long id, int versionNo, String sourceDigest) {
    RealtimeDefinitionVersionPO po = new RealtimeDefinitionVersionPO();
    po.setId(id);
    po.setTaskId(TASK_ID);
    po.setVersionNo(versionNo);
    po.setSourceDraftRevision(4);
    po.setRuntimeEnvironmentId(3L);
    po.setDefinitionJson("{}");
    po.setDefinitionDigest("e".repeat(64));
    po.setSourceConfigDigest(sourceDigest);
    po.setDomainMappingState(DomainMappingState.MAPPED.name());
    po.setCreateTime(LocalDateTime.now());
    return po;
  }

  private CdcPipelineSpec spec() {
    return new CdcPipelineSpec(
        11L,
        22L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                "orders", "ods_orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        60_000,
        new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000),
        new CdcPipelineSpec.SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));
  }
}
