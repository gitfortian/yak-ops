package io.yak.ops.business.dataservice.controller.v1;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.service.DataServicePublicationService;
import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublicationState;
import org.junit.jupiter.api.Test;

class DataServicePublicationStateControllerTest {

  @Test
  void stateDelegatesToPublicationBoundary() {
    DataServicePublicationService publicationService = mock(DataServicePublicationService.class);
    PublicationState state = new PublicationState(false, false, null, null);
    when(publicationService.state("DATA_DEVELOPMENT_DATA_SERVICE", "100")).thenReturn(state);

    DataServicePublicationStateController controller =
        new DataServicePublicationStateController(publicationService);

    controller.state("DATA_DEVELOPMENT_DATA_SERVICE", "100");

    verify(publicationService).state("DATA_DEVELOPMENT_DATA_SERVICE", "100");
  }
}
