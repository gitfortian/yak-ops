package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import org.junit.jupiter.api.Test;

class DevelopmentOutputNodeReservationTest {

  @Test
  void outputNodeTypesStayReservedUntilTheirNodeLifecycleIsImplemented() {
    DevelopmentNodeService service = new DevelopmentNodeService(
        mock(DevelopmentNodeRepository.class),
        mock(DevelopmentDirectoryRepository.class),
        mock(TaskCatalogService.class));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("订单数据集", "DATASET", null, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("订单查询 API", "DATA_SERVICE", null, null));
  }
}
