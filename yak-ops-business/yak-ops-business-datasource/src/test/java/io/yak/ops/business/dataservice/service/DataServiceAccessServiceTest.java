package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiKeyMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiKeyPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServiceAccessServiceTest {

  private DataServiceApiMapper apiMapper;
  private DataServiceApiKeyMapper keyMapper;
  private DataServiceAccessService service;
  private DataServiceApiPO api;

  @BeforeEach
  void setUp() {
    apiMapper = mock(DataServiceApiMapper.class);
    keyMapper = mock(DataServiceApiKeyMapper.class);
    service = new DataServiceAccessService(apiMapper, keyMapper);

    api = new DataServiceApiPO();
    api.setId(9L);
    api.setName("订单查询");
    api.setAuthMode("NONE");
    when(apiMapper.selectById(9L)).thenReturn(api);
  }

  @Test
  void createKeyReturnsSecretOnceAndPersistsOnlyHash() {
    doAnswer(invocation -> {
      DataServiceApiKeyPO key = invocation.getArgument(0);
      key.setId(101L);
      return 1;
    }).when(keyMapper).insert(any(DataServiceApiKeyPO.class));

    DataServiceAccessService.CreatedApiKey created = service.createKey(
        9L,
        new DataServiceAccessService.ApiKeyInput("BI 系统", 120, null));

    assertThat(created.secret()).startsWith("yak_ds_");
    assertThat(created.key().keyPrefix()).startsWith("yak_ds_");
    assertThat(created.key().rateLimitPerMinute()).isEqualTo(120);

    ArgumentCaptor<DataServiceApiKeyPO> captor = ArgumentCaptor.forClass(DataServiceApiKeyPO.class);
    verify(keyMapper).insert(captor.capture());
    assertThat(captor.getValue().getKeyHash()).hasSize(64);
    assertThat(captor.getValue().getKeyHash()).doesNotContain(created.secret());
    assertThat(captor.getValue().getKeyPrefix()).isEqualTo(created.key().keyPrefix());
  }

  @Test
  void apiKeyModeRequiresAtLeastOneValidKey() {
    when(keyMapper.selectList(any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.setAuthMode(9L, "API_KEY"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("至少创建一个有效 Key");
  }

  @Test
  void publicModeDoesNotRequireAKey() {
    DataServiceAccessService.AccessContext access = service.authorize(api, null);

    assertThat(access.callerType()).isEqualTo("PUBLIC");
    assertThat(access.apiKeyId()).isNull();
  }

  @Test
  void invalidApiKeyIsRejected() {
    api.setAuthMode("API_KEY");
    when(keyMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.authorize(api, "yak_ds_invalid"))
        .isInstanceOf(DataServiceUnauthorizedException.class)
        .hasMessageContaining("无效");
  }

  @Test
  void validKeyIsRateLimitedPerMinute() {
    api.setAuthMode("API_KEY");
    DataServiceApiKeyPO key = validKey(101L, 1);
    when(keyMapper.selectOne(any())).thenReturn(key);

    DataServiceAccessService.AccessContext first = service.authorize(api, "yak_ds_secret");
    assertThat(first.apiKeyId()).isEqualTo(101L);
    assertThat(first.apiKeyName()).isEqualTo("BI 系统");

    assertThatThrownBy(() -> service.authorize(api, "yak_ds_secret"))
        .isInstanceOf(DataServiceRateLimitException.class)
        .hasMessageContaining("每分钟 1 次");
  }

  @Test
  void cannotDisableLastValidKeyWhileApiKeyModeIsEnabled() {
    api.setAuthMode("API_KEY");
    DataServiceApiKeyPO key = validKey(101L, 60);
    when(keyMapper.selectById(101L)).thenReturn(key);
    when(keyMapper.selectList(any())).thenReturn(List.of(key));

    assertThatThrownBy(() -> service.setKeyEnabled(9L, 101L, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("最后一个有效 Key");
  }

  private DataServiceApiKeyPO validKey(Long id, int rateLimit) {
    DataServiceApiKeyPO key = new DataServiceApiKeyPO();
    key.setId(id);
    key.setApiId(9L);
    key.setName("BI 系统");
    key.setKeyPrefix("yak_ds_abcd1234");
    key.setKeyHash("hash");
    key.setEnabled(true);
    key.setRateLimitPerMinute(rateLimit);
    key.setExpiresAt(LocalDateTime.now().plusDays(1));
    key.setCreateTime(LocalDateTime.now());
    key.setUpdateTime(LocalDateTime.now());
    return key;
  }
}
