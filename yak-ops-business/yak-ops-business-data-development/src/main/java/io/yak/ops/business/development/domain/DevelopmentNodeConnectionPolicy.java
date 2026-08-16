package io.yak.ops.business.development.domain;

import java.util.Map;
import java.util.Set;

/** Explicit, fail-closed connection policy for the data-development DAG. */
public final class DevelopmentNodeConnectionPolicy {

  private static final Map<DevelopmentNodeType, Set<DevelopmentNodeType>> ALLOWED_TARGETS = Map.of(
      DevelopmentNodeType.SQL,
      Set.of(
          DevelopmentNodeType.SQL,
          DevelopmentNodeType.DATASET,
          DevelopmentNodeType.DATA_SERVICE),
      DevelopmentNodeType.DATASET,
      Set.of(DevelopmentNodeType.DATA_SERVICE));

  private DevelopmentNodeConnectionPolicy() {}

  public static boolean canConnect(DevelopmentNodeType source, DevelopmentNodeType target) {
    if (source == null || target == null) return false;
    return ALLOWED_TARGETS.getOrDefault(source, Set.of()).contains(target);
  }
}
