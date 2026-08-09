package io.yak.ops.business.datasource.domain;

import java.util.List;

/** 与具体分页框架解耦的数据源分页结果。 */
public record DataSourcePage<T>(
    List<T> records,
    long total,
    long pages,
    long pageNo,
    long pageSize) {

  public DataSourcePage {
    records = records == null ? List.of() : List.copyOf(records);
  }
}
