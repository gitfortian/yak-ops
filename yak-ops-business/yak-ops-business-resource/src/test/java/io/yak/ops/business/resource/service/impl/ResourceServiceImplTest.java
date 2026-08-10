package io.yak.ops.business.resource.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.service.support.ResourceViewMapper;
import io.yak.ops.business.resource.storage.StorageOperatorRegistry;
import io.yak.ops.common.bean.dto.resource.ResourceQueryDTO;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceServiceImplTest {

  @Mock private ResourceRepository repository;
  @Mock private StorageOperatorRegistry storageRegistry;
  @Mock private ResourceServiceSupport support;
  @Mock private ResourceFileOperations fileOperations;
  @Mock private ResourceViewMapper viewMapper;

  @Test
  void pageConvertsDtoToDomainWithoutMutatingRequest() {
    ResourceServiceImpl service =
        new ResourceServiceImpl(
            repository,
            storageRegistry,
            support,
            fileOperations,
            viewMapper);
    ResourceQueryDTO request = new ResourceQueryDTO();
    request.setPageNo(2);
    request.setPageSize(30);
    request.setParentId(9L);
    request.setKeyword("  report  ");
    request.setNodeType(" file ");

    when(support.trimToNull("  report  ")).thenReturn("report");
    when(repository.page(any(ResourceQuery.class)))
        .thenReturn(new PageData<ResourceNode>(List.of(), 0L, 0L, 2L, 30L));

    service.page(request);

    ArgumentCaptor<ResourceQuery> captor = ArgumentCaptor.forClass(ResourceQuery.class);
    verify(repository).page(captor.capture());
    ResourceQuery query = captor.getValue();
    assertThat(query.pageNo()).isEqualTo(2);
    assertThat(query.pageSize()).isEqualTo(30);
    assertThat(query.parentId()).isEqualTo(9L);
    assertThat(query.keyword()).isEqualTo("report");
    assertThat(query.nodeType()).isEqualTo(ResourceNodeType.FILE);

    assertThat(request.getKeyword()).isEqualTo("  report  ");
    assertThat(request.getNodeType()).isEqualTo(" file ");
  }
}
