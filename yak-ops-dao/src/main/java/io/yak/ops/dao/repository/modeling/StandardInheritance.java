package io.yak.ops.dao.repository.modeling;

import io.yak.ops.common.enums.modeling.standard.DataType;

/**
 * The five attributes a bound field standard forces onto its consumers.
 *
 * @param enCode inherited field code
 * @param cnName inherited Chinese display name
 * @param dataType inherited logical type
 * @param lengthPrecision inherited length or precision and scale
 * @param isNotNull inherited not-null flag
 */
public record StandardInheritance(
        String enCode, String cnName, DataType dataType, String lengthPrecision, boolean isNotNull) {}
