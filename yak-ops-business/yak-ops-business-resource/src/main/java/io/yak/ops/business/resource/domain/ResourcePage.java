package io.yak.ops.business.resource.domain;

import java.util.List;

/** 与 MyBatis 分页对象解耦的资源分页结果。 */
public record ResourcePage<T>(
    List<T> records,
    long total,
    long pages,
    long pageNo,
    long pageSize) {

  public ResourcePage {
    records = records == null ? List.of() : List.copyOf(records);
  }
}
