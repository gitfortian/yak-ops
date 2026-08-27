package io.yak.ops.business.digitalscreen.controller.v1.vo;

import java.time.Instant;
import java.util.Map;

public final class DigitalScreenViews {

  private DigitalScreenViews() {
  }

  public record DigitalScreenVO(
      String id,
      String name,
      String description,
      String templateId,
      int templateVersion,
      String status,
      Map<String, Object> bindings,
      Instant publishedTime,
      Instant createTime,
      Instant updateTime) {
  }
}
