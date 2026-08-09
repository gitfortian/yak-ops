package io.yak.ops.business.resource.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.resource.config.ResourceProperties;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.storage.StorageOperatorRegistry;
import io.yak.ops.business.resource.sync.ResourceFileSyncDispatcher;
import io.yak.ops.common.bean.dto.resource.ResourceQueryDTO;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceServiceSupportTest {

  @Mock private ResourceRepository repository;
  @Mock private StorageOperatorRegistry storageRegistry;
  @Mock private ResourceFileSyncDispatcher syncDispatcher;

  @Test
  void normalizesQueryIntoDomainWithoutMutatingRequest() {
    ResourceServiceSupport support =
        new ResourceServiceSupport(
            repository,
            storageRegistry,
            syncDispatcher,
            new ResourceProperties());
    ResourceQueryDTO request = new ResourceQueryDTO();
    request.setPageNo(2);
    request.setPageSize(30);
    request.setParentId(9L);
    request.setKeyword("  report  ");
    request.setNodeType(" file ");

    ResourceQuery query = support.normalizeQuery(request);

    assertThat(query.pageNo()).isEqualTo(2);
    assertThat(query.pageSize()).isEqualTo(30);
    assertThat(query.parentId()).isEqualTo(9L);
    assertThat(query.keyword()).isEqualTo("report");
    assertThat(query.nodeType()).isEqualTo(ResourceNodeType.FILE);

    assertThat(request.getKeyword()).isEqualTo("  report  ");
    assertThat(request.getNodeType()).isEqualTo(" file ");
  }
}
