package io.yak.ops.dao.repository.datasource;

import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;

/**
 * Carries repository-level datasource page filters.
 *
 * @param pageNo page number starting at 1
 * @param pageSize maximum rows per page
 * @param name optional partial datasource name
 * @param keyword optional name or JDBC URL keyword
 * @param dbType database provider type filter
 * @param environment datasource environment filter
 * @param connStatus connectivity status filter
 */
public record DataSourcePageQuery(
        int pageNo,
        int pageSize,
        String name,
        String keyword,
        String dbType,
        DataSourceEnvironment environment,
        DataSourceConnStatus connStatus) {}
