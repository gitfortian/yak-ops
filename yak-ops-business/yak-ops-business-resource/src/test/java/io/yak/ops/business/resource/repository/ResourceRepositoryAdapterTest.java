package io.yak.ops.business.resource.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.resource.dao.ResourceDao;
import io.yak.ops.business.resource.dao.ResourceDao.PageQuery;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceRepositoryAdapterTest {

  @Mock private ResourceDao resourceDao;

  @Test
  void insertPropagatesGeneratedIdBackToDomain() {
    ResourceRepositoryAdapter adapter = new ResourceRepositoryAdapter(resourceDao);
    ResourceNode node = new ResourceNode();
    node.setName("README.md");
    node.setFullPath("/README.md");
    node.setNodeType(ResourceNodeType.FILE);
    node.setStorageType(ResourceStorageType.LOCAL);

    doAnswer(
            invocation -> {
              ResourcePO po = invocation.getArgument(0);
              po.setId(42L);
              return 1;
            })
        .when(resourceDao)
        .insert(any(ResourcePO.class));

    assertThat(adapter.insert(node)).isTrue();
    assertThat(node.getId()).isEqualTo(42L);

    ArgumentCaptor<ResourcePO> captor = ArgumentCaptor.forClass(ResourcePO.class);
    verify(resourceDao).insert(captor.capture());
    assertThat(captor.getValue().getFullPath()).isEqualTo("/README.md");
    assertThat(captor.getValue().getNodeType()).isEqualTo(ResourceNodeType.FILE);
  }

  @Test
  void pageMapsDomainQueryToDaoQueryAndBack() {
    ResourceRepositoryAdapter adapter = new ResourceRepositoryAdapter(resourceDao);
    ResourcePO po = new ResourcePO();
    po.setId(7L);
    po.setName("job.sql");
    po.setNodeType(ResourceNodeType.FILE);
    po.setStorageType(ResourceStorageType.LOCAL);

    Page<ResourcePO> page = Page.of(2, 10);
    page.setRecords(java.util.List.of(po));
    page.setTotal(21L);
    when(resourceDao.selectPage(any(PageQuery.class))).thenReturn(page);

    PageData<ResourceNode> result =
        adapter.page(new ResourceQuery(2, 10, 3L, "job", ResourceNodeType.FILE));

    assertThat(result.records()).hasSize(1);
    assertThat(result.records().get(0).getId()).isEqualTo(7L);
    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.pageNo()).isEqualTo(2L);

    ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
    verify(resourceDao).selectPage(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(3L);
    assertThat(captor.getValue().keyword()).isEqualTo("job");
    assertThat(captor.getValue().nodeType()).isEqualTo(ResourceNodeType.FILE);
  }
}
