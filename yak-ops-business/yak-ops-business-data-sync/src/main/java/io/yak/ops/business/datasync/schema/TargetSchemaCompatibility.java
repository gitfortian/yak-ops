package io.yak.ops.business.datasync.schema;

import io.yak.ops.business.datasync.catalog.DataSyncCatalogColumns;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaCompatibility;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaMapper;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 校验产品 LogicalTable 是否可以直接写入一个已存在 JDBC 目标表。
 *
 * <p>字段按 LogicalTable 顺序做同名匹配，类型兼容统一复用 JdbcSchemaCompatibility。</p>
 *
 * @author weifuwan
 * @since 2026-10-04
 */
public final class TargetSchemaCompatibility {

    private TargetSchemaCompatibility() {}

    public static TargetSchemaCompatibilityResult check(
            LogicalTable logicalTable, List<DataSourceCatalogColumnVO> targetColumns) {
        Objects.requireNonNull(logicalTable, "logicalTable must not be null");
        Objects.requireNonNull(targetColumns, "targetColumns must not be null");

        Map<String, DataSourceCatalogColumnVO> targetByName = DataSyncCatalogColumns.indexByName(targetColumns);
        List<DataSourceColumn> mappedTargetColumns = new ArrayList<>(logicalTable.columns().size());
        List<String> issues = new ArrayList<>();

        for (int index = 0; index < logicalTable.columns().size(); index++) {
            LogicalColumn source = logicalTable.columns().get(index);
            DataSourceCatalogColumnVO target =
                    DataSyncCatalogColumns.findByName(targetByName, source.name());
            if (target == null) {
                issues.add("目标表缺少字段：" + source.name());
                continue;
            }

            DataSourceColumn targetColumn = DataSyncCatalogColumns.toColumn(target, index + 1);
            if (targetColumn == null) {
                issues.add("目标字段元数据不完整：" + source.name());
                continue;
            }

            YakColumn sourceColumn =
                    new YakColumn(source.name(), source.dataType(), source.nullable(), source.length());
            YakColumn targetYakColumn;
            try {
                targetYakColumn = JdbcSchemaMapper.toYakColumn(targetColumn);
            } catch (IllegalArgumentException exception) {
                issues.add("目标字段类型不支持：" + source.name() + "（" + target.getTypeName() + "）");
                continue;
            }

            if (!JdbcSchemaCompatibility.isCompatible(sourceColumn, targetYakColumn)) {
                issues.add("目标字段不兼容：" + source.name() + "（"
                        + source.dataType().kind() + " → " + targetYakColumn.dataType().kind() + "）");
                continue;
            }
            mappedTargetColumns.add(targetColumn);
        }

        if (!issues.isEmpty()) {
            return new TargetSchemaCompatibilityResult(false, null, issues);
        }
        return new TargetSchemaCompatibilityResult(
                true, JdbcSchemaMapper.fromColumns(mappedTargetColumns), List.of());
    }
}
