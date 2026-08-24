package io.yak.ops.business.dataservice.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ResponseFieldDoc;
import io.yak.ops.business.dataservice.execution.DataServiceSqlCompiler;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceDocumentationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataServiceDocumentationManagerTest {

  private DataServiceReader dataServiceReader;
  private DataServiceDocumentationRepository repository;
  private DataServiceDocumentationManager manager;

  @BeforeEach
  void setUp() {
    dataServiceReader = mock(DataServiceReader.class);
    repository = mock(DataServiceDocumentationRepository.class);
    DataServiceSqlCompiler compiler = new DataServiceSqlCompiler();
    DocumentationFingerprint fingerprint = new DocumentationFingerprint();
    DataServiceDocumentationReader reader =
        new DataServiceDocumentationReader(dataServiceReader, repository, compiler, fingerprint);
    manager = new DataServiceDocumentationManager(
        dataServiceReader, repository, reader, compiler, fingerprint);
  }

  @Test
  void currentSqlParametersRemainSourceOfTruth() {
    DataServiceDefinition definition = definition(
        "select * from orders where status = :status and tenant_id = :tenantId");
    when(dataServiceReader.require(7L)).thenReturn(definition);
    when(repository.findByApiId(7L)).thenReturn(Optional.of(new DataServiceDocumentation(
        7L,
        new DocumentationFingerprint().sqlHash(definition.runtimeSnapshot().sql()),
        List.of(
            new ParameterDoc("status", "STRING", true, "订单状态", null),
            new ParameterDoc("removed", "STRING", true, null, null)),
        List.of(),
        null)));

    DataServiceDocumentationReader reader = new DataServiceDocumentationReader(
        dataServiceReader, repository, new DataServiceSqlCompiler(), new DocumentationFingerprint());
    ApiDocumentation result = reader.get(7L);

    assertThat(result.parameters()).extracting(ParameterDoc::name)
        .containsExactly("status", "tenantId");
    assertThat(result.parameters().get(0).description()).isEqualTo("订单状态");
    assertThat(result.schemaStale()).isFalse();
  }

  @Test
  void rejectsParameterThatDoesNotExistInSql() {
    when(dataServiceReader.require(7L)).thenReturn(
        definition("select * from orders where status = :status"));

    assertThatThrownBy(() -> manager.save(
        7L,
        new DocumentationInput(
            List.of(new ParameterDoc("tenantId", "STRING", true, null, null)),
            List.<ResponseFieldDoc>of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL 中不存在参数");

    verify(repository, never()).save(any());
  }

  private DataServiceDefinition definition(String sql) {
    LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);
    return DataServiceDefinition.restore(
        7L,
        new DataServiceSettings("订单查询", "/orders", 1000, 30, true, "供订单系统查询", false),
        new PublishedRuntimeSnapshot(42L, sql),
        new SourceReference("DATA_DEVELOPMENT_RELEASE", "88", 102L, 2),
        RuntimePolicy.defaults(false),
        AuthMode.API_KEY,
        now,
        now);
  }
}
