package io.yak.ops.business.dataservice.query;

import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import java.time.LocalDateTime;

/** Access-management projection for one Data Service in the current Project Space. */
public record DataServiceAccessOverviewItem(
    Long apiId,
    String name,
    String path,
    String runtimePath,
    boolean enabled,
    AuthMode authMode,
    IpAccessMode ipAccessMode,
    int apiKeyCount,
    int activeApiKeyCount,
    int allowlistRuleCount,
    int activeAllowlistRuleCount,
    int denylistRuleCount,
    int activeDenylistRuleCount,
    LocalDateTime updateTime) {}
