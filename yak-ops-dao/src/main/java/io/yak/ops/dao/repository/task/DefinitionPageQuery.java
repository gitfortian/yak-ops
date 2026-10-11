package io.yak.ops.dao.repository.task;

import java.util.Map;

/** Workspace-scoped Task filtering, including safe top-level JSON parameter keys. */
public record DefinitionPageQuery(
        String workspaceId,
        int pageNo,
        int pageSize,
        String keyword,
        String taskType,
        Integer statusValue,
        Map<String, String> parameterFilters) {}
