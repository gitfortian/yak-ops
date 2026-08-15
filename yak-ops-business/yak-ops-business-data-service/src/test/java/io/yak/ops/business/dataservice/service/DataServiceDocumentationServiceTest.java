package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceDocumentationMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceDocumentationPO;
import io.yak.ops.business.dataservice.service.DataServiceDocumentationService.DocumentationInput;
import io.yak.ops.business.dataservice.service.DataServiceDocumentationService.ParameterDoc;
import io.yak.ops.business.dataservice.service.DataServiceDocumentationService.ResponseFieldDoc;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServiceDocumentationServiceTest {

  private DataServiceDocumentationMapper mapper;
  private DataServiceService dataServiceService;
  private DataServiceDocumentationService service;

  @BeforeEach
  void setUp() {
    mapper = mock(DataServiceDocumentationMapper.class);
    dataServiceService = mock(DataServiceService.class);
    service = new DataServiceDocumentationService(mapper, dataServiceService, new ObjectMapper());
  }

  @Test
  void currentSqlParametersAreSourceOfTruth() throws Exception {
    ApiView api = api("select * from orders where status = :status and tenant_id = :tenantId");
    when(dataServiceService.get(7L)).thenReturn(api);

    DataServiceDocumentationPO po = new DataServiceDocumentationPO();
    po.setApiId(7L);
    po.setSqlHash(hash(api.sql()));
    po.setParameterSchemaJson("[{\"name\":\"status\",\"type\":\"STRING\",\"required\":true,\"description\":\"订单状态\"},{\"name\":\"removed\",\"type\":\"STRING\",\"required\":true}]");
    po.setResponseSchemaJson("[]");
    when(mapper.selectById(7L)).thenReturn(po);

    var result = service.get(7L);

    assertThat(result.parameters()).extracting(ParameterDoc::name)
        .containsExactly("status", "tenantId");
    assertThat(result.parameters().get(0).description()).isEqualTo("订单状态");
    assertThat(result.parameters().get(1).type()).isEqualTo("STRING");
    assertThat(result.schemaStale()).isFalse();
  }

  @Test
  void savePersistsSqlFingerprintAndOpenApiReflectsSecurityAndRowSchema() throws Exception {
    ApiView api = api("select id, amount from orders where status = :status");
    when(dataServiceService.get(7L)).thenReturn(api);
    when(mapper.selectById(7L)).thenReturn(null);

    var saved = service.save(
        7L,
        new DocumentationInput(
            List.of(new ParameterDoc("status", "STRING", true, "订单状态", "PAID")),
            List.of(
                new ResponseFieldDoc("id", "INTEGER", false, "订单 ID", "1001"),
                new ResponseFieldDoc("amount", "NUMBER", true, "订单金额", "99.50"))));

    ArgumentCaptor<DataServiceDocumentationPO> captor =
        ArgumentCaptor.forClass(DataServiceDocumentationPO.class);
    verify(mapper).insert(captor.capture());
    assertThat(captor.getValue().getSqlHash()).isEqualTo(hash(api.sql()));
    assertThat(saved.documented()).isTrue();

    DataServiceDocumentationPO persisted = captor.getValue();
    when(mapper.selectById(7L)).thenReturn(persisted);
    Map<String, Object> spec = service.openApi(7L);

    assertThat(spec.get("openapi")).isEqualTo("3.0.3");
    assertThat(spec).containsKey("components");
    assertThat(spec.get("paths").toString()).contains(api.runtimePath()).contains("status").contains("amount");
  }

  @Test
  void rejectsDocumentationForParameterNotPresentInSql() {
    when(dataServiceService.get(7L)).thenReturn(api("select * from orders where status = :status"));

    assertThatThrownBy(() -> service.save(
        7L,
        new DocumentationInput(
            List.of(new ParameterDoc("tenantId", "STRING", true, null, null)),
            List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL 中不存在参数");
    verify(mapper, org.mockito.Mockito.never()).insert(any());
  }

  private ApiView api(String sql) {
    List<String> parameters = sql.contains(":tenantId")
        ? List.of("status", "tenantId")
        : List.of("status");
    return new ApiView(
        7L,
        "订单查询",
        "/orders",
        "/api/v1/data-service/runtime/orders",
        42L,
        sql,
        parameters,
        1000,
        30,
        true,
        "API_KEY",
        "供订单系统查询",
        "DATA_DEVELOPMENT_RELEASE",
        "88",
        102L,
        2,
        null,
        null);
  }

  private String hash(String value) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
