package io.yak.ops.business.modeling.publication;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates the {@code CREATE TABLE} script for one logical model.
 *
 * <p>The generator is pure: it never reaches a database, so the exact script a publication will run
 * can be previewed, tested and recorded. Field order follows the model ordinal, and the primary-key
 * order follows the key positions, because that order is what a later verification compares against.
 *
 * <p>Deliberately absent: partition, storage and lifecycle clauses. {@code isPartition},
 * {@code aggregateType}, {@code storagePolicy} and {@code lifecycleDays} are logical attributes in
 * this release and would need an engine-specific physical design to be meaningful.
 */
public final class PhysicalDdlGenerator {

    private PhysicalDdlGenerator() {}

    /**
     * Builds the script for one model.
     *
     * @param dialect target dialect
     * @param address resolved target address
     * @param table physical table name
     * @param columns ordered columns, at least one
     * @return statements to execute plus the joined script
     * @throws IllegalArgumentException when the model cannot be published as declared
     */
    public static PhysicalScript generate(
            PhysicalDialect dialect, PhysicalAddress address, String table, List<PhysicalColumnSpec> columns) {
        if (table == null || table.isBlank()) throw new IllegalArgumentException("物理表名不能为空");
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("模型没有可发布的字段");
        var qualified = dialect.qualify(address, table);
        var definitions = new ArrayList<String>();
        var comments = new ArrayList<String>();
        for (var column : columns) {
            if (column.name() == null || column.name().isBlank()) {
                throw new IllegalArgumentException("字段英文编码不能为空");
            }
            var type = PhysicalTypes.of(dialect, column.dataType(), column.lengthPrecision());
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
        var keyColumns = columns.stream()
                .filter(column -> column.keyPosition() != null)
                .sorted(Comparator.comparing(PhysicalColumnSpec::keyPosition))
                .map(column -> dialect.quote(column.name()))
                .collect(Collectors.joining(", "));
        if (!keyColumns.isEmpty()) definitions.add("  PRIMARY KEY (" + keyColumns + ")");
        var statements = new ArrayList<String>();
        statements.add("CREATE TABLE " + qualified + " (\n" + String.join(",\n", definitions) + "\n)");
        statements.addAll(comments);
        return new PhysicalScript(List.copyOf(statements), String.join(";\n", statements) + ";");
    }

    private static String commentOf(PhysicalColumnSpec column) {
        var comment = column.comment() == null ? null : column.comment().trim();
        return comment == null || comment.isEmpty() ? null : comment;
    }

    private static String escape(String value, PhysicalDialect dialect) {
        // MySQL reads a backslash inside a string literal as an escape character, so it is doubled
        // before the quote is; a comment that ends in a lone backslash would otherwise escape the
        // closing quote and break the whole statement. PostgreSQL (standard_conforming_strings) and
        // Oracle treat a backslash as an ordinary character, where doubling would store two.
        var prepared = dialect == PhysicalDialect.MYSQL ? value.replace("\\", "\\\\") : value;
        return prepared.replace("'", "''");
    }
}
