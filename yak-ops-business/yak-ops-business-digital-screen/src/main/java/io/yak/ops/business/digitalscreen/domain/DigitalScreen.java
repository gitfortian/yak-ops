package io.yak.ops.business.digitalscreen.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted Digital Screen definition. Runtime Dataset values are intentionally excluded. */
public record DigitalScreen(
    long id,
    String name,
    String description,
    String templateId,
    int templateVersion,
    DigitalScreenStatus status,
    Map<String, Object> bindings,
    Instant publishedTime,
    Instant createTime,
    Instant updateTime) {

  public DigitalScreen {
    bindings = bindings == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
  }
}
