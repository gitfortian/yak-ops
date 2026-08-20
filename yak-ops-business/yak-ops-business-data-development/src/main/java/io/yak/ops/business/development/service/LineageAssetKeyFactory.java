package io.yak.ops.business.development.service;

/**
 * Centralizes lineage asset key generation for physical assets.
 *
 * <p>Asset keys must be generated from resolved physical table identities instead of raw SQL table
 * names so that same-named tables across schemas remain isolated.
 */
public class LineageAssetKeyFactory {

  public String tableKey(TableIdentityResolver.PhysicalTableIdentity identity) {
    return "table:" + identity.assetKey();
  }

  public String columnKey(
      TableIdentityResolver.PhysicalTableIdentity identity,
      String columnName) {
    return "column:" + identity.assetKey() + "." + columnName;
  }
}
