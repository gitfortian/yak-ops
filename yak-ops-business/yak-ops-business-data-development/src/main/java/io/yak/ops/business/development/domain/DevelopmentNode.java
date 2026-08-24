package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Stable workspace identity for one data-development resource. */
public record DevelopmentNode(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    String name,
    String type,
    @JsonSerialize(using = ToStringSerializer.class) Long projectId,
    @JsonSerialize(using = ToStringSerializer.class) Long directoryId,
    boolean configured,
    Instant createTime,
    Instant updateTime,
    String updatedBy,
    boolean pendingPublish) {

  /** Keeps existing callers source-compatible while tree metadata is populated by persistence. */
  public DevelopmentNode(
      Long id,
      String name,
      String type,
      Long projectId,
      Long directoryId,
      boolean configured,
      Instant createTime,
      Instant updateTime) {
    this(id, name, type, projectId, directoryId, configured, createTime, updateTime, null, false);
  }

  /** Resolves the domain node type instead of spreading string parsing across application services. */
  public DevelopmentNodeType nodeType() {
    return DevelopmentNodeType.tryParse(type)
        .orElseThrow(() -> new IllegalArgumentException("未知数据开发节点类型：" + type));
  }

  public boolean supportsTaskLifecycle() {
    return nodeType().supportsTaskLifecycle();
  }

  /** Guards the mutable Draft -> immutable Revision lifecycle at the domain identity boundary. */
  public void requireTaskLifecycle() {
    if (!supportsTaskLifecycle()) {
      throw new IllegalArgumentException(
          "当前节点不是可执行开发任务，不能进入草稿/发布生命周期：" + type);
    }
  }
}
