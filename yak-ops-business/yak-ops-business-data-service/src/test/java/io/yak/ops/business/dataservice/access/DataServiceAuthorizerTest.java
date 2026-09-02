package io.yak.ops.business.dataservice.access;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import org.junit.jupiter.api.Test;

class DataServiceAuthorizerTest {

  @Test
  void ipPolicyRejectsBeforeApiKeyLookupOrRateLimit() {
    DataServiceApiKeyRepository repository = mock(DataServiceApiKeyRepository.class);
    ApiKeySecretGenerator secrets = mock(ApiKeySecretGenerator.class);
    DataServiceRateLimiter rateLimiter = mock(DataServiceRateLimiter.class);
    DataServiceIpAccessAuthorizer ipAccessAuthorizer = mock(DataServiceIpAccessAuthorizer.class);
    DataServiceDefinition definition = mock(DataServiceDefinition.class);
    when(definition.id()).thenReturn(7L);
    DataServiceForbiddenException forbidden =
        new DataServiceForbiddenException("blocked by network policy");
    org.mockito.Mockito.doThrow(forbidden)
        .when(ipAccessAuthorizer)
        .authorize(7L, "203.0.113.9");

    DataServiceAuthorizer authorizer = new DataServiceAuthorizer(
        repository, secrets, rateLimiter, ipAccessAuthorizer);

    assertThatThrownBy(
        () -> authorizer.authorize(definition, "yak-key", "203.0.113.9"))
        .isSameAs(forbidden);

    verify(ipAccessAuthorizer).authorize(7L, "203.0.113.9");
    verifyNoInteractions(repository, secrets, rateLimiter);
  }
}
