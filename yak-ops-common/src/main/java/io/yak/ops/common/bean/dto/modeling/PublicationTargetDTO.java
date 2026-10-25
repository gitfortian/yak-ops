package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Explicit physical target confirmed by the user for a preview or a publication.
 *
 * <p>Neither value is ever inferred from the datasource default, the table name or the layer code:
 * MySQL, Oracle and PostgreSQL do not share namespace semantics, and catalog visibility is
 * authorization-dependent, so an inferred address could publish somewhere the user never saw.
 *
 * @param database target database or catalog name
 * @param schema target schema or owner; rejected where the engine has no independent schema
 */
public record PublicationTargetDTO(
        @NotBlank @Size(max = 128) String database,
        @Size(max = 128) String schema) {}
