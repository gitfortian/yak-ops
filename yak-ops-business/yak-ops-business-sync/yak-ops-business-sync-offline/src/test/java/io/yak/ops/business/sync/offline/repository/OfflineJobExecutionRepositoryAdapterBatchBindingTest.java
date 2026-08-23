package io.yak.ops.business.sync.offline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import org.junit.jupiter.api.Test;

class OfflineJobExecutionRepositoryAdapterBatchBindingTest {

  @Test
  void delegatesSafeBatchBindingToDao() {
    OfflineJobExecutionDao dao = mock(OfflineJobExecutionDao.class);
    when(dao.bindBatch(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(101L), any()))
        .thenReturn(true);
    OfflineJobExecutionRepositoryAdapter repository =
        new OfflineJobExecutionRepositoryAdapter(dao);

    assertThat(repository.bindBatch(9L, 101L)).isTrue();
    verify(dao).bindBatch(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(101L), any());
  }

  @Test
  void rejectsInvalidBindingIdentityBeforeDao() {
    OfflineJobExecutionDao dao = mock(OfflineJobExecutionDao.class);
    OfflineJobExecutionRepositoryAdapter repository =
        new OfflineJobExecutionRepositoryAdapter(dao);

    assertThatThrownBy(() -> repository.bindBatch(0L, 101L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ExecutionId");
    assertThatThrownBy(() -> repository.bindBatch(9L, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BatchExecutionId");
  }
}
