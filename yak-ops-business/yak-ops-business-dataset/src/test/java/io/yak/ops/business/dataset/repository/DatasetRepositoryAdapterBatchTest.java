package io.yak.ops.business.dataset.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldDefinition;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetVersionDraft;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.business.dataset.repository.support.DatasetJsonCodec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatasetRepositoryAdapterBatchTest {

  @Test
  void appendVersionWritesFieldContractInOneBatchBoundary() {
    DatasetDao datasetDao = mock(DatasetDao.class);
    DatasetJsonCodec jsonCodec = mock(DatasetJsonCodec.class);
    DatasetRepositoryAdapter repository = new DatasetRepositoryAdapter(datasetDao, jsonCodec);
    List<DatasetFieldDefinition> fields = List.of(
        field("order_id", DatasetFieldRole.DIMENSION),
        field("amount", DatasetFieldRole.MEASURE));
    DatasetVersionDraft draft =
        DatasetVersionDraft.sqlQuery(7L, 3, "ds-1", "select 1", fields);

    when(jsonCodec.schemaSnapshot(fields)).thenReturn("[]");
    when(datasetDao.insertVersion(any(DatasetVersionPO.class)))
        .thenAnswer(invocation -> {
          DatasetVersionPO value = invocation.getArgument(0);
          value.setId(31L);
          return 1;
        });
    when(datasetDao.insertFields(anyList())).thenReturn(2);

    long versionId = repository.appendVersion(draft);

    assertThat(versionId).isEqualTo(31L);
    ArgumentCaptor<List<DatasetFieldPO>> rows = ArgumentCaptor.forClass(List.class);
    verify(datasetDao).insertFields(rows.capture());
    assertThat(rows.getValue()).hasSize(2);
    assertThat(rows.getValue()).extracting(DatasetFieldPO::getVersionId).containsOnly(31L);
    assertThat(rows.getValue()).extracting(DatasetFieldPO::getSortOrder).containsExactly(1, 2);
  }

  private DatasetFieldDefinition field(String name, DatasetFieldRole role) {
    return new DatasetFieldDefinition(
        name,
        name,
        name,
        role == DatasetFieldRole.MEASURE
            ? DatasetFieldDataType.NUMBER
            : DatasetFieldDataType.STRING,
        true,
        null,
        role);
  }
}
