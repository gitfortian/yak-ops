package io.yak.ops.business.datasource.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.DataSourceChangedEvent;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataSourceCatalogCacheInvalidationListenerTest {

  @Test
  void datasourceChangeRemovesOnlyMatchingCacheEntries() {
    DataSourceCatalogMetadataCache cache = new DataSourceCatalogMetadataCache();
    DataSourceDefinition first = definition(1L);
    DataSourceDefinition second = definition(2L);

    cache.getOrLoad(cache.key(first, "tables"), 60, () -> "first");
    cache.getOrLoad(cache.key(second, "tables"), 60, () -> "second");
    assertThat(cache.size()).isEqualTo(2);

    new DataSourceCatalogCacheInvalidationListener(cache)
        .onDataSourceChanged(new DataSourceChangedEvent(1L));

    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.invalidate(1L)).isZero();
    assertThat(cache.invalidate(2L)).isEqualTo(1);
  }

  private DataSourceDefinition definition(Long id) {
    DataSourceDefinition definition = mock(DataSourceDefinition.class);
    when(definition.getId()).thenReturn(id);
    when(definition.getUpdateTime()).thenReturn(LocalDateTime.of(2026, 8, 27, 14, 0));
    return definition;
  }
}
