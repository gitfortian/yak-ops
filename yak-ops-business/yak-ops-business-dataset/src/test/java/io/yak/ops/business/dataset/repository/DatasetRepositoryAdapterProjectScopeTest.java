package io.yak.ops.business.dataset.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.repository.support.DatasetJsonCodec;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetRepositoryAdapterProjectScopeTest {

  @Mock private DatasetDao datasetDao;
  @Mock private DatasetJsonCodec jsonCodec;

  @Test
  void listUsesCurrentProjectAsRepositoryBoundary() {
    DatasetPO po = new DatasetPO();
    po.setId(11L);
    po.setProjectId(7L);
    po.setName("sales");
    po.setStatus("ONLINE");
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    when(datasetDao.selectDatasets(7L)).thenReturn(List.of(po));

    List<Dataset> result =
        new DatasetRepositoryAdapter(datasetDao, jsonCodec, currentProject).listDatasets();

    assertThat(result).extracting(Dataset::id).containsExactly(11L);
    verify(datasetDao).selectDatasets(7L);
  }
}
