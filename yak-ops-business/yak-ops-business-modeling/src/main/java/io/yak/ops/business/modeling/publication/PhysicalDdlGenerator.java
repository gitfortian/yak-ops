package io.yak.ops.business.modeling.publication;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates the DDL script for one logical model: {@code CREATE TABLE} for a fresh target and
 * {@code ALTER TABLE} statements for a schema diff.
 *
 * <p>The generator is pure: it never reaches a database, so the exact script a publication will
 * run can be previewed, tested and recorded. Field order follows the model ordinal, and the primary-key
 * order follows the key positions, because that order is what a later verification compares against.
 *
 * <p>Deliberately absent: partition and lifecycle clauses. {@code isPartition}, {@code storagePolicy}
 * and {@code lifecycleDays} remain logical attributes. Engine properties that do have a physical
 * meaning (Doris bucket, replica and data model) travel through {@link PhysicalTableProperties}.
 */
public final class PhysicalDdlGenerator {

    private PhysicalDdlGenerator() {}

    /**
     * Builds the {@code CREATE TABLE} script without engine properties.
     *
     * <p>The five-argument overload is the authoritative path; this convenience exists for the
     * DDL-shape tests and for dialects that accept no properties at all.
     */
    public static PhysicalScript generate(
            PhysicalDialect dialect, PhysicalAddress address, String table, List<PhysicalColumnSpec> columns) {
        return generate(dialect, address, table, columns, PhysicalTableProperties.NONE);
    }

    /**
     * Builds the {@code CREATE TABLE} script for one model.
     *
     * @param dialect target dialect
     * @param address resolved target address
     * @param table physical table name
     * @param columns ordered columns, at least one
     * @param properties declared engine properties, never null
     * @return statements to execute plus the joined script
     * @throws IllegalArgumentException when the model cannot be published as declared
     */
    public static PhysicalScript generate(
            PhysicalDialect dialect,
            PhysicalAddress address,
            String table,
            List<PhysicalColumnSpec> columns,
            PhysicalTableProperties properties) {
        if (table == null || table.isBlank()) throw new IllegalArgumentException("物理表名不能为空");
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("模型没有可发布的字段");
        var effective = properties == null ? PhysicalTableProperties.NONE : properties;
        var qualified = dialect.qualify(address, table);
        var definitions = new ArrayList<String>();
        var comments = new ArrayList<String>();
        for (var column : columns) {
            if (column.name() == null || column.name().isBlank()) {
                throw new IllegalArgumentException("字段英文编码不能为空");
            }
            var type = typeOf(dialect, column);
            var definition = new StringBuilder("  ")
                    .append(dialect.quote(column.name()))
                    .append(' ')
                    .append(type.sqlType());
            // A key column is implicitly NOT NULL on every supported engine, so the DDL says it too.
            if (column.notNull() || column.keyPosition() != null) definition.append(" NOT NULL");
            var comment = commentOf(column);
            if (comment != null && dialect.hasInlineComments()) {
                definition.append(" COMMENT '").append(escape(comment, dialect)).append('\'');
            }
            definitions.add(definition.toString());
            if (comment != null && !dialect.hasInlineComments()) {
                comments.add("COMMENT ON COLUMN " + qualified + "." + dialect.quote(column.name()) + " IS '"
                        + escape(comment, dialect) + "'");
            }
        }
        var keys = keyColumns(dialect, columns);
        if (!keys.isEmpty()) definitions.add("  PRIMARY KEY (" + keys + ")");
        var create = new StringBuilder("CREATE TABLE ")
                .append(qualified)
                .append(" (\n")
                .append(String.join(",\n", definitions))
                .append("\n)");
        appendEngineProperties(create, dialect, effective, keys, columns);
        var statements = new ArrayList<String>();
        statements.add(create.toString());
        statements.addAll(comments);
        return new PhysicalScript(List.copyOf(statements), String.join(";\n", statements) + ";");
    }

