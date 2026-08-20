package io.yak.ops.business.development.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Creates stable lineage asset keys from resolved physical identities.
 *
 * <p>All lineage producers should use this component instead of constructing asset keys locally.
 */
@Component
public class LineageAssetKeyFactory {

  public String tableKey(TableIdentityResolver.PhysicalTableIdentity identity) {
    if (identity == null) {
      throw new IllegalArgumentException("table identity 不能为空");
    }
    return identity.assetKey();
  }

  public String columnKey(
      TableIdentityResolver.PhysicalTableIdentity identity,
      String columnName) {
    if (identity == null) {
      throw new IllegalArgumentException("table identity 不能为空");
    }
    String column = columnName == null ? "" : columnName.trim().toLowerCase(Locale.ROOT);
    return "column:%s.%s".formatted(
        identity.assetKey().substring("table:".length()),
        column);
  }
}
