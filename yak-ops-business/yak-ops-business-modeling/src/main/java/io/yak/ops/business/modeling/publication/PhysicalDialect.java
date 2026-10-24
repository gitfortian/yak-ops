package io.yak.ops.business.modeling.publication;

/**
 * Engines a logical model can be published to.
 *
 * <p>The dialect exists because the three supported engines do not share DDL: type names, identifier
 * quoting and comment syntax all differ, and so does namespace semantics. Anything that reaches the
 * engine goes through this enum rather than through a vendor check spread over the generator.
 */
public enum PhysicalDialect {
    /** MySQL family: no independent schema, backtick quoting, inline column comments. */
    MYSQL(65_535, false, true),
    /** PostgreSQL: schema-qualified tables, double-quote quoting, {@code COMMENT ON} statements. */
    POSTGRESQL(10_485_760, true, false),
    /** Oracle: owner-qualified tables, {@code COMMENT ON} statements, no {@code bigint} type. */
    ORACLE(4_000, true, false);

    private final int maxStringLength;
    private final boolean requiresSchema;
    private final boolean inlineComments;

    PhysicalDialect(int maxStringLength, boolean requiresSchema, boolean inlineComments) {
        this.maxStringLength = maxStringLength;
        this.requiresSchema = requiresSchema;
        this.inlineComments = inlineComments;
    }

    /**
     * Resolves the dialect of a datasource {@code dbType}.
     *
     * @param dbType descriptor type or an alias accepted by the plugin registry
     * @return matching dialect, or null when the engine cannot be published to
     */
    public static PhysicalDialect of(String dbType) {
        if (dbType == null) return null;
        return switch (dbType.trim().toUpperCase()) {
            case "MYSQL" -> MYSQL;
            case "POSTGRE_SQL", "POSTGRES", "POSTGRESQL" -> POSTGRESQL;
            case "ORACLE" -> ORACLE;
            default -> null;
        };
    }

    /** Whether the engine has an independent schema the publication must select explicitly. */
    public boolean requiresSchema() {
        return requiresSchema;
    }

    /** Largest {@code varchar} length this engine accepts. */
    public int maxStringLength() {
        return maxStringLength;
    }

    /** Whether column comments are written inline in {@code CREATE TABLE} instead of separately. */
    public boolean hasInlineComments() {
        return inlineComments;
    }

    /**
     * Quotes one identifier for this engine.
     *
     * <p>Oracle is left unquoted on purpose: an unquoted identifier folds to upper case, which is the
     * case its catalog reports, so quoting would make every later comparison case-sensitive.
     *
     * @param identifier bare identifier
     * @return identifier safe to embed in DDL
     */
    public String quote(String identifier) {
        return switch (this) {
            case MYSQL -> "`" + identifier + "`";
            case POSTGRESQL -> "\"" + identifier + "\"";
            case ORACLE -> identifier.toUpperCase();
        };
    }

    /**
     * Builds the table reference used in DDL.
     *
     * <p>MySQL qualifies through the database and PostgreSQL through the schema, because that is the
     * namespace each engine resolves a table through. Oracle qualifies through the owner, which is
     * its schema.
     *
     * @param address resolved target address
     * @param table table name
     * @return qualified table reference
     */
    public String qualify(PhysicalAddress address, String table) {
        return switch (this) {
            case MYSQL -> quote(address.database()) + "." + quote(table);
            case POSTGRESQL -> quote(address.schema()) + "." + quote(table);
            case ORACLE -> quote(address.schema()) + "." + quote(table);
        };
    }
}