    /**
     * Builds the {@code ALTER TABLE} statements for one schema diff.
     *
     * <p>Each diff entry becomes one or more statements; the engines commit each statement
     * independently, exactly as the create path does. Adds come before changes and drops because
     * that order never references a column that a previous statement removed.
     *
     * @param dialect target dialect
     * @param address resolved target address
     * @param table physical table name
     * @param columns ordered columns the model declares
     * @param changes diff computed by {@link SchemaDiff#diff}
     * @return statements to execute plus the joined script
     * @throws IllegalArgumentException when a change cannot be expressed for the dialect
     */
    public static PhysicalScript generateAlter(
            PhysicalDialect dialect,
            PhysicalAddress address,
            String table,
            List<PhysicalColumnSpec> columns,
            List<SchemaDiff> changes) {
        if (table == null || table.isBlank()) throw new IllegalArgumentException("物理表名不能为空");
        if (changes == null || changes.isEmpty()) throw new IllegalArgumentException("没有需要执行的变更");
        var byName = columns.stream()
                .collect(Collectors.toMap(PhysicalColumnSpec::name, column -> column, (first, ignored) -> first));
        var qualified = dialect.qualify(address, table);
        var statements = new ArrayList<String>();
        for (var change : changes) {
            switch (change.kind()) {
                case ADD -> {
                    var column = requireColumn(byName, change.column());
                    statements.add(
                            alterPrefix(dialect, qualified) + " ADD COLUMN " + columnDefinition(dialect, column));
                    if (commentOf(column) != null && !dialect.hasInlineComments()) {
                        statements.add(commentOn(dialect, qualified, column.name(), commentOf(column)));
                    }
                }
                case CHANGE -> {
                    var column = requireColumn(byName, change.column());
                    var definition = columnDefinition(dialect, column);
                    switch (dialect) {
                        case MYSQL, DORIS ->
                            statements.add(alterPrefix(dialect, qualified) + " MODIFY COLUMN " + definition);
                        case POSTGRESQL -> {
                            statements.add("ALTER TABLE " + qualified + " ALTER COLUMN "
                                    + dialect.quote(column.name()) + " TYPE " + sqlType(dialect, column)
                                    + " USING " + dialect.quote(column.name()) + "::" + sqlType(dialect, column));
                            var notNull = column.notNull() || column.keyPosition() != null;
                            statements.add("ALTER TABLE " + qualified + " ALTER COLUMN " + dialect.quote(column.name())
                                    + (notNull ? " SET NOT NULL" : " DROP NOT NULL"));
                        }
                        case ORACLE ->
                            statements.add("ALTER TABLE " + qualified + " MODIFY ("
                                    + dialect.quote(column.name()) + " " + sqlType(dialect, column)
                                    + (column.notNull() || column.keyPosition() != null ? " NOT NULL" : "") + ")");
                    }
                    var comment = commentOf(column);
                    if (comment != null && !dialect.hasInlineComments()) {
                        statements.add(commentOn(dialect, qualified, column.name(), comment));
                    }
                }
                case DROP ->
                    statements.add(alterPrefix(dialect, qualified) + " DROP COLUMN " + dialect.quote(change.column()));
            }
        }
        return new PhysicalScript(List.copyOf(statements), String.join(";\n", statements) + ";");
    }

