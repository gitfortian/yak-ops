package io.yak.ops.business.modeling.publication;

/**
 * Resolved physical target of a publication.
 *
 * @param database explicit database or catalog chosen by the user
 * @param schema explicit schema or owner; null where the engine has none
 */
public record PhysicalAddress(String database, String schema) {

    /**
     * Normalizes a submitted target, collapsing blank schema to null.
     *
     * @param database submitted database
     * @param schema submitted schema, possibly blank
     * @return normalized address
     */
    public static PhysicalAddress of(String database, String schema) {
        var trimmedDatabase = database == null ? null : database.trim();
        var trimmedSchema = schema == null ? null : schema.trim();
        return new PhysicalAddress(
                trimmedDatabase, trimmedSchema == null || trimmedSchema.isEmpty() ? null : trimmedSchema);
    }
}