    private static void appendEngineProperties(
            StringBuilder create,
            PhysicalDialect dialect,
            PhysicalTableProperties properties,
            String keys,
            List<PhysicalColumnSpec> columns) {
        if (dialect != PhysicalDialect.DORIS) return;
        var dataModel = properties.dataModel();
        if (dataModel != null) {
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("Doris 数据模型 " + dataModel + " KEY 需要模型定义主键字段");
            }
            create.append("\n").append(dataModel).append(" KEY(").append(keys).append(")");
        }
        if (properties.bucketCount() == null) {
            // Doris resolves the distribution itself; the model declines to guess a bucket count.
            throw new IllegalArgumentException("发布到 Doris 需要填写分桶数（物理属性 bucketCount）");
        }
        var hashColumn = keys.isEmpty() ? dialect.quote(columns.getFirst().name()) : firstKey(dialect, keys);
        create.append("\nDISTRIBUTED BY HASH(")
                .append(hashColumn)
                .append(") BUCKETS ")
                .append(properties.bucketCount());
        if (properties.replicaCount() != null) {
            create.append("\nPROPERTIES (\"replication_num\" = \"")
                    .append(properties.replicaCount())
                    .append("\")");
        }
    }

    private static String alterPrefix(PhysicalDialect dialect, String qualified) {
        return dialect == PhysicalDialect.ORACLE ? "ALTER TABLE " + qualified + " " : "ALTER TABLE " + qualified;
    }

    private static String columnDefinition(PhysicalDialect dialect, PhysicalColumnSpec column) {
        var type = PhysicalTypes.of(dialect, column.dataType(), column.lengthPrecision());
        var definition =
                new StringBuilder(dialect.quote(column.name())).append(' ').append(type.sqlType());
        if (column.notNull() || column.keyPosition() != null) definition.append(" NOT NULL");
        var comment = commentOf(column);
        if (comment != null && dialect.hasInlineComments()) {
            definition.append(" COMMENT '").append(escape(comment, dialect)).append('\'');
        }
        return definition.toString();
    }

    /** Renders just the SQL type for engines that split type and constraint clauses. */
    private static String sqlType(PhysicalDialect dialect, PhysicalColumnSpec column) {
        return PhysicalTypes.of(dialect, column.dataType(), column.lengthPrecision())
                .sqlType();
    }

    private static String commentOn(PhysicalDialect dialect, String qualified, String column, String comment) {
        return "COMMENT ON COLUMN " + qualified + "." + dialect.quote(column) + " IS '" + escape(comment, dialect)
                + "'";
    }

    private static PhysicalColumnSpec requireColumn(Map<String, PhysicalColumnSpec> byName, String name) {
        var column = byName.get(name);
        if (column == null) throw new IllegalArgumentException("变更引用了模型中不存在的字段 " + name);
        return column;
    }

    private static String keyColumns(PhysicalDialect dialect, List<PhysicalColumnSpec> columns) {
        return columns.stream()
                .filter(column -> column.keyPosition() != null)
                .sorted(Comparator.comparing(PhysicalColumnSpec::keyPosition))
                .map(column -> dialect.quote(column.name()))
                .collect(Collectors.joining(", "));
    }

    /** Extracts the first key column out of the rendered key list for the hash distribution. */
    private static String firstKey(PhysicalDialect dialect, String keys) {
        var separator = keys.indexOf(", ");
        return separator < 0 ? keys : keys.substring(0, separator);
    }

    /**
     * Resolves one column's physical type, stating which column broke the rule.
     *
     * <p>A per-column rejection without its column name leaves the user comparing every row of the
     * model by hand, so the name is attached while the original failure stays the cause.
     *
     * @param dialect target dialect
     * @param column column being defined
     * @return resolved physical type
     * @throws IllegalArgumentException naming the column when the declaration cannot be published
     */
    private static PhysicalTypeSpec typeOf(PhysicalDialect dialect, PhysicalColumnSpec column) {
        try {
            return PhysicalTypes.of(dialect, column.dataType(), column.lengthPrecision());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("字段 " + column.name() + "：" + failure.getMessage(), failure);
        }
    }

    private static String commentOf(PhysicalColumnSpec column) {
        var comment = column.comment() == null ? null : column.comment().trim();
        return comment == null || comment.isEmpty() ? null : comment;
    }

    private static String escape(String value, PhysicalDialect dialect) {
        // MySQL reads a backslash inside a string literal as an escape character, so it is doubled
        // before the quote is; a comment that ends in a lone backslash would otherwise escape the
        // closing quote and break the whole statement. Doris shares that behaviour, while
        // PostgreSQL (standard_conforming_strings) and Oracle treat a backslash as an ordinary
        // character, where doubling would store two.
        var prepared = dialect == PhysicalDialect.MYSQL || dialect == PhysicalDialect.DORIS
                ? value.replace("\\", "\\\\")
                : value;
        return prepared.replace("'", "''");
    }
}
